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
import androidx.health.connect.client.records.StepsRecord

class HealthPermissionActivity : ComponentActivity() {
    
    private lateinit var requestPermissionActivityContract: ActivityResultLauncher<Set<PermissionController.Permission>>
    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(this) }
    
    companion object {
        private const val TAG = "HealthPermissionActivity"
        const val EXTRA_PERMISSIONS_GRANTED = "permissions_granted"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set up the permission request launcher
        requestPermissionActivityContract = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { granted ->
            Log.d(TAG, "Permission result received: $granted")
            
            // Set result and finish
            val resultIntent = Intent().apply {
                putExtra(EXTRA_PERMISSIONS_GRANTED, granted.containsAll(getRequiredPermissions()))
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
        
        // Start permission request immediately
        requestPermissions()
    }
    
    private fun requestPermissions() {
        val permissions = getRequiredPermissions()
        
        try {
            Log.d(TAG, "Requesting Health Connect permissions: $permissions")
            requestPermissionActivityContract.launch(permissions)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching permission request", e)
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }
    
    private fun getRequiredPermissions(): Set<PermissionController.Permission> {
        return setOf(
            PermissionController.createReadPermission(StepsRecord::class)
        )
    }
} 