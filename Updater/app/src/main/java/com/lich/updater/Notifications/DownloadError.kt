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

class DownloadError(private val context: Context) {
	companion object {
		private const val CHANNEL_ID = "download_error_channel"
		private const val NOTIFICATION_ID = 2002
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
				context.getString(R.string.download_error_channel),
				NotificationManager.IMPORTANCE_DEFAULT
			)
			channel.enableVibration(true)
			notificationManager.createNotificationChannel(channel)
		}
	}

	private fun build(title: String, message: String): Notification {
		val intent = Intent(context, MainActivity::class.java).apply {
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
		}
		val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
		val contentIntent = PendingIntent.getActivity(context, 0, intent, flags)
		return NotificationCompat.Builder(context, CHANNEL_ID)
			.setSmallIcon(android.R.drawable.stat_notify_error)
			.setContentTitle(title)
			.setContentText(message)
			.setStyle(NotificationCompat.BigTextStyle().bigText(message))
			.setAutoCancel(true)
			.setContentIntent(contentIntent)
			.build()
	}

	fun showNetworkFailure() {
		notificationManager.notify(
			NOTIFICATION_ID,
			build(
				context.getString(R.string.download_failed_title),
				context.getString(R.string.download_failed_network)
			)
		)
	}

	fun cancel() {
		notificationManager.cancel(NOTIFICATION_ID)
	}
}
