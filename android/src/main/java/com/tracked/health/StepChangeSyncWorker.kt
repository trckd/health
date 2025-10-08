package com.tracked.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.Instant

class StepChangeSyncWorker(
  context: Context,
  workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

  override suspend fun doWork(): Result {
    return try {
      val client = HealthConnectClient.getOrCreate(applicationContext)
      val requiredPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class)
      )
      val granted = client.permissionController.getGrantedPermissions()
      if (!granted.containsAll(requiredPermissions)) {
        Log.w(TAG, "Permissions missing; clearing background sync state")
        HealthBackgroundSync.disable(applicationContext)
        return Result.success()
      }

      val hasChanges = HealthBackgroundSync.pullLatestChanges(applicationContext)
      if (hasChanges) {
        val steps = HealthBackgroundSync.readTodayStepCount(applicationContext)
        val timestamp = Instant.now().toString()

        val module = HealthModule.getInstance()
        if (module != null) {
          module.sendStepDataUpdate(steps, timestamp)
          Log.d(TAG, "Dispatched background step update event ($steps)")
        } else {
          Log.d(TAG, "Health module not available; skipping JS dispatch")
        }
      } else {
        Log.d(TAG, "No step record changes detected")
      }

      HealthBackgroundSync.scheduleSync(applicationContext)
      Result.success()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to process Health Connect changes", e)
      Result.retry()
    }
  }

  companion object {
    private const val TAG = "StepChangeSyncWorker"
  }
}
