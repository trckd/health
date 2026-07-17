package com.tracked.health

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord

class HealthPermissionActivity : ComponentActivity() {

    private lateinit var requestPermissionActivityContract: ActivityResultLauncher<Set<String>>
    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(this) }

    companion object {
        private const val TAG = "HealthPermissionActivity"
        const val EXTRA_PERMISSIONS_GRANTED = "permissions_granted"
        const val EXTRA_SUCCESS_PERMISSIONS = "success_permissions"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requiredPermissions = intent.getStringArrayListExtra("required_permissions")?.toSet()
            ?: getRequiredPermissions()
        // Optional capabilities such as background reads should be shown in
        // the same Health Connect sheet, but declining an optional capability
        // must not make foreground step access look denied to JavaScript.
        val successPermissions = intent
            .getStringArrayListExtra(EXTRA_SUCCESS_PERMISSIONS)
            ?.toSet()
            ?: requiredPermissions

        requestPermissionActivityContract = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { grantedPermissions ->
            Log.d(TAG, "Permission result received: $grantedPermissions")

            val hasAllPermissions = grantedPermissions.containsAll(successPermissions)
            Log.d(TAG, "All permissions granted: $hasAllPermissions")

            val moduleInstance = HealthModule.getInstance()
            moduleInstance?.resolvePermissionResult(hasAllPermissions)

            val resultIntent = Intent().apply {
                putExtra(EXTRA_PERMISSIONS_GRANTED, hasAllPermissions)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

        requestPermissions(requiredPermissions)
    }

    private fun requestPermissions(permissions: Set<String>) {
        try {
            Log.d(TAG, "Requesting Health Connect permissions: $permissions")
            requestPermissionActivityContract.launch(permissions)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching permission request", e)

            val moduleInstance = HealthModule.getInstance()
            moduleInstance?.resolvePermissionResult(false)

            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private fun getRequiredPermissions(): Set<String> {
        return setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )
    }
}
