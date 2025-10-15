package com.lich.updater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

data class ManifestData(
    val buildDate: String,
    val md5: String,
    val patch: String,
    val size: String,
    val bl: String,
    val url: String,
    val version: String
)

class GetManifest(private val context: Context) {
    
    companion object {
        private const val MANIFEST_URL = "https://raw.githubusercontent.com/MrDemon-LICH/PROJECT-LichUI/refs/heads/main/Update/manifest.json"
    }
    
    suspend fun fetchManifestData(): ManifestData? = withContext(Dispatchers.IO) {
        try {
            val url = URL(MANIFEST_URL)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
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
                
                return@withContext parseManifestJson(response.toString())
            } else {
                connection.disconnect()
                return@withContext null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
    
    private fun parseManifestJson(jsonString: String): ManifestData? {
        return try {
            val jsonObject = JSONObject(jsonString)
            val responseArray = jsonObject.getJSONArray("response")
            
            if (responseArray.length() > 0) {
                val firstItem = responseArray.getJSONObject(0)
                
                ManifestData(
                    buildDate = formatDate(firstItem.getInt("builddate")),
                    md5 = firstItem.getString("md5"),
                    patch = formatDate(firstItem.getInt("patch")),
                    size = formatSize(firstItem.getLong("size")),
                    bl = firstItem.getString("bl"),
                    url = firstItem.getString("url"),
                    version = firstItem.getString("version")
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun formatDate(dateInt: Int): String {
        return try {
            val dateString = dateInt.toString()
            if (dateString.length == 8) {
                val year = dateString.substring(0, 4)
                val month = dateString.substring(4, 6)
                val day = dateString.substring(6, 8)
                "$year/$month/$day"
            } else {
                context.getString(R.string.unknown)
            }
        } catch (e: Exception) {
            context.getString(R.string.unknown)
        }
    }
    
    private fun formatSize(sizeBytes: Long): String {
        return try {
            val sizeGB = sizeBytes / (1024.0 * 1024.0 * 1024.0)
            String.format("%.2f GB", sizeGB)
        } catch (e: Exception) {
            context.getString(R.string.unknown)
        }
    }
    
    fun compareDates(buildPropDate: String, manifestDate: String): DateComparisonResult {
        return try {
            val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
            val propDate = dateFormat.parse(buildPropDate)
            val manifestDateParsed = dateFormat.parse(manifestDate)
            
            when {
                propDate == null || manifestDateParsed == null -> DateComparisonResult.UNKNOWN
                propDate >= manifestDateParsed -> DateComparisonResult.UP_TO_DATE
                else -> DateComparisonResult.UPDATE_AVAILABLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
            DateComparisonResult.UNKNOWN
        }
    }
}

enum class DateComparisonResult {
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    UNKNOWN,
    NO_INTERNET
}
