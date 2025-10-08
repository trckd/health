package com.tracked.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import java.time.Instant
import java.time.ZoneId

internal object HealthBackgroundSync {
  private const val TAG = "HealthBackgroundSync"
  private const val PREFS_FILE = "com.tracked.health.background"
  private const val KEY_STEPS_TOKEN = "steps_changes_token"
  private const val KEY_FREQUENCY = "update_frequency"
  private const val WORK_NAME = "HealthStepChangeSync"

  suspend fun enable(context: Context, frequency: String): Boolean {
    val appContext = context.applicationContext
    val client = HealthConnectClient.getOrCreate(appContext)

    val requiredPermissions = setOf(
      HealthPermission.getReadPermission(StepsRecord::class)
    )
    val granted = client.permissionController.getGrantedPermissions()
    if (!granted.containsAll(requiredPermissions)) {
      Log.w(TAG, "Cannot enable background delivery without Health Connect permissions")
      return false
    }

    val tokenRequest = ChangesTokenRequest(
      recordTypes = setOf(StepsRecord::class)
    )
    val token = client.getChangesToken(tokenRequest)
    preferences(appContext).edit()
      .putString(KEY_STEPS_TOKEN, token)
      .putString(KEY_FREQUENCY, frequency)
      .apply()

    return true
  }

  suspend fun disable(context: Context): Boolean {
    val appContext = context.applicationContext
    preferences(appContext).edit()
      .remove(KEY_STEPS_TOKEN)
      .remove(KEY_FREQUENCY)
      .apply()
    WorkManager.getInstance(appContext).cancelUniqueWork(WORK_NAME)
    return true
  }

  fun scheduleSync(context: Context, immediate: Boolean = false) {
    val appContext = context.applicationContext
    val prefs = preferences(appContext)
    val requestedFrequency = prefs.getString(KEY_FREQUENCY, null)

    val requestBuilder = OneTimeWorkRequestBuilder<StepChangeSyncWorker>()
      .addTag(WORK_NAME)

    val delayMinutes = if (immediate) 0L else frequencyToDelayMinutes(requestedFrequency)
    if (delayMinutes > 0) {
      requestBuilder.setInitialDelay(delayMinutes, TimeUnit.MINUTES)
    }

    val workRequest = requestBuilder.build()

    WorkManager.getInstance(appContext).enqueueUniqueWork(
      WORK_NAME,
      ExistingWorkPolicy.APPEND_OR_REPLACE,
      workRequest
    )
  }

  suspend fun pullLatestChanges(context: Context): Boolean {
    val appContext = context.applicationContext
    val client = HealthConnectClient.getOrCreate(appContext)
    val prefs = preferences(appContext)

    val tokenRequest = ChangesTokenRequest(
      recordTypes = setOf(StepsRecord::class)
    )

    val currentToken = prefs.getString(KEY_STEPS_TOKEN, null)
      ?: client.getChangesToken(tokenRequest).also { token ->
        prefs.edit().putString(KEY_STEPS_TOKEN, token).apply()
      }

    var hasChanges = false
    var nextToken = currentToken
    var keepReading = true

    while (keepReading) {
      val response = client.getChanges(nextToken)

      if (response.changesTokenExpired) {
        Log.w(TAG, "Changes token expired, requesting a fresh token")
        nextToken = client.getChangesToken(tokenRequest)
        prefs.edit().putString(KEY_STEPS_TOKEN, nextToken).apply()
        continue
      }

      if (response.changes.isNotEmpty()) {
        hasChanges = true
      }

      nextToken = response.nextChangesToken
      keepReading = response.hasMore
    }

    prefs.edit().putString(KEY_STEPS_TOKEN, nextToken).apply()
    return hasChanges
  }

  suspend fun readTodayStepCount(context: Context): Double {
    val appContext = context.applicationContext
    val client = HealthConnectClient.getOrCreate(appContext)

    val zone = ZoneId.systemDefault()
    val now = Instant.now()
    val startOfDay = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
    val endOfDay = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone)
      .minusNanos(1)
      .toInstant()

    val response = client.readRecords(
      ReadRecordsRequest(
        recordType = StepsRecord::class,
        timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
      )
    )

    return response.records.sumOf { it.count }.toDouble()
  }

  private fun preferences(context: Context) =
    context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

  private fun frequencyToDelayMinutes(frequency: String?): Long = when (frequency?.lowercase()) {
    "immediate" -> 15L
    "daily" -> 24L * 60L
    "weekly" -> 7L * 24L * 60L
    "hourly" -> 60L
    else -> 60L
  }
}
