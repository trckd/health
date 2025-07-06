package com.tracked.health

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.*
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.Exceptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class HealthModule : Module() {
  private val context: Context
    get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

  private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }
  private val moduleScope = CoroutineScope(Dispatchers.Main)

  companion object {
    private const val WORK_NAME = "HealthStepDataSync"
    private const val TAG = "HealthModule"
    
    // Static reference to the module instance for WorkManager communication
    @Volatile
    private var moduleInstance: HealthModule? = null
  }

  init {
    moduleInstance = this
  }

  override fun definition() = ModuleDefinition {
    Name("Health")

    Events("onStepDataUpdate")

    Constants(
      "isHealthDataAvailable" to isHealthConnectAvailable()
    )

    Function("checkHealthDataAvailable") {
      return@Function isHealthConnectAvailable()
    }

    AsyncFunction("requestAuthorization") { promise: Promise ->
      moduleScope.launch {
        try {
          val permissions = setOf(
            PermissionController.createReadPermission(StepsRecord::class)
          )
          
          val granted = healthConnectClient.permissionController.getGrantedPermissions()
          
          if (granted.containsAll(permissions)) {
            Log.i(TAG, "Health Connect permissions already granted")
            promise.resolve(true)
          } else {
            Log.i(TAG, "Requesting Health Connect permissions")
            requestHealthConnectPermissions(promise)
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
          val startInstant = Instant.ofEpochSecond(startDate)
          val endInstant = Instant.ofEpochSecond(endDate)
          
          val request = ReadRecordsRequest(
            recordType = StepsRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
          )
          
          val response = healthConnectClient.readRecords(request)
          
          val totalSteps = response.records.sumOf { it.count }
          
          Log.d(TAG, "Retrieved $totalSteps steps for period ${startInstant} to ${endInstant}")
          promise.resolve(totalSteps.toDouble())
        } catch (e: Exception) {
          Log.e(TAG, "Error getting step count", e)
          // Return 0 instead of rejecting to match iOS behavior
          promise.resolve(0.0)
        }
      }
    }

    AsyncFunction("enableBackgroundDelivery") { frequency: String, promise: Promise ->
      moduleScope.launch {
        try {
          val workManager = WorkManager.getInstance(context)
          
          // Convert frequency to work interval
          val (interval, timeUnit) = when (frequency.lowercase()) {
            "immediate" -> Pair(15L, TimeUnit.MINUTES) // Minimum interval for WorkManager
            "hourly" -> Pair(1L, TimeUnit.HOURS)
            "daily" -> Pair(1L, TimeUnit.DAYS)
            "weekly" -> Pair(7L, TimeUnit.DAYS)
            else -> Pair(1L, TimeUnit.HOURS)
          }
          
          val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .build()
          
          val workRequest = PeriodicWorkRequestBuilder<StepDataWorker>(interval, timeUnit)
            .setConstraints(constraints)
            .addTag(WORK_NAME)
            .build()
          
          workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
          )
          
          Log.i(TAG, "Background delivery enabled with frequency: $frequency")
          promise.resolve(true)
        } catch (e: Exception) {
          Log.e(TAG, "Error enabling background delivery", e)
          promise.reject("background_delivery_error", e.message, e)
        }
      }
    }

    AsyncFunction("disableBackgroundDelivery") { promise: Promise ->
      moduleScope.launch {
        try {
          val workManager = WorkManager.getInstance(context)
          workManager.cancelUniqueWork(WORK_NAME)
          
          Log.i(TAG, "Background delivery disabled")
          promise.resolve(true)
        } catch (e: Exception) {
          Log.e(TAG, "Error disabling background delivery", e)
          promise.reject("background_delivery_error", e.message, e)
        }
      }
    }
  }

  private fun isHealthConnectAvailable(): Boolean {
    return try {
      when (HealthConnectClient.getSdkStatus(context)) {
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

  private fun requestHealthConnectPermissions(promise: Promise) {
    try {
      // Launch the permission activity
      val intent = Intent(context, HealthPermissionActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      
      // Start the activity and handle the result
      val currentActivity = appContext.currentActivity
      if (currentActivity != null) {
        // If we have an activity context, use it
        currentActivity.startActivity(intent)
        
        // Since we can't get the result directly in this context,
        // we'll check permissions after a delay
        moduleScope.launch {
          kotlinx.coroutines.delay(2000) // Give user time to interact
          
          val permissions = setOf(
            PermissionController.createReadPermission(StepsRecord::class)
          )
          val granted = healthConnectClient.permissionController.getGrantedPermissions()
          val hasAllPermissions = granted.containsAll(permissions)
          
          Log.i(TAG, "Permission check after request: $hasAllPermissions")
          promise.resolve(hasAllPermissions)
        }
      } else {
        // Fallback: start with application context
        context.startActivity(intent)
        
        moduleScope.launch {
          kotlinx.coroutines.delay(2000)
          
          val permissions = setOf(
            PermissionController.createReadPermission(StepsRecord::class)
          )
          val granted = healthConnectClient.permissionController.getGrantedPermissions()
          val hasAllPermissions = granted.containsAll(permissions)
          
          Log.i(TAG, "Permission check after request (fallback): $hasAllPermissions")
          promise.resolve(hasAllPermissions)
        }
      }
      
    } catch (e: Exception) {
      Log.e(TAG, "Error requesting Health Connect permissions", e)
      promise.resolve(false)
    }
  }

  // Helper function to send step data update events
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

  // Static method to get the current module instance
  companion object {
    fun getInstance(): HealthModule? = moduleInstance
  }
}

// WorkManager worker for background step data sync
class StepDataWorker(
  context: Context,
  workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

  private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(applicationContext) }

  override suspend fun doWork(): Result {
    return try {
      Log.d("StepDataWorker", "Starting background step data sync")
      
      // Check if we have permissions first
      val permissions = setOf(
        PermissionController.createReadPermission(StepsRecord::class)
      )
      val granted = healthConnectClient.permissionController.getGrantedPermissions()
      
      if (!granted.containsAll(permissions)) {
        Log.w("StepDataWorker", "Health Connect permissions not granted, skipping sync")
        return Result.failure()
      }
      
      // Get today's steps
      val now = System.currentTimeMillis()
      val startOfDay = getStartOfDay(now)
      val endOfDay = getEndOfDay(now)
      
      val request = ReadRecordsRequest(
        recordType = StepsRecord::class,
        timeRangeFilter = TimeRangeFilter.between(
          Instant.ofEpochMilli(startOfDay),
          Instant.ofEpochMilli(endOfDay)
        )
      )
      
      val response = healthConnectClient.readRecords(request)
      val totalSteps = response.records.sumOf { it.count }
      
      Log.d("StepDataWorker", "Retrieved $totalSteps steps for today")
      
      // Send the update event through the module instance
      val moduleInstance = HealthModule.getInstance()
      if (moduleInstance != null) {
        val dateString = Instant.ofEpochMilli(now).toString()
        moduleInstance.sendStepDataUpdate(totalSteps.toDouble(), dateString)
        Log.d("StepDataWorker", "Sent step data update event")
      } else {
        Log.w("StepDataWorker", "Module instance not available, cannot send event")
      }
      
      Result.success()
    } catch (e: Exception) {
      Log.e("StepDataWorker", "Error syncing step data", e)
      Result.retry()
    }
  }

  private fun getStartOfDay(timestamp: Long): Long {
    val instant = Instant.ofEpochMilli(timestamp)
    val zonedDateTime = instant.atZone(ZoneId.systemDefault())
    val startOfDay = zonedDateTime.toLocalDate().atStartOfDay(ZoneId.systemDefault())
    return startOfDay.toInstant().toEpochMilli()
  }

  private fun getEndOfDay(timestamp: Long): Long {
    val instant = Instant.ofEpochMilli(timestamp)
    val zonedDateTime = instant.atZone(ZoneId.systemDefault())
    val endOfDay = zonedDateTime.toLocalDate().atTime(23, 59, 59, 999_999_999)
      .atZone(ZoneId.systemDefault())
    return endOfDay.toInstant().toEpochMilli()
  }
} 