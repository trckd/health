package com.tracked.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
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
  private const val KEY_LAST_RUN_MS = "last_worker_run_ms"
  private const val KEY_LAST_RESULT = "last_worker_result"
  private const val KEY_LAST_ERROR = "last_worker_error"
  private const val KEY_LAST_TOKEN_ISSUED_MS = "last_token_issued_ms"
  internal const val WORK_NAME = "HealthStepChangeSync"

  internal fun recordWorkerRun(context: Context, result: String, error: String? = null) {
    val edit = preferences(context.applicationContext).edit()
      .putLong(KEY_LAST_RUN_MS, System.currentTimeMillis())
      .putString(KEY_LAST_RESULT, result)
    if (error != null) edit.putString(KEY_LAST_ERROR, error)
    else edit.remove(KEY_LAST_ERROR)
    edit.apply()
  }

  fun readTelemetry(context: Context): Map<String, Any?> {
    val prefs = preferences(context.applicationContext)
    val lastRun = prefs.getLong(KEY_LAST_RUN_MS, 0L).takeIf { it > 0L }
    return mapOf(
      "backgroundEnabled" to (prefs.getString(KEY_STEPS_TOKEN, null) != null),
      "frequency" to prefs.getString(KEY_FREQUENCY, null),
      "lastRunMs" to lastRun,
      "lastResult" to prefs.getString(KEY_LAST_RESULT, null),
      "lastError" to prefs.getString(KEY_LAST_ERROR, null),
      "lastTokenIssuedMs" to prefs.getLong(KEY_LAST_TOKEN_ISSUED_MS, 0L).takeIf { it > 0L }
    )
  }

  private fun stampToken(context: Context, token: String) {
    preferences(context.applicationContext).edit()
      .putString(KEY_STEPS_TOKEN, token)
      .putLong(KEY_LAST_TOKEN_ISSUED_MS, System.currentTimeMillis())
      .apply()
  }

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
    stampToken(appContext, token)
    preferences(appContext).edit()
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
        stampToken(appContext, token)
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
          Log.e(TAG, "Changes token expired $maxTokenRetries times; aborting")
          break
        }
        Log.w(TAG, "Changes token expired (attempt $tokenRetries/$maxTokenRetries), requesting a fresh token")
        nextToken = client.getChangesToken(tokenRequest)
        stampToken(appContext, nextToken)
        continue
      }

      if (response.changes.isNotEmpty()) {
        hasChanges = true
      }

      nextToken = response.nextChangesToken
      keepReading = response.hasMore
    }

    stampToken(appContext, nextToken)
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

    val response = client.aggregate(
      AggregateRequest(
        metrics = setOf(StepsRecord.COUNT_TOTAL),
        timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
      )
    )

    return response[StepsRecord.COUNT_TOTAL]?.toDouble() ?: 0.0
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
