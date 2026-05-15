package com.lich.updater.Notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.lich.updater.MainActivity
import com.lich.updater.R

class InstallNotification(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "install_notification_channel"
        const val NOTIFICATION_ID = 2001
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.install_notification_channel)
            val descriptionText = context.getString(R.string.install_notification_description)
            val importance = NotificationManager.IMPORTANCE_HIGH // High importance for install notification
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                // Sound will be enabled by default with high importance
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun show() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.install_now))
            .setContentText(context.getString(R.string.install_notification_message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Notification disappears when tapped
            .setSound(null) // Use default system sound
            .setVibrate(longArrayOf(0, 250, 250, 250)) // Vibration pattern
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun hide() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
