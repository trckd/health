package com.tracked.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.Instant

class BodyWeightChangeSyncWorker(
  context: Context,
  workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

  override suspend fun doWork(): Result {
    return try {
      val client = HealthConnectClient.getOrCreate(applicationContext)
      val requiredPermissions = setOf(
        HealthPermission.getReadPermission(WeightRecord::class)
      )
      val granted = client.permissionController.getGrantedPermissions()
      if (!granted.containsAll(requiredPermissions)) {
        Log.w(TAG, "Permissions missing; clearing bodyweight background sync state")
        BodyWeightBackgroundSync.disable(applicationContext)
        return Result.success()
      }

      val hasChanges = BodyWeightBackgroundSync.pullLatestChanges(applicationContext)
      if (hasChanges) {
        val latest = BodyWeightBackgroundSync.readLatestBodyWeight(applicationContext)
        val module = HealthModule.getInstance()
        if (latest != null && module != null) {
          val valueAny = latest["value"]
          val value = when (valueAny) {
            is Double -> valueAny
            is Float -> valueAny.toDouble()
            is Number -> valueAny.toDouble()
            else -> return Result.success()
          }

          val timeAny = latest["time"]
          val time = when (timeAny) {
            is Long -> timeAny
            is Int -> timeAny.toLong()
            is Number -> timeAny.toLong()
            else -> Instant.now().toEpochMilli()
          }
          val isoDate = latest["isoDate"] as? String ?: Instant.ofEpochMilli(time).toString()
          val source = latest["source"] as? String ?: "health_connect"

          module.sendBodyWeightUpdate(value, time, isoDate, source)
          Log.d(TAG, "Dispatched bodyweight update event ($value kg)")
        } else {
          Log.d(TAG, "No latest bodyweight available after change notification")
        }
      } else {
        Log.d(TAG, "No bodyweight changes detected")
      }

      BodyWeightBackgroundSync.scheduleSync(applicationContext)
      Result.success()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to process bodyweight changes", e)
      Result.retry()
    }
  }

  companion object {
    private const val TAG = "BodyWeightChangeWorker"
  }
}
