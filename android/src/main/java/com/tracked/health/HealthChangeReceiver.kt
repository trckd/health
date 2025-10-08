package com.tracked.health

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives Health Connect change notifications and schedules a worker to process them off the main thread.
 */
class HealthChangeReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action
    if (action == ACTION_HEALTH_DATA_SYNC) {
      Log.d(TAG, "Health Connect change notification received")
      HealthBackgroundSync.scheduleSync(context.applicationContext, immediate = true)
      BodyWeightBackgroundSync.scheduleSync(context.applicationContext, immediate = true)
    } else {
      Log.v(TAG, "Ignored broadcast action: $action")
    }
  }

  companion object {
    private const val TAG = "HealthChangeReceiver"
    private const val ACTION_HEALTH_DATA_SYNC = "androidx.health.connect.action.HEALTH_DATA_SYNC"
  }
}
