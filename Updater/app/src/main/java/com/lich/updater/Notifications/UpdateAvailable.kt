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

class UpdateAvailable(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID = "update_available_channel"
        private const val NOTIFICATION_ID = 1004
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_available_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.update_available_channel_description)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun show(version: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_available_title))
            .setContentText(context.getString(R.string.update_available_message, version))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(context.getString(R.string.update_available_big_message, version))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setShowWhen(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    fun hide() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
