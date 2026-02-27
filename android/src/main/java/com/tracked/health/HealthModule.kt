package com.tracked.health

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.time.Instant
import java.util.ArrayList

class HealthModule : Module() {
  private val context: Context
    get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

  private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }
  private val moduleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val ioDispatcher = Dispatchers.IO

  companion object {
    private const val TAG = "HealthModule"

    @Volatile
    private var moduleInstance: WeakReference<HealthModule>? = null

    fun getInstance(): HealthModule? = moduleInstance?.get()

    internal fun setInstance(instance: HealthModule) {
      moduleInstance = WeakReference(instance)
    }

    internal fun clearInstance() {
      moduleInstance = null
    }
  }

  private var pendingPermissionPromise: Promise? = null

  override fun definition() = ModuleDefinition {
    Name("Health")

    Events("onStepDataUpdate", "onBodyWeightDataUpdate")

    Constants(
      "isHealthDataAvailable" to isHealthConnectAvailable()
    )

    OnCreate {
      setInstance(this@HealthModule)
    }

    OnDestroy {
      // Clean up any pending promises to prevent memory leaks
      pendingPermissionPromise?.reject("module_destroyed", "Health module was destroyed", null)
      pendingPermissionPromise = null
      clearInstance()
    }

    Function("checkHealthDataAvailable") {
      return@Function isHealthConnectAvailable()
    }

    AsyncFunction("requestAuthorization") { promise: Promise ->
      moduleScope.launch {
        try {
          val permissions = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class)
          )

          val granted = withContext(ioDispatcher) {
            healthConnectClient.permissionController.getGrantedPermissions()
          }

          if (granted.containsAll(permissions)) {
            Log.i(TAG, "Health Connect permissions already granted")
            promise.resolve(true)
          } else {
            Log.i(TAG, "Requesting Health Connect permissions")
            launchPermissionActivity(permissions, promise)
          }
        } catch (e: Exception) {
          Log.e(TAG, "Error requesting authorization", e)
          promise.reject("authorization_error", e.message, e)
        }
      }
    }

    AsyncFunction("getStepCount") { startDate: Long, endDate: Long, promise: Promise ->
      moduleScope.launch {
        try {
          val startInstant = Instant.ofEpochMilli(startDate)
          val endInstant = Instant.ofEpochMilli(endDate)

          Log.d(TAG, "Querying steps from $startInstant to $endInstant (input: $startDate to $endDate)")

          val totalSteps = withContext(ioDispatcher) {
            val response = healthConnectClient.aggregate(
              AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
              )
            )
            response[StepsRecord.COUNT_TOTAL]?.toDouble() ?: 0.0
          }

          Log.d(TAG, "Retrieved $totalSteps steps for period ${startInstant} to ${endInstant}")
          promise.resolve(totalSteps)
        } catch (e: Exception) {
          Log.e(TAG, "Error getting step count", e)

          // Distinguish between "no data" (legitimate) vs actual errors
          val errorMessage = e.message ?: "Unknown error"
          if (errorMessage.contains("No data available") || errorMessage.contains("no records found")) {
            // Legitimate case: no step data for this date range
            Log.d(TAG, "No step data available for period - returning 0")
            promise.resolve(0.0)
          } else {
            // Actual error: reject so JavaScript can handle it
            Log.e(TAG, "Health Connect error: $errorMessage")
            promise.reject("HEALTH_CONNECT_ERROR", "Failed to retrieve step data: $errorMessage", e)
          }
        }
      }
    }

    AsyncFunction("hasStepDataForDate") { startDate: Long, endDate: Long, promise: Promise ->
      moduleScope.launch {
        try {
          val startInstant = Instant.ofEpochMilli(startDate)
          val endInstant = Instant.ofEpochMilli(endDate)

          Log.d(TAG, "Checking if step data exists for period $startInstant to $endInstant")

          val hasData = withContext(ioDispatcher) {
            val request = ReadRecordsRequest(
              recordType = StepsRecord::class,
              timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
            )

            val response = healthConnectClient.readRecords(request)
            response.records.isNotEmpty()
          }

          Log.d(TAG, "Step data exists for period: $hasData")
          promise.resolve(hasData)
        } catch (e: Exception) {
          Log.e(TAG, "Error checking for step data", e)
          promise.resolve(false)
        }
      }
    }

    AsyncFunction("getBodyWeightSamples") { startDate: Long, endDate: Long, promise: Promise ->
      moduleScope.launch {
        try {
          val startInstant = Instant.ofEpochMilli(startDate)
          val endInstant = Instant.ofEpochMilli(endDate)

          val samples = withContext(ioDispatcher) {
            val request = ReadRecordsRequest(
              recordType = WeightRecord::class,
              timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
            )

            val response = healthConnectClient.readRecords(request)
            response.records.sortedBy { it.time }.map { mapWeightRecord(it) }
          }

          promise.resolve(samples)
        } catch (e: Exception) {
          Log.e(TAG, "Error reading body weight samples", e)
          promise.reject("body_weight_read_error", e.message, e)
        }
      }
    }

    AsyncFunction("getLatestBodyWeight") { promise: Promise ->
      moduleScope.launch {
        try {
          val latest = withContext(ioDispatcher) {
            val now = Instant.now()
            // Try last 90 days first to avoid scanning entire history
            val recentStart = now.minus(java.time.Duration.ofDays(90))
            val recentRequest = ReadRecordsRequest(
              recordType = WeightRecord::class,
              timeRangeFilter = TimeRangeFilter.between(recentStart, now)
            )
            val recentResponse = healthConnectClient.readRecords(recentRequest)
            val record = recentResponse.records.maxByOrNull { it.time }
              ?: run {
                // Fallback to last 2 years if no recent records
                val fallbackStart = now.minus(java.time.Duration.ofDays(730))
                val fallbackRequest = ReadRecordsRequest(
                  recordType = WeightRecord::class,
                  timeRangeFilter = TimeRangeFilter.between(fallbackStart, now)
                )
                healthConnectClient.readRecords(fallbackRequest).records.maxByOrNull { it.time }
              }
            record?.let { mapWeightRecord(it) }
          }

          promise.resolve(latest)
        } catch (e: Exception) {
          Log.e(TAG, "Error reading latest body weight", e)
          promise.reject("body_weight_read_error", e.message, e)
        }
      }
    }

    AsyncFunction("enableBackgroundDelivery") { frequency: String, promise: Promise ->
      moduleScope.launch {
        try {
          val result = withContext(ioDispatcher) {
            HealthBackgroundSync.enable(context.applicationContext, frequency)
          }

          if (result) {
            HealthBackgroundSync.scheduleSync(context.applicationContext, immediate = true)
          }

          Log.i(TAG, "Background delivery enabled with Health Connect change notifications (frequency hint: $frequency)")
          promise.resolve(result)
        } catch (e: Exception) {
          Log.e(TAG, "Error enabling background delivery", e)
          promise.reject("background_delivery_error", e.message, e)
        }
      }
    }

    AsyncFunction("disableBackgroundDelivery") { promise: Promise ->
      moduleScope.launch {
        try {
          val result = withContext(ioDispatcher) {
            HealthBackgroundSync.disable(context.applicationContext)
          }

          Log.i(TAG, "Background delivery disabled")
          promise.resolve(result)
        } catch (e: Exception) {
          Log.e(TAG, "Error disabling background delivery", e)
          promise.reject("background_delivery_error", e.message, e)
        }
      }
    }

    AsyncFunction("enableBodyWeightUpdates") { frequency: String, promise: Promise ->
      moduleScope.launch {
        try {
          val result = withContext(ioDispatcher) {
            BodyWeightBackgroundSync.enable(context.applicationContext, frequency)
          }

          if (result) {
            BodyWeightBackgroundSync.scheduleSync(context.applicationContext, immediate = true)
          }

          Log.i(TAG, "Bodyweight background updates enabled (frequency hint: $frequency)")
          promise.resolve(result)
        } catch (e: Exception) {
          Log.e(TAG, "Error enabling bodyweight updates", e)
          promise.reject("body_weight_delivery_error", e.message, e)
        }
      }
    }

    AsyncFunction("disableBodyWeightUpdates") { promise: Promise ->
      moduleScope.launch {
        try {
          val result = withContext(ioDispatcher) {
            BodyWeightBackgroundSync.disable(context.applicationContext)
          }

          Log.i(TAG, "Bodyweight background updates disabled")
          promise.resolve(result)
        } catch (e: Exception) {
          Log.e(TAG, "Error disabling bodyweight updates", e)
          promise.reject("body_weight_delivery_error", e.message, e)
        }
      }
    }
  }

  private fun isHealthConnectAvailable(): Boolean {
    return try {
      val providerPackageName = "com.google.android.apps.healthdata"
      when (HealthConnectClient.getSdkStatus(context, providerPackageName)) {
        HealthConnectClient.SDK_AVAILABLE -> true
        HealthConnectClient.SDK_UNAVAILABLE -> false
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> false
        else -> false
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error checking Health Connect availability", e)
      false
    }
  }

  private fun launchPermissionActivity(permissions: Set<String>, promise: Promise) {
    try {
      // Reject if a permission request is already in flight
      pendingPermissionPromise?.let { existing ->
        existing.reject("authorization_error", "A permission request is already in progress", null)
        pendingPermissionPromise = null
      }

      val intent = Intent(context, HealthPermissionActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putStringArrayListExtra("required_permissions", ArrayList(permissions))
      }

      val currentActivity = appContext.currentActivity
      currentActivity?.startActivity(intent) ?: context.startActivity(intent)
      pendingPermissionPromise = promise
    } catch (e: Exception) {
      Log.e(TAG, "Error requesting Health Connect permissions", e)
      promise.resolve(false)
    }
  }

  private fun mapWeightRecord(record: WeightRecord): Map<String, Any?> {
    val timeMs = record.time.toEpochMilli()
    return mapOf(
      "value" to record.weight.inKilograms,
      "time" to timeMs,
      "isoDate" to Instant.ofEpochMilli(timeMs).toString(),
      "source" to record.metadata.dataOrigin.packageName
    )
  }

  internal fun sendStepDataUpdate(steps: Double, date: String) {
    try {
      sendEvent("onStepDataUpdate", mapOf(
        "steps" to steps,
        "date" to date
      ))
      Log.d(TAG, "Sent step data update event: $steps steps")
    } catch (e: Exception) {
      Log.e(TAG, "Error sending step data update event", e)
    }
  }

  internal fun sendBodyWeightUpdate(valueKg: Double, timeMs: Long, isoDate: String, source: String) {
    try {
      sendEvent(
        "onBodyWeightDataUpdate",
        mapOf(
          "value" to valueKg,
          "time" to timeMs,
          "isoDate" to isoDate,
          "source" to source
        )
      )
      Log.d(TAG, "Sent bodyweight update event: $valueKg kg")
    } catch (e: Exception) {
      Log.e(TAG, "Error sending bodyweight update event", e)
    }
  }

  internal fun resolvePermissionResult(granted: Boolean) {
    Log.d(TAG, "Resolving permission result: $granted")
    pendingPermissionPromise?.resolve(granted)
    pendingPermissionPromise = null
  }
}
