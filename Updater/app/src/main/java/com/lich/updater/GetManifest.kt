package com.lich.updater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class ManifestData(
    val md5: String,
    val patch: String,
    val size: String,
    val bl: String,
    val url: String,
    val changelogUrl: String,
    val version: String
)

class GetManifest(private val context: Context) {

    companion object {
        private const val MANIFEST_URL =
            "https://raw.githubusercontent.com/SkyUI25/ota_manifest/refs/heads/master/manifest.json"
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

    private fun getDeviceName(): String {
        return try {
            val process = Runtime.getRuntime().exec("getprop ro.product.device")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()?.trim()
            reader.close()
            process.waitFor()

            if (!output.isNullOrBlank()) output else android.os.Build.DEVICE
        } catch (e: Exception) {
            android.os.Build.DEVICE
        }
    }

    private fun parseManifestJson(jsonString: String): ManifestData? {
        return try {
            val jsonObject = JSONObject(jsonString)
            val responseArray = jsonObject.getJSONArray("response")
            val currentDevice = getDeviceName()

            android.util.Log.d(
                "GetManifest",
                "Current device detected as: '$currentDevice'"
            )

            for (i in 0 until responseArray.length()) {
                val item = responseArray.getJSONObject(i)

                if (item.has("device") && item.getString("device") == currentDevice) {
                    return ManifestData(
                        md5 = item.getString("md5"),
                        patch = formatPatchDate(item.getInt("patch")),
                        size = formatSize(item.getLong("size")),
                        bl = item.getString("bl"),
                        url = item.getString("url"),
                        changelogUrl = item.getString("changelog"),
                        version = item.getString("version")
                    )
                }
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun formatPatchDate(dateInt: Int): String {
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

    fun compareVersions(
        installedVersion: String,
        manifestVersion: String
    ): DateComparisonResult {
        return try {
            val installed = installedVersion.toLongOrNull()
            val available = manifestVersion.toLongOrNull()

            when {
                installed == null || available == null ->
                    DateComparisonResult.UNKNOWN

                installed >= available ->
                    DateComparisonResult.UP_TO_DATE

                else ->
                    DateComparisonResult.UPDATE_AVAILABLE
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
