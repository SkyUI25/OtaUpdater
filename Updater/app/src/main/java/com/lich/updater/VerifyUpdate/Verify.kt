package com.lich.updater.VerifyUpdate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.lich.updater.BuildPropReader
import com.lich.updater.UpdatePrefs
import com.lich.updater.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Verify : BroadcastReceiver() {

    companion object {
        private const val TAG = "VerifyUpdate"
        private const val CHANNEL_ID = "verify_update_channel"
        private const val NOTIFICATION_ID = 3007
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val updatePrefs = UpdatePrefs(appContext)
                val installStatus = updatePrefs.getInstallStatus()

                Log.d(TAG, "Boot completed - installStatus=$installStatus")

                // Only verify after an actual installation attempt.
                if (installStatus != UpdatePrefs.INSTALL_STATUS_INSTALLING) {
                    Log.d(TAG, "No pending installation verification, skipping")
                    return@launch
                }

                val manifest = updatePrefs.getSavedManifestData()

                if (manifest == null) {
                    Log.w(TAG, "Saved manifest not found, restoring install state")
                    restoreReadyOrClear(updatePrefs)
                    return@launch
                }

                val deviceVersion = BuildPropReader(appContext).getSkyUIVersion()
                val targetVersion = manifest.version

                Log.d(
                    TAG,
                    "deviceVersion=$deviceVersion targetVersion=$targetVersion"
                )

                val success =
                    deviceVersion != appContext.getString(R.string.unknown) &&
                    deviceVersion == targetVersion

                if (success) {
                    // Installation succeeded.
                    updatePrefs.clearUpdate()

                    showResultNotification(appContext, success = true)
                    Log.i(TAG, "Update verification success")
                } else {
                    // Installation did not reach the target version.
                    // Restore READY if the downloaded ROM still exists.
                    restoreReadyOrClear(updatePrefs)

                    showResultNotification(appContext, success = false)
                    Log.i(TAG, "Update verification failed")
                }

            } catch (t: Throwable) {
                Log.e(TAG, "Verification error: ${t.message}", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun restoreReadyOrClear(updatePrefs: UpdatePrefs) {
        val filePath = updatePrefs.getInstallFilePath()
        val installFile = filePath?.let { java.io.File(it) }

        if (installFile?.exists() == true) {
            updatePrefs.setInstallStatus(
                UpdatePrefs.INSTALL_STATUS_READY,
                filePath
            )
            Log.d(TAG, "Install state restored to READY")
        } else {
            updatePrefs.clearInstallState()
            Log.d(TAG, "Install file missing, install state cleared")
        }
    }

    private fun showResultNotification(
        context: Context,
        success: Boolean
    ) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Update Verification",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val title = if (success) {
            "Update success"
        } else {
            "Update failed"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
