package com.lich.updater

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.app.PendingIntent
import com.lich.updater.Notifications.DownloadError
import com.lich.updater.Notifications.Md5Progress
import com.lich.updater.Md5Verifier.Md5Comprobations
import java.io.File
import com.lich.updater.Notifications.Md5Error

class DownloadForegroundService : Service() {
	companion object {
		const val EXTRA_URL = "extra_url"
		const val EXTRA_FILE_NAME = "extra_file_name"
		const val EXTRA_COMMAND = "extra_command"
		const val COMMAND_CANCEL = "cancel"

		const val ACTION_PROGRESS = "com.lich.updater.DOWNLOAD_PROGRESS"
		const val ACTION_COMPLETE = "com.lich.updater.DOWNLOAD_COMPLETE"
		const val ACTION_FAILED = "com.lich.updater.DOWNLOAD_FAILED"
		const val ACTION_CANCELED = "com.lich.updater.DOWNLOAD_CANCELED"
		const val ACTION_MD5_PROGRESS = "com.lich.updater.MD5_PROGRESS"
		const val ACTION_MD5_SUCCESS = "com.lich.updater.MD5_SUCCESS"
		const val ACTION_MD5_FAILED = "com.lich.updater.MD5_FAILED"
		const val EXTRA_PERCENT = "percent"
		const val EXTRA_ETA = "eta"
		const val EXTRA_PATH = "path"

		private const val CHANNEL_ID = "download_channel"
		private const val NOTIFICATION_ID = 1001
	}

	enum class NotificationState {
		DOWNLOADING,
		VERIFYING,
		RESUMING
	}

	private lateinit var downloader: RomDownloader
	private lateinit var updatePrefs: UpdatePrefs
	private lateinit var downloadError: DownloadError
	private lateinit var md5Error: Md5Error
	@Volatile private var wasCanceled: Boolean = false
	private var connectivityManager: ConnectivityManager? = null
	private var networkCallback: ConnectivityManager.NetworkCallback? = null

	private val md5Verifier = Md5Comprobations()
	private var md5ProgressNotif: Md5Progress? = null

