
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
    val size: String,
    val url: String,
    val changelogUrl: String,
    val version: String,
    val type: String
)

sealed class ManifestResult {
    data class Success(val data: ManifestData) : ManifestResult()
    object DeviceNotSupported : ManifestResult()
    object NoInternet : ManifestResult()
    object Error : ManifestResult()
}

fun formatVersion(version: String): String {
    if (version.length != 6 || !version.all { it.isDigit() }) {
        return version
    }

    val major = version.substring(0, 2).toInt()
    val minor = version.substring(2, 4).toInt()
    val patch = version.substring(4, 6).toInt()

    return when {
        patch != 0 -> "$major.$minor.$patch"
        minor != 0 -> "$major.$minor"
        else -> "$major"
    }
}

class GetManifest(private val context: Context) {

    companion object {
        private const val MANIFEST_URL =
            "https://raw.githubusercontent.com/SkyUI25/ota_manifest/refs/heads/master/manifest.json"
    }

    suspend fun fetchManifestData(
        installedVersion: String
    ): ManifestResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null

        try {
            val url = URL("$MANIFEST_URL?t=${System.currentTimeMillis()}")
            connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("Pragma", "no-cache")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use {
                    it.readText()
                }

                android.util.Log.d(
                    "GetManifest",
                    "Fetched manifest: $response"
                )

                parseManifestJson(response, installedVersion)
            } else {
                ManifestResult.Error
            }
        } catch (e: java.io.IOException) {
            e.printStackTrace()
            ManifestResult.NoInternet
        } catch (e: Exception) {
            e.printStackTrace()
            ManifestResult.Error
        } finally {
            connection?.disconnect()
        }
    }

    private fun getDeviceName(): String {
        return try {
            val process = Runtime.getRuntime().exec(
                "getprop ro.product.vendor.device"
            )

            val reader = BufferedReader(
                InputStreamReader(process.inputStream)
            )

            val output = reader.readLine()?.trim()
            reader.close()
            process.waitFor()

            if (!output.isNullOrBlank()) {
                output
            } else {
                android.os.Build.DEVICE
            }
        } catch (e: Exception) {
            android.os.Build.DEVICE
        }
    }

    private fun parseManifestJson(
        jsonString: String,
        installedVersion: String
    ): ManifestResult {
        return try {
            if (installedVersion.toLongOrNull() == null) {
                android.util.Log.e(
                    "GetManifest",
                    "Invalid installed version: $installedVersion"
                )
                return ManifestResult.Error
            }

            val jsonObject = JSONObject(jsonString)
            val responseArray = jsonObject.getJSONArray("response")
            val currentDevice = getDeviceName().trim()

            var deviceFound = false

            val fullCandidates = mutableListOf<ManifestData>()
            val partialCandidates = mutableListOf<ManifestData>()

            android.util.Log.d(
                "GetManifest",
                "Current device: '$currentDevice', Installed version: '$installedVersion'"
            )

            for (i in 0 until responseArray.length()) {
                val item = responseArray.getJSONObject(i)

                val manifestDevice = item.optString("device").trim()

                val matchesDevice = manifestDevice
                    .split(Regex("\\s+"))
                    .any {
                        it.equals(currentDevice, ignoreCase = true)
                    }

                android.util.Log.d(
                    "GetManifest",
                    "Checking entry: device='$manifestDevice', type='${item.optString("type")}', version='${item.optString("version")}'"
                )

                if (!matchesDevice) {
                    continue
                }

                deviceFound = true

                
                val md5 = item.optString("md5")
                val sizeBytes = item.optLong("size", -1L)
                val url = item.optString("url")
                val changelogUrl = item.optString("changelog")
                val version = item.optString("version")
                val type = item.optString("type").lowercase()

                if (
                    md5.isBlank() ||
                    sizeBytes <= 0L ||
                    url.isBlank() ||
                    changelogUrl.isBlank() ||
                    version.toLongOrNull() == null ||
                    (type != "full" && type != "partial")
                ) {
                    android.util.Log.w(
                        "GetManifest",
                        "Skipping invalid manifest entry at index $i"
                    )
                    continue
                }

                val manifestData = ManifestData(
                    md5 = md5,
                    size = formatSize(sizeBytes),
                    url = url,
                    changelogUrl = changelogUrl,
                    version = version,
                    type = type
                )

                val comparison = compareVersions(
                    installedVersion,
                    manifestData.version,
                    manifestData.type
                )

                if (comparison != DateComparisonResult.UPDATE_AVAILABLE) {
                    android.util.Log.d(
                        "GetManifest",
                        "Candidate rejected: type=${manifestData.type}, version=${manifestData.version}, result=$comparison"
                    )
                    continue
                }

                when (manifestData.type) {
                    "full" -> fullCandidates.add(manifestData)
                    "partial" -> partialCandidates.add(manifestData)
                }
            }

            if (!deviceFound) {
                android.util.Log.w(
                    "GetManifest",
                    "Device '$currentDevice' is not supported"
                )

                return ManifestResult.DeviceNotSupported
            }

            // Full always has priority over Partial.
            // Select the highest compatible version in each category.
            val selectedManifest = fullCandidates.maxByOrNull {
                it.version.toLong()
            } ?: partialCandidates.maxByOrNull {
                it.version.toLong()
            }

            if (selectedManifest == null) {
                android.util.Log.d(
                    "GetManifest",
                    "Device supported, but no compatible update found for $installedVersion"
                )

                return ManifestResult.Success(
                    ManifestData(
                        md5 = "",
                        size = "",
                        url = "",
                        changelogUrl = "",
                        version = installedVersion,
                        type = "none"
                    )
                )
            }

            android.util.Log.d(
                "GetManifest",
                "Selected update: type=${selectedManifest.type}, version=${selectedManifest.version}"
            )

            ManifestResult.Success(selectedManifest)

        } catch (e: Exception) {
            e.printStackTrace()
            ManifestResult.Error
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
        manifestVersion: String,
        type: String
    ): DateComparisonResult {
        return try {
            val installed = installedVersion.toLongOrNull()
            val available = manifestVersion.toLongOrNull()

            when {
                installed == null || available == null ->
                    DateComparisonResult.UNKNOWN

                installedVersion.length != 6 ||
                manifestVersion.length != 6 ->
                    DateComparisonResult.UNKNOWN

                !installedVersion.all { it.isDigit() } ||
                !manifestVersion.all { it.isDigit() } ->
                    DateComparisonResult.UNKNOWN

                installed >= available ->
                    DateComparisonResult.UP_TO_DATE

                installedVersion.take(2) != manifestVersion.take(2) ->
                    DateComparisonResult.DEVICE_NOT_SUPPORTED

                type.equals("partial", ignoreCase = true) ->
                    if (available - installed == 1L) {
                        DateComparisonResult.UPDATE_AVAILABLE
                    } else {
                        DateComparisonResult.DEVICE_NOT_SUPPORTED
                    }

                type.equals("full", ignoreCase = true) ->
                    DateComparisonResult.UPDATE_AVAILABLE

                else ->
                    DateComparisonResult.UNKNOWN
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
    DEVICE_NOT_SUPPORTED,
    UNKNOWN,
    NO_INTERNET
}
