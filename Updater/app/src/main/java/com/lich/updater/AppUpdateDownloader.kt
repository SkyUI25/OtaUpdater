package com.lich.updater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import com.lich.updater.root.RootManager
import android.util.Log

import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class AppUpdateDownloader(private val context: Context) {

    suspend fun downloadAndInstall(releaseInfo: AppReleaseInfo): Boolean = withContext(Dispatchers.IO) {
        val downloadUrl = releaseInfo.downloadUrl
        if (downloadUrl.isEmpty()) return@withContext false

        // Check Mobile Data Preference
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
            
            // Add Authorization header for private repository asset download
            val token = BuildConfig.GPR_KEY
            if (token.isNotEmpty()) {
                connection.setRequestProperty("Authorization", "token $token")
            }
            connection.setRequestProperty("Accept", "application/octet-stream")

            if (connection.responseCode == HttpURLConnection.HTTP_OK || connection.responseCode == HttpURLConnection.HTTP_MOVED_TEMP || connection.responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
                // Handle redirects since GitHub releases might redirect to S3
                val finalUrl = if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    url
                } else {
                    URL(connection.getHeaderField("Location"))
                }
                
                val finalConnection = if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    val conn = finalUrl.openConnection() as HttpURLConnection
                    // Usually we don't send the auth token to the redirected AWS S3 bucket
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
                
                notification.showProgress(-1) // Installing state
                val success = installApk(apkFile)
                if (!success) {
                    notification.hide()
                }
                return@withContext success
            } else {
                Log.e("AppUpdateDownloader", "Failed to download APK: HTTP ${connection.responseCode}")
                connection.disconnect()
                return@withContext false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    private suspend fun installApk(apkFile: File): Boolean {
        val rootManager = RootManager()
        val hasRoot = rootManager.hasRootAccess()
        val command = "pm install -r ${apkFile.absolutePath}"
        
        return if (hasRoot) {
            Log.d("AppUpdateDownloader", "Installing via Root")
            val result = rootManager.executeCommand("su -c $command")
            result.success
        } else {
            // Attempt to install silently via standard Runtime (works if app is priv-app with INSTALL_PACKAGES permission)
            Log.d("AppUpdateDownloader", "Installing via Priv-App Runtime")
            try {
                val process = Runtime.getRuntime().exec(command)
                process.waitFor() == 0
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private fun isUsingMobileData(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}