	override fun onCreate() {
		super.onCreate()
		downloader = RomDownloader(this)
		updatePrefs = UpdatePrefs(this)
		downloadError = DownloadError(this)
		md5Error = Md5Error(this)
		createChannel()
		connectivityManager = getSystemService(ConnectivityManager::class.java)
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		val command = intent?.getStringExtra(EXTRA_COMMAND)
		if (command == COMMAND_CANCEL) {
			wasCanceled = true
			try { downloader.cancel() } catch (_: Exception) {}
			// Only delete file if we are strictly in downloading state (not MD5)
			if (updatePrefs.isDownloadActive()) {
				try { downloader.getLastOutputFile()?.let { if (it.exists()) it.delete() } } catch (_: Exception) {}
			}
			updatePrefs.clearDownload()
			updatePrefs.clearMd5State()
			downloadError.cancel()
			androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_CANCELED))
			stopForeground(true)
			unregisterNetworkCallbackSafe()
			stopSelf()
			return START_NOT_STICKY
		}

		// Check for MD5 resumption
		if (updatePrefs.isMd5Active()) {
			val path = updatePrefs.getMd5Path()
			if (path != null) {
				val file = File(path)
				if (file.exists()) {
					wasCanceled = false
					updateNotification(NotificationState.RESUMING, updatePrefs.getMd5Percent(), getString(R.string.verifying_md5))
					// Resume MD5 verification
					Thread {
						startMd5Verification(file)
					}.start()
					return START_STICKY
				} else {
					// File missing, clear state
					updatePrefs.clearMd5State()
				}
			}
		}

		val url = intent?.getStringExtra(EXTRA_URL)
		val requestedFileName = intent?.getStringExtra(EXTRA_FILE_NAME)
		if (url.isNullOrEmpty()) {
			stopSelf()
			return START_NOT_STICKY
		}
		wasCanceled = false
		updateNotification(NotificationState.RESUMING, 0, getString(R.string.resuming_download))
		registerNetworkCallback()

		Thread {
			try {
				downloader.download(
					url = url,
					requestedFileName = requestedFileName,
					onProgress = { progress ->
						if (wasCanceled) return@download
						// Persist progress directly from service so it's not lost when UI is backgrounded
						updatePrefs.saveDownloadState(true, progress.percent, progress.etaText)
						updateNotification(NotificationState.DOWNLOADING, progress.percent, progress.etaText)
						androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_PROGRESS).apply {
							putExtra(EXTRA_PERCENT, progress.percent)
							putExtra(EXTRA_ETA, progress.etaText)
						})
					},
					onCompleted = { file ->
						if (wasCanceled) return@download
						updatePrefs.clearDownload()
						downloadError.cancel()
						// Start MD5 verification instead of finishing now
						startMd5Verification(file)
					},
					onFailed = {
						if (wasCanceled) return@download
						// Do NOT delete the file on failure, so we can resume later
						// try { downloader.getLastOutputFile()?.let { if (it.exists()) it.delete() } } catch (_: Exception) {}
						updatePrefs.clearDownload()
						downloadError.showNetworkFailure()
						androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_FAILED))
						stopForeground(true)
						unregisterNetworkCallbackSafe()
						stopSelf()
					}
				)
			} catch (_: Exception) {
				if (!wasCanceled) {
					// Do NOT delete the file on exception, so we can resume later
					// try { downloader.getLastOutputFile()?.let { if (it.exists()) it.delete() } } catch (_: Exception) {}
					updatePrefs.clearDownload()
					updatePrefs.clearMd5State()
					downloadError.showNetworkFailure()
					androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_FAILED))
				}
				stopForeground(true)
				unregisterNetworkCallbackSafe()
				stopSelf()
			}
		}.start()

		return START_STICKY
	}

	private fun registerNetworkCallback() {
		val cm = connectivityManager ?: return
		val cb = object : ConnectivityManager.NetworkCallback() {
			override fun onLost(network: Network) {
				if (!wasCanceled) {
					try { downloader.cancel() } catch (_: Exception) {}
					// Do NOT delete file on network lost
					// try { downloader.getLastOutputFile()?.let { if (it.exists()) it.delete() } } catch (_: Exception) {}
					updatePrefs.clearDownload()
					updatePrefs.clearMd5State()
					downloadError.showNetworkFailure()
					androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this@DownloadForegroundService).sendBroadcast(Intent(ACTION_FAILED))
					stopForeground(true)
					unregisterNetworkCallbackSafe()
					stopSelf()
				}
			}
		}
		try {
			cm.registerDefaultNetworkCallback(cb)
			networkCallback = cb
		} catch (_: Exception) {}
	}

	private fun unregisterNetworkCallbackSafe() {
		val cm = connectivityManager
		val cb = networkCallback
		if (cm != null && cb != null) {
			try { cm.unregisterNetworkCallback(cb) } catch (_: Exception) {}
		}
		networkCallback = null
	}

	private fun createChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val nm = getSystemService(NotificationManager::class.java)
			val ch = NotificationChannel(CHANNEL_ID, "ROM Download", NotificationManager.IMPORTANCE_HIGH)
			nm.createNotificationChannel(ch)
		}
	}

	private fun buildNotification(state: NotificationState, percent: Int, text: String): Notification {
		val intent = Intent(this, MainActivity::class.java).apply {
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
		}
		val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
		val contentIntent = PendingIntent.getActivity(this, 0, intent, flags)
		
		val title = when (state) {
			NotificationState.DOWNLOADING -> getString(R.string.downloading_rom)
			NotificationState.VERIFYING -> getString(R.string.verifying_md5)
			NotificationState.RESUMING -> getString(R.string.resuming_download)
		}
		
		val icon = when (state) {
			NotificationState.VERIFYING -> android.R.drawable.stat_notify_sync
			else -> android.R.drawable.stat_sys_download
		}
		
		val isIndeterminate = state == NotificationState.RESUMING

		return NotificationCompat.Builder(this, CHANNEL_ID)
			.setContentTitle(title)
			.setContentText(text)
			.setSmallIcon(icon)
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setProgress(100, percent, isIndeterminate)
			.setContentIntent(contentIntent)
			.build()
	}

	private fun updateNotification(state: NotificationState, percent: Int, text: String) {
		val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
		val notification = buildNotification(state, percent, text)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			// Ensure it stays in foreground if it already is, or starts if not
			startForeground(NOTIFICATION_ID, notification)
		} else {
			nm.notify(NOTIFICATION_ID, notification)
		}
	}

	private fun startMd5Verification(file: java.io.File) {
		val expected = updatePrefs.getSavedManifestData()?.md5
		// Do not stopForeground here; transition to MD5 notification
		// stopForeground(true)
		
		val msg = "" // Progress text handled by buildNotification / state
		updateNotification(NotificationState.VERIFYING, 0, getString(R.string.verifying_md5))
		
		// Set MD5 as active and persist state with PATH
		updatePrefs.setMd5State(true, 0, file.absolutePath)
		
		// Send initial MD5 progress broadcast
		androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_MD5_PROGRESS).apply {
			putExtra(EXTRA_PERCENT, 0)
		})
		
		md5Verifier.verify(
			file = file,
			expectedMd5 = expected ?: "",
			onProgress = { p ->
				if (wasCanceled) return@verify
				// Update notification, UI broadcast, and persist progress
				updatePrefs.setMd5State(true, p.percent, file.absolutePath)
				updateNotification(NotificationState.VERIFYING, p.percent, getString(R.string.verifying_md5))
				androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_MD5_PROGRESS).apply {
					putExtra(EXTRA_PERCENT, p.percent)
				})
			},
			onResult = { ok ->
				if (wasCanceled) return@verify
				
				if (ok) {
					updatePrefs.setMd5Result(UpdatePrefs.MD5_STATUS_SUCCESS, 100)
					md5Error.cancel()
					androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_MD5_SUCCESS).apply { putExtra(EXTRA_PATH, file.absolutePath) })
				} else {
					// clear any md5 persisted state on failure
					updatePrefs.clearMd5State()
					md5Error.showMd5Failure()
					androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_MD5_FAILED))
				}
				stopForeground(true)
				unregisterNetworkCallbackSafe()
				stopSelf()
			},
			shouldCancel = { wasCanceled }
		)
	}

	override fun onBind(intent: Intent?): IBinder? = null
}


