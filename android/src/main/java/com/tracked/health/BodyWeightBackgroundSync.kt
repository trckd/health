package com.tracked.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

internal object BodyWeightBackgroundSync {
  private const val TAG = "BodyWeightBackgroundSync"
  private const val PREFS_FILE = "com.tracked.health.bodyweight"
  private const val KEY_WEIGHT_TOKEN = "bodyweight_changes_token"
  private const val KEY_FREQUENCY = "bodyweight_update_frequency"
  private const val WORK_NAME = "BodyWeightChangeSync"

  suspend fun enable(context: Context, frequency: String): Boolean {
    val appContext = context.applicationContext
    val client = HealthConnectClient.getOrCreate(appContext)

    val requiredPermissions = setOf(
      HealthPermission.getReadPermission(WeightRecord::class)
    )
    val granted = client.permissionController.getGrantedPermissions()
    if (!granted.containsAll(requiredPermissions)) {
      Log.w(TAG, "Cannot enable bodyweight updates without Health Connect permissions")
      return false
    }

    val tokenRequest = ChangesTokenRequest(
      recordTypes = setOf(WeightRecord::class)
    )
    val token = client.getChangesToken(tokenRequest)
    preferences(appContext).edit()
      .putString(KEY_WEIGHT_TOKEN, token)
      .putString(KEY_FREQUENCY, frequency)
      .apply()

    return true
  }

  suspend fun disable(context: Context): Boolean {
    val appContext = context.applicationContext
    preferences(appContext).edit()
      .remove(KEY_WEIGHT_TOKEN)
      .remove(KEY_FREQUENCY)
      .apply()
    WorkManager.getInstance(appContext).cancelUniqueWork(WORK_NAME)
    return true
  }

  fun scheduleSync(context: Context, immediate: Boolean = false) {
    val appContext = context.applicationContext
    val prefs = preferences(appContext)
    val requestedFrequency = prefs.getString(KEY_FREQUENCY, null)

    val requestBuilder = OneTimeWorkRequestBuilder<BodyWeightChangeSyncWorker>()
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
      recordTypes = setOf(WeightRecord::class)
    )

    val currentToken = prefs.getString(KEY_WEIGHT_TOKEN, null)
      ?: client.getChangesToken(tokenRequest).also { token ->
        prefs.edit().putString(KEY_WEIGHT_TOKEN, token).apply()
      }

    var hasChanges = false
    var nextToken = currentToken
    var keepReading = true
    var tokenRetries = 0
    val maxTokenRetries = 3

    while (keepReading) {
      val response = client.getChanges(nextToken)

      if (response.changesTokenExpired) {
        tokenRetries++
        if (tokenRetries > maxTokenRetries) {
          Log.e(TAG, "Bodyweight changes token expired $maxTokenRetries times; aborting")
          break
        }
        Log.w(TAG, "Bodyweight changes token expired (attempt $tokenRetries/$maxTokenRetries); requesting a fresh token")
        nextToken = client.getChangesToken(tokenRequest)
        prefs.edit().putString(KEY_WEIGHT_TOKEN, nextToken).apply()
        continue
      }

      if (response.changes.isNotEmpty()) {
        hasChanges = true
      }

      nextToken = response.nextChangesToken
      keepReading = response.hasMore
    }

    prefs.edit().putString(KEY_WEIGHT_TOKEN, nextToken).apply()
    return hasChanges
  }

  suspend fun readLatestBodyWeight(context: Context): Map<String, Any?>? {
    val appContext = context.applicationContext
    val client = HealthConnectClient.getOrCreate(appContext)

    val now = Instant.now()
    // Try last 90 days first to avoid scanning entire history
    val recentStart = now.minus(java.time.Duration.ofDays(90))
    val recentResponse = client.readRecords(
      ReadRecordsRequest(
        recordType = WeightRecord::class,
        timeRangeFilter = TimeRangeFilter.between(recentStart, now)
      )
    )
    val record = recentResponse.records.maxByOrNull { it.time }
      ?: run {
        // Fallback to last 2 years if no recent records
        val fallbackStart = now.minus(java.time.Duration.ofDays(730))
        client.readRecords(
          ReadRecordsRequest(
            recordType = WeightRecord::class,
            timeRangeFilter = TimeRangeFilter.between(fallbackStart, now)
          )
        ).records.maxByOrNull { it.time }
      }
      ?: return null
    val timeMs = record.time.toEpochMilli()

    return mapOf(
      "value" to record.weight.inKilograms,
      "time" to timeMs,
      "isoDate" to record.time.toString(),
      "source" to record.metadata.dataOrigin.packageName
    )
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
