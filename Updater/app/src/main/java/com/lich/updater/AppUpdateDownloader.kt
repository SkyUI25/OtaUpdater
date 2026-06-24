package com.lich.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.app.PendingIntent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateDownloader(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdateDownloader"
    }

    suspend fun downloadAndInstall(releaseInfo: AppReleaseInfo): Boolean = withContext(Dispatchers.IO) {
        val downloadUrl = releaseInfo.downloadUrl
        if (downloadUrl.isEmpty()) return@withContext false

        val updatePrefs = UpdatePrefs(context)
        if (!updatePrefs.isUseMobileDataEnabled() && isUsingMobileData()) {
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Mobile data download is disabled in settings.", android.widget.Toast.LENGTH_LONG).show()
            }
            return@withContext false
        }

        val apkFile = File(context.cacheDir, "update_${releaseInfo.tagName}.apk")

        try {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val token = BuildConfig.GPR_KEY
            if (token.isNotEmpty()) {
                connection.setRequestProperty("Authorization", "token $token")
            }
            connection.setRequestProperty("Accept", "application/octet-stream")

            if (connection.responseCode == HttpURLConnection.HTTP_OK || connection.responseCode == HttpURLConnection.HTTP_MOVED_TEMP || connection.responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
                val finalUrl = if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    url
                } else {
                    URL(connection.getHeaderField("Location"))
                }

                val finalConnection = if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    val conn = finalUrl.openConnection() as HttpURLConnection
                    conn
                } else connection

                val fileLength = finalConnection.contentLength
                val inputStream = finalConnection.inputStream
                val outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(4096)
                var bytesRead: Int
                var totalBytesRead: Long = 0
                val notification = com.lich.updater.Notifications.AppUpdateNotification(context)

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (fileLength > 0) {
                        val progress = ((totalBytesRead * 100) / fileLength).toInt()
                        notification.showProgress(progress)
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                if (connection != finalConnection) {
                    finalConnection.disconnect()
                }
                connection.disconnect()

                notification.showProgress(-1)
                val success = installApk(apkFile)
                if (!success) {
                    notification.hide()
                }
                return@withContext success
            } else {
                Log.e(TAG, "Failed to download APK: HTTP ${connection.responseCode}")
                connection.disconnect()
                return@withContext false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    private suspend fun installApk(apkFile: File): Boolean {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file not found: ${apkFile.absolutePath}")
            return false
        }

        // Try PackageInstaller API first (works for system/priv-app)
        if (tryPackageInstallerInstall(apkFile)) {
            return true
        }

        Log.d(TAG, "PackageInstaller API unavailable, notifying user")
        withContext(Dispatchers.Main) {
            android.widget.Toast.makeText(
                context,
                "Download complete. Open the Downloads folder to install.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
        return false
    }

    private fun tryPackageInstallerInstall(apkFile: File): Boolean {
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val sessionParams = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )

            val sessionId = packageInstaller.createSession(sessionParams)
            val session = packageInstaller.openSession(sessionId)

            val inputStream = apkFile.inputStream()
            val outputStream = session.openWrite("app_update", 0, apkFile.length())

            inputStream.copyTo(outputStream)
            session.fsync(outputStream)

            outputStream.close()
            inputStream.close()

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )

            session.commit(pendingIntent.intentSender)
            session.close()

            Log.d(TAG, "PackageInstaller install initiated successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "PackageInstaller install failed: ${e.message}")
            return false
        }
    }

    private fun isUsingMobileData(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}
