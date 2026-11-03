
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

class UpdatesCheckReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UpdatesCheckReceiver"
        private const val ACTION_CHECK_UPDATES =
            "com.lich.updater.ACTION_CHECK_UPDATES"
        private const val REQUEST_CODE = 0

        fun schedule(context: Context) {
            val updatePrefs = UpdatePrefs(context)
            val intervalHours = updatePrefs.getCheckInterval()
            val intervalMs = intervalHours * 60 * 60 * 1000L

            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

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

            // Schedule repeating check
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + intervalMs,
                intervalMs,
                pendingIntent
            )

            Log.d(
                TAG,
                "Scheduled background update check every $intervalHours hours"
            )
        }

        fun cancel(context: Context) {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

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

    private fun checkUpdates(
        context: Context,
        pendingResult: PendingResult
    ) {
        val updatePrefs = UpdatePrefs(context)

        // 1. Check notification settings
        if (!updatePrefs.isUpdateNotificationsEnabled()) {
            Log.d(TAG, "Notifications disabled, skipping check")
            pendingResult.finish()
            return
        }

        // 2. Check connectivity
        if (!isNetworkAvailable(context)) {
            Log.d(TAG, "No network, skipping check")
            pendingResult.finish()
            return
        }

        // 3. Perform ROM and app update checks
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Checking for updates...")

                val currentSkyUIVersion =
                    BuildPropReader(context).getSkyUIVersion()

                val manifestResult =
                    GetManifest(context).fetchManifestData(currentSkyUIVersion)

                when (manifestResult) {
                    is ManifestResult.Success -> {
                        val manifestData = manifestResult.data

                        if (manifestData.type == "none") {
                            Log.d(
                                TAG,
                                "Device supported, but no compatible update found."
                            )
                        } else {
                            val comparison = GetManifest(context).compareVersions(
                                currentSkyUIVersion,
                                manifestData.version,
                                manifestData.type
                            )

                            if (comparison == DateComparisonResult.UPDATE_AVAILABLE) {
                                val savedData = updatePrefs.getSavedManifestData()

                                if (savedData == null ||
                                    savedData.version != manifestData.version ||
                                    savedData.type != manifestData.type
                                ) {
                                    updatePrefs.saveManifestData(manifestData)

                                    com.lich.updater.Notifications.UpdateAvailable(context)
                                        .show(manifestData.version)

                                    Log.d(
                                        TAG,
                                        "Update found: Installed=$currentSkyUIVersion Available=${manifestData.version} Type=${manifestData.type}"
                                    )
                                }
                            } else {
                                Log.d(
                                    TAG,
                                    "No update available. Current version: $currentSkyUIVersion"
                                )
                            }
                        }
                    }

                    ManifestResult.DeviceNotSupported -> {
                        Log.w(
                            TAG,
                            "Device is not supported. Skipping ROM update notification."
                        )
                        updatePrefs.clearUpdate()
                    }

                    ManifestResult.NoInternet -> {
                        Log.w(
                            TAG,
                            "Unable to check ROM updates: No internet"
                        )
                    }

                    ManifestResult.Error -> {
                        Log.e(
                            TAG,
                            "Unable to check ROM updates: Manifest error"
                        )
                    }
                }

                // 4. Also check for app updates
                val appUpdateInfo =
                    AppUpdateChecker(context).checkForAppUpdate()

                if (appUpdateInfo != null && appUpdateInfo.isUpdateAvailable) {
                    com.lich.updater.Notifications.AppUpdateNotification(context)
                        .show(appUpdateInfo.tagName)

                    Log.d(
                        TAG,
                        "App Update found and notified: ${appUpdateInfo.tagName}"
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error checking updates", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false

        return caps.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        )
    }
}
