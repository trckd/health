package com.tracked.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SleepChangeSyncWorker(
  context: Context,
  workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

  override suspend fun doWork(): Result {
    return try {
      val client = HealthConnectClient.getOrCreate(applicationContext)
      val requiredPermissions = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class)
      )
      val granted = client.permissionController.getGrantedPermissions()
      if (!granted.containsAll(requiredPermissions)) {
        Log.w(TAG, "Permissions missing; clearing sleep background sync state")
        SleepBackgroundSync.disable(applicationContext)
        return Result.success()
      }

      val hasChanges = SleepBackgroundSync.pullLatestChanges(applicationContext)
      if (hasChanges) {
        val latestSession = SleepBackgroundSync.readLatestSleepSession(applicationContext)
        val module = HealthModule.getInstance()
        if (latestSession != null && module != null) {
          module.sendSleepDataUpdate(latestSession)
          Log.d(TAG, "Dispatched sleep update event")
        } else {
          Log.d(TAG, "No latest sleep session available after change notification")
        }
      } else {
        Log.d(TAG, "No sleep changes detected")
      }

      SleepBackgroundSync.scheduleSync(applicationContext)
      Result.success()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to process sleep changes", e)
      Result.retry()
    }
  }

  companion object {
    private const val TAG = "SleepChangeWorker"
  }
}
