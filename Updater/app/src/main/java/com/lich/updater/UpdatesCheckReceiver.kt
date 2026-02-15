package com.lich.updater

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class UpdatesCheckReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UpdatesCheckReceiver"
        private const val ACTION_CHECK_UPDATES = "com.lich.updater.ACTION_CHECK_UPDATES"
        private const val REQUEST_CODE = 0
        // Check every 12 hours
        private const val INTERVAL_MS = 12 * 60 * 60 * 1000L

        fun schedule(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, UpdatesCheckReceiver::class.java).apply {
                action = ACTION_CHECK_UPDATES
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 
                REQUEST_CODE, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Cancel any existing alarm to avoid duplicates
            alarmManager.cancel(pendingIntent)

            // Set repeating alarm
            // Use setInexactRepeating for battery efficiency
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + INTERVAL_MS,
                INTERVAL_MS,
                pendingIntent
            )
            
            Log.d(TAG, "Scheduled background update check")
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, UpdatesCheckReceiver::class.java).apply {
                action = ACTION_CHECK_UPDATES
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 
                REQUEST_CODE, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Canceled background update check")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "onReceive: $action")

        if (Intent.ACTION_BOOT_COMPLETED == action) {
            schedule(context)
            return
        }

        if (ACTION_CHECK_UPDATES == action) {
            checkUpdates(context, goAsync())
        }
    }

    private fun checkUpdates(context: Context, pendingResult: PendingResult) {
        val updatePrefs = UpdatePrefs(context)
        
        // 1. Check if notifications are enabled
        if (!updatePrefs.isUpdateNotificationsEnabled()) {
            Log.d(TAG, "Notifications disabled, skipping check")
            pendingResult.finish()
            return
        }

        // 2. Check Connectivity
        if (!isNetworkAvailable(context)) {
            Log.d(TAG, "No network, skipping check")
            pendingResult.finish()
            return
        }

        // 3. Perform Check
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Checking for updates...")
                val manifestData = GetManifest(context).fetchManifestData()
                
                if (manifestData != null) {
                    val currentBuildDate = BuildPropReader(context).getBuildDate()
                    val comparison = GetManifest(context).compareDates(currentBuildDate, manifestData.buildDate)
                    
                    if (comparison == DateComparisonResult.UPDATE_AVAILABLE) {
                         val savedData = updatePrefs.getSavedManifestData()
                         if (savedData == null || savedData.version != manifestData.version) {
                             // Notify
                             com.lich.updater.Notifications.UpdateAvailable(context).show(manifestData.version, manifestData.buildDate)
                             updatePrefs.saveManifestData(manifestData)
                             Log.d(TAG, "Update found and notified: ${manifestData.version}")
                         }
                    } else {
                        Log.d(TAG, "No update available")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking updates", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
