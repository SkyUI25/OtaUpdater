package com.lich.updater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean
)

class AppUpdateChecker(private val context: Context) {

    suspend fun checkForAppUpdate(): AppReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            // Usually the repo is MrDemonc/LichApps or MrDemonc/Updater. Adjust if needed.
            // Using LichApps since the git remote points to it.
            val repoUrl = "https://api.github.com/repos/MrDemonc/LichApps/releases/latest"
            val url = URL(repoUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            // Add Authorization header for private repository
            val token = BuildConfig.GPR_KEY
            if (token.isNotEmpty()) {
                connection.setRequestProperty("Authorization", "token $token")
            }
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val bufferedReader = BufferedReader(InputStreamReader(inputStream))
                val response = StringBuilder()
                
                var line: String?
                while (bufferedReader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                
                bufferedReader.close()
                inputStream.close()
                connection.disconnect()
                
                val jsonObject = JSONObject(response.toString())
                val tagName = jsonObject.getString("tag_name").replace("v", "") // handle 'v2.0.1' or '2.0.1'
                val currentVersion = BuildConfig.VERSION_NAME.replace("v", "")
                
                // Find APK asset
                val assets = jsonObject.getJSONArray("assets")
                var downloadUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.getString("url") // GitHub API asset url requires auth to download
                        break
                    }
                }
                
                val isUpdate = isVersionGreater(tagName, currentVersion)
                return@withContext AppReleaseInfo(tagName, downloadUrl, isUpdate)
            } else {
                android.util.Log.e("AppUpdateChecker", "Failed to fetch releases: ${connection.responseCode}")
                connection.disconnect()
                return@withContext null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
    
    private fun isVersionGreater(newVersion: String, currentVersion: String): Boolean {
        try {
            val newParts = newVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
            
            val maxLength = maxOf(newParts.size, currentParts.size)
            for (i in 0 until maxLength) {
                val n = if (i < newParts.size) newParts[i] else 0
                val c = if (i < currentParts.size) currentParts[i] else 0
                if (n > c) return true
                if (n < c) return false
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to simple string comparison if parsing fails
            return newVersion > currentVersion
        }
    }
}
