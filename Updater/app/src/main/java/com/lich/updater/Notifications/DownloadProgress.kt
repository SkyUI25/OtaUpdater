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

class DownloadProgress(private val context: Context) {
	companion object {
		private const val CHANNEL_ID = "download_progress_channel"
		private const val NOTIFICATION_ID = 2001
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
				context.getString(R.string.download_progress_channel),
				NotificationManager.IMPORTANCE_LOW
			)
			channel.setShowBadge(false)
			channel.enableVibration(false)
			notificationManager.createNotificationChannel(channel)
		}
	}

	private fun build(progressPercent: Int, eta: String): Notification {
		val intent = Intent(context, MainActivity::class.java).apply {
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
		}
		val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
		val contentIntent = PendingIntent.getActivity(context, 0, intent, flags)
		return NotificationCompat.Builder(context, CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_notification)
			.setContentTitle(context.getString(R.string.downloading_rom))
			.setContentText(eta)
			.setOnlyAlertOnce(true)
			.setOngoing(true)
			.setProgress(100, progressPercent.coerceIn(0, 100), false)
			.setContentIntent(contentIntent)
			.build()
	}

	fun show(progressPercent: Int, eta: String) {
		notificationManager.notify(NOTIFICATION_ID, build(progressPercent, eta))
	}

	fun hide() {
		notificationManager.cancel(NOTIFICATION_ID)
	}
}
