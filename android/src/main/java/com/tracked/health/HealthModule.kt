package com.tracked.health

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.WorkInfo
import androidx.work.WorkManager
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
    private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

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
          // Re-check provider availability fresh so a stale "unavailable at boot"
          // result does not silently mask a now-installed Health Connect.
          if (!isHealthConnectAvailable()) {
            promise.reject(
              "HC_PROVIDER_UNAVAILABLE",
              "Health Connect provider is not available on this device",
              null
            )
            return@launch
          }

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
            Log.d(TAG, "No step data available for period - returning 0")
            promise.resolve(0.0)
            return@launch
          }

          val code = when {
            e is SecurityException -> "HC_PERMISSION_DENIED"
            e is android.os.RemoteException -> "HC_REMOTE_EXCEPTION"
            errorMessage.contains("permission", ignoreCase = true) -> "HC_PERMISSION_DENIED"
            else -> "HC_UNKNOWN_ERROR"
          }
          val errClass = e.javaClass.simpleName
          Log.e(TAG, "Health Connect error [$code/$errClass]: $errorMessage")
          promise.reject(code, "$errClass: $errorMessage", e)
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

    AsyncFunction("getHealthDiagnostics") { promise: Promise ->
      moduleScope.launch {
        try {
          val snapshot = withContext(ioDispatcher) { collectDiagnostics() }
          promise.resolve(snapshot)
        } catch (e: Exception) {
          Log.e(TAG, "Error collecting health diagnostics", e)
          // Never reject — diagnostics must be best-effort.
          promise.resolve(mapOf(
            "sdkStatus" to "EXCEPTION",
            "error" to (e.message ?: e.javaClass.simpleName)
          ))
        }
      }
    }

    AsyncFunction("openHealthConnectSettings") { promise: Promise ->
      try {
        val opened = tryStartActivity(
          Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        if (opened) {
          promise.resolve(true)
          return@AsyncFunction
        }
        // Fall back to Play Store listing for the provider package
        val store = tryStartActivity(
          Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        promise.resolve(store)
      } catch (e: Exception) {
        Log.e(TAG, "openHealthConnectSettings failed", e)
        promise.resolve(false)
      }
    }

    AsyncFunction("openBatteryOptimizationSettings") { promise: Promise ->
      val pkg = context.packageName
      val candidates = listOf(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
          .setData(Uri.parse("package:$pkg"))
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          to "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          to "IGNORE_BATTERY_OPTIMIZATION_SETTINGS",
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
          .setData(Uri.fromParts("package", pkg, null))
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          to "APPLICATION_DETAILS_SETTINGS"
      )
      for ((intent, label) in candidates) {
        if (tryStartActivity(intent)) {
          promise.resolve(mapOf("ok" to true, "intentUsed" to label))
          return@AsyncFunction
        }
      }
      promise.resolve(mapOf("ok" to false, "intentUsed" to null))
    }

    AsyncFunction("openOemAppLaunchSettings") { promise: Promise ->
      val manufacturer = Build.MANUFACTURER.lowercase()
      val brand = Build.BRAND.lowercase()
      // Order matters: try OEM-specific settings first, fall back to generic battery.
      val candidates = mutableListOf<Pair<Intent, String>>()
      val oem = when {
        manufacturer == "oppo" || manufacturer == "realme" || brand == "oppo" || brand == "realme" -> "coloros"
        manufacturer == "xiaomi" || manufacturer == "redmi" || manufacturer == "poco" -> "miui"
        manufacturer == "huawei" || manufacturer == "honor" -> "emui"
        manufacturer == "oneplus" || brand == "oneplus" -> "oneplus"
        manufacturer == "vivo" -> "funtouch"
        else -> "stock"
      }
      when (oem) {
        "coloros" -> {
          // ColorOS startup-manager activities (names vary by version)
          listOf(
            "com.coloros.safecenter/.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter/.startupapp.StartupAppListActivity",
            "com.coloros.oppoguardelf/com.coloros.powermanager.fuelgaue.PowerUsageModelActivity",
            "com.oppo.safe/.permission.startup.StartupAppListActivity"
          ).forEach { spec ->
            val parts = spec.split("/")
            candidates += Intent().apply {
              component = ComponentName(parts[0], parts[1])
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } to spec
          }
        }
        "miui" -> {
          listOf(
            "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.miui.securitycenter/com.miui.powercenter.PowerSettings"
          ).forEach { spec ->
            val parts = spec.split("/")
            candidates += Intent().apply {
              component = ComponentName(parts[0], parts[1])
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } to spec
          }
        }
        "emui" -> {
          listOf(
            "com.huawei.systemmanager/.optimize.process.ProtectActivity",
            "com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity"
          ).forEach { spec ->
            val parts = spec.split("/")
            candidates += Intent().apply {
              component = ComponentName(parts[0], parts[1])
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } to spec
          }
        }
        "oneplus" -> {
          candidates += Intent().apply {
            component = ComponentName(
              "com.oneplus.security",
              "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          } to "com.oneplus.security/.chainlaunch.view.ChainLaunchAppListActivity"
        }
        "funtouch" -> {
          candidates += Intent().apply {
            component = ComponentName(
              "com.iqoo.secure",
              "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          } to "com.iqoo.secure/.ui.phoneoptimize.AddWhiteListActivity"
        }
      }
      // Generic fallback: app details, where most ROMs surface "auto-launch" / "background" toggles
      candidates += Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) to "APPLICATION_DETAILS_SETTINGS"

      for ((intent, label) in candidates) {
        if (tryStartActivity(intent)) {
          promise.resolve(mapOf("ok" to true, "oem" to oem, "intentUsed" to label))
          return@AsyncFunction
        }
      }
      promise.resolve(mapOf("ok" to false, "oem" to oem, "intentUsed" to null))
    }

    AsyncFunction("triggerSyncNow") { promise: Promise ->
      moduleScope.launch {
        try {
          withContext(ioDispatcher) {
            HealthBackgroundSync.scheduleSync(context.applicationContext, immediate = true)
          }
          promise.resolve(true)
        } catch (e: Exception) {
          Log.e(TAG, "triggerSyncNow failed", e)
          promise.resolve(false)
        }
      }
    }
  }

  private fun isHealthConnectAvailable(): Boolean {
    return when (sdkStatusString()) {
      "AVAILABLE" -> true
      else -> false
    }
  }

  private fun sdkStatusString(): String {
    return try {
      when (HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PACKAGE)) {
        HealthConnectClient.SDK_AVAILABLE -> "AVAILABLE"
        HealthConnectClient.SDK_UNAVAILABLE -> "UNAVAILABLE"
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "PROVIDER_UPDATE_REQUIRED"
        else -> "UNKNOWN"
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error checking Health Connect availability", e)
      "EXCEPTION"
    }
  }

  private fun tryStartActivity(intent: Intent): Boolean {
    return try {
      val resolved = intent.resolveActivity(context.packageManager) != null
      if (!resolved) return false
      val activity = appContext.currentActivity
      activity?.startActivity(intent) ?: context.startActivity(intent)
      true
    } catch (e: Exception) {
      Log.w(TAG, "Failed to start activity for ${intent.action ?: intent.component}", e)
      false
    }
  }

  private suspend fun collectDiagnostics(): Map<String, Any?> {
    val app = context.applicationContext
    val sdkStatus = sdkStatusString()

    val (providerVersionCode, providerVersionName) = readProviderVersion()

    // Only emit a boolean when we actually queried the permission controller.
    // Otherwise the diagnostic screen / Sentry can't distinguish "denied" from
    // "couldn't ask" (provider not installed, needs update, or query threw).
    val requiredPermissions = setOf(
      HealthPermission.getReadPermission(StepsRecord::class)
    )
    val (grantedPermissions, permissionsGranted) = if (sdkStatus == "AVAILABLE") {
      try {
        val granted = healthConnectClient.permissionController.getGrantedPermissions().toList()
        granted to (granted.toSet().containsAll(requiredPermissions) as Boolean?)
      } catch (e: Exception) {
        Log.w(TAG, "getGrantedPermissions failed", e)
        emptyList<String>() to (null as Boolean?)
      }
    } else {
      emptyList<String>() to (null as Boolean?)
    }

    val workManagerState = try {
      WorkManager.getInstance(app)
        .getWorkInfosForUniqueWork(HealthBackgroundSync.WORK_NAME)
        .get()
        .lastOrNull()
        ?.state
        ?.name
    } catch (e: Exception) {
      Log.w(TAG, "WorkManager state lookup failed", e)
      null
    }

    val ignoringBatteryOptimizations = try {
      val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
      pm?.isIgnoringBatteryOptimizations(app.packageName) ?: false
    } catch (e: Exception) {
      false
    }

    val telemetry = HealthBackgroundSync.readTelemetry(app)

    return mapOf(
      "sdkStatus" to sdkStatus,
      "providerPackage" to HEALTH_CONNECT_PACKAGE,
      "providerVersionCode" to providerVersionCode,
      "providerVersionName" to providerVersionName,
      "permissionsGranted" to permissionsGranted,
      "grantedPermissions" to grantedPermissions,
      "backgroundDeliveryEnabled" to telemetry["backgroundEnabled"],
      "lastWorkerRunMs" to telemetry["lastRunMs"],
      "lastWorkerResult" to telemetry["lastResult"],
      "lastWorkerError" to telemetry["lastError"],
      "lastChangesTokenIssuedMs" to telemetry["lastTokenIssuedMs"],
      "workManagerState" to workManagerState,
      "oemBrand" to Build.BRAND,
      "oemManufacturer" to Build.MANUFACTURER,
      "oemModel" to Build.MODEL,
      "oemDevice" to Build.DEVICE,
      "osSdkInt" to Build.VERSION.SDK_INT,
      "osRelease" to Build.VERSION.RELEASE,
      "ignoringBatteryOptimizations" to ignoringBatteryOptimizations
    )
  }

  private fun readProviderVersion(): Pair<Long?, String?> {
    return try {
      val pm = context.packageManager
      val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(HEALTH_CONNECT_PACKAGE, PackageManager.PackageInfoFlags.of(0))
      } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(HEALTH_CONNECT_PACKAGE, 0)
      }
      val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
      } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
      }
      code to info.versionName
    } catch (e: PackageManager.NameNotFoundException) {
      null to null
    } catch (e: Exception) {
      Log.w(TAG, "readProviderVersion failed", e)
      null to null
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
