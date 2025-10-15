package com.lich.updater

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackgroundUpdateChecker : Service() {
    
    companion object {
        private const val TAG = "BackgroundUpdateChecker"
        private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L // 12 hours in milliseconds
    }
    
    private var serviceJob: Job? = null
    private lateinit var updatePrefs: UpdatePrefs
    private lateinit var updateAvailableNotification: com.lich.updater.Notifications.UpdateAvailable
    
    override fun onCreate() {
        super.onCreate()
        updatePrefs = UpdatePrefs(this)
        updateAvailableNotification = com.lich.updater.Notifications.UpdateAvailable(this)
        Log.d(TAG, "Background update checker service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Background update checker service started")
        
        // Only start checking if notifications are enabled
        if (!updatePrefs.isUpdateNotificationsEnabled()) {
            Log.d(TAG, "Update notifications disabled, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }
        
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    checkForUpdates()
                    delay(CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in background update check", e)
                    delay(CHECK_INTERVAL_MS) // Continue checking even if there's an error
                }
            }
        }
        
        return START_STICKY // Restart service if killed by system
    }
    
    private suspend fun checkForUpdates() {
        Log.d(TAG, "Checking for updates in background")
        
        try {
            val manifestData = withContext(Dispatchers.IO) {
                GetManifest(this@BackgroundUpdateChecker).fetchManifestData()
            }
            
            if (manifestData != null) {
                val currentBuildDate = BuildPropReader(this@BackgroundUpdateChecker).getBuildDate()
                val getManifest = GetManifest(this@BackgroundUpdateChecker)
                val comparisonResult = getManifest.compareDates(currentBuildDate, manifestData.buildDate)
                
                Log.d(TAG, "Current build date: $currentBuildDate, Manifest build date: ${manifestData.buildDate}")
                
                if (comparisonResult == DateComparisonResult.UPDATE_AVAILABLE) {
                    Log.d(TAG, "Update available: ${manifestData.version}")
                    
                    // Check if we already notified about this version
                    val savedData = updatePrefs.getSavedManifestData()
                    if (savedData == null || savedData.version != manifestData.version) {
                        // New version available, show notification
                        updateAvailableNotification.show(manifestData.version, manifestData.buildDate)
                        
                        // Save the manifest data
                        updatePrefs.saveManifestData(manifestData)
                    }
                } else {
                    Log.d(TAG, "No update available (comparison result: $comparisonResult)")
                    // Hide notification if ROM is up to date
                    updateAvailableNotification.hide()
                }
            } else {
                Log.w(TAG, "Failed to fetch manifest data")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceJob?.cancel()
        Log.d(TAG, "Background update checker service destroyed")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
