package com.lich.updater.VerifyUpdate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lich.updater.BuildPropReader
import com.lich.updater.UpdatePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Verify : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val TAG = "VerifyUpdate"
        Log.d(TAG, "Boot completed received - starting verification")

        // Work off main thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val updatePrefs = UpdatePrefs(context)
                val manifest = updatePrefs.getSavedManifestData()
                if (manifest == null) {
                    Log.d(TAG, "No saved manifest, skipping verification")
                    return@launch
                }

                val buildPropReader = BuildPropReader(context)
                val deviceBuildDate = buildPropReader.getBuildDate()
                val manifestBuildDate = manifest.buildDate

                Log.d(TAG, "deviceBuildDate=$deviceBuildDate manifestBuildDate=$manifestBuildDate")

                if (deviceBuildDate == manifestBuildDate) {
                    // Success: clear saved update state so app reloads manifest next launch
                    updatePrefs.clearUpdate()
                    // Show success notification
                    showResultNotification(context, success = true)
                    Log.i(TAG, "Update verification success")
                } else {
                    // Failure
                    showResultNotification(context, success = false)
                    Log.i(TAG, "Update verification failed")
                }
            } catch (t: Throwable) {
                Log.e("VerifyUpdate", "Verification error: ${t.message}", t)
            }
        }
    }

    private fun showResultNotification(context: Context, success: Boolean) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "verify_update_channel"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Update Verification",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
            mgr.createNotificationChannel(channel)
        }

        val title = if (success) "Update success" else "Update failed"
        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setAutoCancel(true)
            .build()

        mgr.notify(3007, notification)
    }
}


