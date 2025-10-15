package com.lich.updater.Notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.lich.updater.MainActivity
import com.lich.updater.R

class Md5Progress(private val context: Context) {
	companion object {
		private const val CHANNEL_ID = "md5_progress_channel"
		private const val NOTIFICATION_ID = 2003
	}

	private val notificationManager: NotificationManager =
		context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

	init {
		createChannelIfNeeded()
	}

	private fun createChannelIfNeeded() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				CHANNEL_ID,
				context.getString(R.string.md5_progress_channel),
				NotificationManager.IMPORTANCE_LOW
			)
			channel.setShowBadge(false)
			channel.enableVibration(false)
			notificationManager.createNotificationChannel(channel)
		}
	}

	private fun build(): Notification {
		val intent = Intent(context, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
		}
		val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
		val contentIntent = PendingIntent.getActivity(context, 0, intent, flags)
		return NotificationCompat.Builder(context, CHANNEL_ID)
			.setSmallIcon(android.R.drawable.stat_sys_download_done)
			.setContentTitle(context.getString(R.string.verifying_md5))
			.setContentText(context.getString(R.string.verifying_md5))
			.setOnlyAlertOnce(true)
			.setOngoing(true)
			.setContentIntent(contentIntent)
			.build()
	}

	fun show(progressPercent: Int) {
		notificationManager.notify(NOTIFICATION_ID, build())
	}

	fun hide() {
		notificationManager.cancel(NOTIFICATION_ID)
	}
}
