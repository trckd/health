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
import androidx.health.connect.client.records.StepsRecord

class HealthPermissionActivity : ComponentActivity() {
    
    private lateinit var requestPermissionActivityContract: ActivityResultLauncher<Set<String>>
    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(this) }
    
    companion object {
        private const val TAG = "HealthPermissionActivity"
        const val EXTRA_PERMISSIONS_GRANTED = "permissions_granted"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get required permissions from intent or use default
        val requiredPermissions = intent.getStringArrayListExtra("required_permissions")?.toSet()
            ?: getRequiredPermissions()

        // Set up the permission request launcher
        requestPermissionActivityContract = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { grantedPermissions ->
            Log.d(TAG, "Permission result received: $grantedPermissions")

            val hasAllPermissions = grantedPermissions.containsAll(requiredPermissions)
            Log.d(TAG, "All permissions granted: $hasAllPermissions")

            // Notify the module instance about the result
            val moduleInstance = HealthModule.getInstance()
            moduleInstance?.resolvePermissionResult(hasAllPermissions)

            // Set result and finish
            val resultIntent = Intent().apply {
                putExtra(EXTRA_PERMISSIONS_GRANTED, hasAllPermissions)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

        // Start permission request immediately
        requestPermissions(requiredPermissions)
    }
    
    private fun requestPermissions(permissions: Set<String>) {
        try {
            Log.d(TAG, "Requesting Health Connect permissions: $permissions")
            requestPermissionActivityContract.launch(permissions)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching permission request", e)

            // Notify the module instance about the failure
            val moduleInstance = HealthModule.getInstance()
            moduleInstance?.resolvePermissionResult(false)

            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }
    
    private fun getRequiredPermissions(): Set<String> {
        return setOf(
            HealthPermission.getReadPermission(StepsRecord::class)
        )
    }
} 