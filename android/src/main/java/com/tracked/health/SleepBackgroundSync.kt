package com.tracked.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

internal object SleepBackgroundSync {
  private const val TAG = "SleepBackgroundSync"
  private const val PREFS_FILE = "com.tracked.health.sleep"
  private const val KEY_SLEEP_TOKEN = "sleep_changes_token"
  private const val KEY_FREQUENCY = "sleep_update_frequency"
  private const val WORK_NAME = "SleepChangeSync"

  suspend fun enable(context: Context, frequency: String): Boolean {
    val appContext = context.applicationContext
    val client = HealthConnectClient.getOrCreate(appContext)

    val requiredPermissions = setOf(
      HealthPermission.getReadPermission(SleepSessionRecord::class)
    )
    val granted = client.permissionController.getGrantedPermissions()
    if (!granted.containsAll(requiredPermissions)) {
      Log.w(TAG, "Cannot enable sleep updates without Health Connect permissions")
      return false
    }

    val tokenRequest = ChangesTokenRequest(
      recordTypes = setOf(SleepSessionRecord::class)
    )
    val token = client.getChangesToken(tokenRequest)
    preferences(appContext).edit()
      .putString(KEY_SLEEP_TOKEN, token)
      .putString(KEY_FREQUENCY, frequency)
      .apply()

    return true
  }

  suspend fun disable(context: Context): Boolean {
    val appContext = context.applicationContext
    preferences(appContext).edit()
      .remove(KEY_SLEEP_TOKEN)
      .remove(KEY_FREQUENCY)
      .apply()
    WorkManager.getInstance(appContext).cancelUniqueWork(WORK_NAME)
    return true
  }

  fun scheduleSync(context: Context, immediate: Boolean = false) {
    val appContext = context.applicationContext
    val prefs = preferences(appContext)
    val requestedFrequency = prefs.getString(KEY_FREQUENCY, null)

    val requestBuilder = OneTimeWorkRequestBuilder<SleepChangeSyncWorker>()
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
      recordTypes = setOf(SleepSessionRecord::class)
    )

    val currentToken = prefs.getString(KEY_SLEEP_TOKEN, null)
      ?: client.getChangesToken(tokenRequest).also { token ->
        prefs.edit().putString(KEY_SLEEP_TOKEN, token).apply()
      }

    var hasChanges = false
    var nextToken = currentToken
    var keepReading = true

    while (keepReading) {
      val response = client.getChanges(nextToken)

      if (response.changesTokenExpired) {
        Log.w(TAG, "Sleep changes token expired; requesting a fresh token")
        nextToken = client.getChangesToken(tokenRequest)
        prefs.edit().putString(KEY_SLEEP_TOKEN, nextToken).apply()
        continue
      }

      if (response.changes.isNotEmpty()) {
        hasChanges = true
      }

      nextToken = response.nextChangesToken
      keepReading = response.hasMore
    }

    prefs.edit().putString(KEY_SLEEP_TOKEN, nextToken).apply()
    return hasChanges
  }

  suspend fun readLatestSleepSession(context: Context): Map<String, Any?>? {
    val appContext = context.applicationContext
    val client = HealthConnectClient.getOrCreate(appContext)

    // Get sleep sessions from the last 7 days
    val now = Instant.now()
    val sevenDaysAgo = now.minusSeconds(7 * 24 * 60 * 60)

    val response = client.readRecords(
      ReadRecordsRequest(
        recordType = SleepSessionRecord::class,
        timeRangeFilter = TimeRangeFilter.between(sevenDaysAgo, now)
      )
    )

    val record = response.records.maxByOrNull { it.endTime } ?: return null

    return mapSleepSession(record)
  }

  private fun sleepStageTypeToString(stage: Int): String {
    return when (stage) {
      SleepSessionRecord.STAGE_TYPE_UNKNOWN -> "Unknown"
      SleepSessionRecord.STAGE_TYPE_AWAKE -> "Awake"
      SleepSessionRecord.STAGE_TYPE_SLEEPING -> "Sleeping"
      SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "OutOfBed"
      SleepSessionRecord.STAGE_TYPE_LIGHT -> "Light"
      SleepSessionRecord.STAGE_TYPE_DEEP -> "Deep"
      SleepSessionRecord.STAGE_TYPE_REM -> "REM"
      SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> "AwakeInBed"
      else -> "Unknown"
    }
  }

  private fun mapSleepSession(record: SleepSessionRecord): Map<String, Any?> {
    val startTimeMs = record.startTime.toEpochMilli()
    val endTimeMs = record.endTime.toEpochMilli()
    val totalDuration = endTimeMs - startTimeMs

    val stages = record.stages.map { stage ->
      val stageStartMs = stage.startTime.toEpochMilli()
      val stageEndMs = stage.endTime.toEpochMilli()
      val stageDuration = stageEndMs - stageStartMs

      mapOf(
        "type" to sleepStageTypeToString(stage.stage),
        "startTime" to stageStartMs,
        "endTime" to stageEndMs,
        "duration" to stageDuration
      )
    }

    return mapOf(
      "startTime" to startTimeMs,
      "endTime" to endTimeMs,
      "totalDuration" to totalDuration,
      "isoStartDate" to record.startTime.toString(),
      "isoEndDate" to record.endTime.toString(),
      "stages" to stages,
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
    else -> 24L * 60L // Default to daily for sleep
  }
}
