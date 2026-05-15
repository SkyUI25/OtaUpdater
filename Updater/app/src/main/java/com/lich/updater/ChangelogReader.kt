package com.lich.updater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ChangelogReader(private val context: Context) {

    suspend fun fetchChangelog(changelogUrl: String): List<String>? = withContext(Dispatchers.IO) {
        try {
            val url = URL(changelogUrl)
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val bufferedReader = BufferedReader(InputStreamReader(inputStream))
                val changelogLines = mutableListOf<String>()

                var line: String?
                while (bufferedReader.readLine().also { line = it } != null) {
                    line?.let { 
                        if (it.trim().isNotEmpty()) {
                            changelogLines.add(it.trim())
                        }
                    }
                }

                bufferedReader.close()
                inputStream.close()
                connection.disconnect()

                return@withContext changelogLines.ifEmpty { null }
            } else {
                connection.disconnect()
                return@withContext null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    fun formatChangelog(changelogLines: List<String>): String {
        return changelogLines.joinToString("\n") { "• $it" }
    }
}
