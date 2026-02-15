package com.lich.updater

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class RomDownloader(private val context: Context) {
	private val client = OkHttpClient.Builder()
		.connectTimeout(30, TimeUnit.SECONDS)
		.readTimeout(30, TimeUnit.SECONDS)
		.followRedirects(true)
		.followSslRedirects(true)
		.build()

	@Volatile private var currentCall: Call? = null
	@Volatile private var lastOutputFile: File? = null

	data class Progress(val percent: Int, val etaText: String)

	private fun isNetworkAvailable(): Boolean {
		val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
		val network = connectivityManager.activeNetwork ?: return false
		val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
		return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
	}

	private fun isMobileDataAllowed(): Boolean {
		val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
		val network = connectivityManager.activeNetwork ?: return false
		val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
		
		// Check if we're on mobile data
		val isMobileData = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
		
		if (isMobileData) {
			// Check user preference for mobile data usage
			val updatePrefs = UpdatePrefs(context)
			return updatePrefs.isUseMobileDataEnabled()
		}
		
		// WiFi or other connections are always allowed
		return true
	}

	fun download(
		url: String,
		requestedFileName: String?,
		onProgress: (Progress) -> Unit,
		onCompleted: (File) -> Unit,
		onFailed: (Throwable) -> Unit
	) {
		try {
			if (!isNetworkAvailable()) {
				onFailed(IOException("No network connection available"))
				return
			}
			
			if (!isMobileDataAllowed()) {
				onFailed(IOException("Mobile data usage is disabled in settings"))
				return
			}
			
			val downloadDir = context.getExternalFilesDir(null) ?: context.filesDir
			if (!downloadDir.exists()) downloadDir.mkdirs()

			val fileName = requestedFileName ?: inferFileName(url)
			val outputFile = File(downloadDir, fileName)
			lastOutputFile = outputFile

			var downloadedBytes = 0L
			if (outputFile.exists()) {
				downloadedBytes = outputFile.length()
			}

			Log.d("RomDownloader", "Start download url=$url file=${outputFile.absolutePath} existingBytes=$downloadedBytes")

			val requestBuilder = Request.Builder().url(url)
			if (downloadedBytes > 0) {
				requestBuilder.header("Range", "bytes=$downloadedBytes-")
			}
			val request = requestBuilder.build()

			val call = client.newCall(request)
			currentCall = call
			val response = call.execute()
			
			response.use {
				if (!response.isSuccessful) {
                    // Handle 416 Range Not Satisfiable (e.g., file already complete)
                    if (response.code == 416) {
						// Check if file is actually complete by fetching full size
						val headRequest = request.newBuilder().removeHeader("Range").head().build()
						client.newCall(headRequest).execute().use { headResponse ->
							if (headResponse.isSuccessful) {
								val fullLength = headResponse.header("Content-Length")?.toLongOrNull() ?: -1L
								if (fullLength > 0 && downloadedBytes == fullLength) {
									Log.d("RomDownloader", "File already fully downloaded (416). Completing.")
									onCompleted(outputFile)
									return
								}
							}
						}
                         Log.w("RomDownloader", "Range not satisfiable, maybe file is already complete? Redownloading.")
                         throw IOException("HTTP 416: Range not satisfiable")
                    }
					throw IOException("HTTP ${response.code}")
				}

				val responseCode = response.code
				val isResume = responseCode == 206
				val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
				// If resume, total = existing + remaining. If not resume, total = content length
				val totalBytes = if (isResume) downloadedBytes + contentLength else contentLength

				Log.d("RomDownloader", "HTTP $responseCode resume=$isResume length=$contentLength total=$totalBytes")

				val contentType = response.header("Content-Type") ?: ""
				if (contentType.startsWith("text/html")) throw IOException("HTML response for download URL")

				// Check Content-Disposition for filename
				// Only if fresh download or if we want to ensure we are appending to correct file (though we use existing outputFile for append)
				// For simplicity, we only rename on fresh download start (downloadedBytes == 0) to avoid messy rename logic during resume
				var currentOutputFile = outputFile
				if (!isResume && downloadedBytes == 0L) {
					val contentDisposition = response.header("Content-Disposition")
					if (contentDisposition != null) {
						val serverFileName = parseFileNameFromContentDisposition(contentDisposition)
						if (serverFileName != null && serverFileName != fileName) {
							Log.d("RomDownloader", "Server provided filename: $serverFileName")
							// Check if we should switch to this new filename
							val newOutputFile = File(downloadDir, serverFileName)
							
							// If the new filename already exists and we are starting fresh, we might overwrite it
							// But to be safe, let's switch to it.
							currentOutputFile = newOutputFile
							lastOutputFile = currentOutputFile
						}
					}
				}

				// If server didn't accept partial (sent 200 instead of 206), we must overwrite
				val append = isResume
				if (!isResume && downloadedBytes > 0) {
					// We requested range but server sent 200. Overwrite file.
					downloadedBytes = 0 
				}

				response.body?.byteStream()?.use { input ->
					FileOutputStream(currentOutputFile, append).use { output ->
						val buffer = ByteArray(32 * 1024)
						var bytesRead: Int
						var currentDownloaded = downloadedBytes
						var lastReportTime = System.currentTimeMillis()
						var lastBytes = currentDownloaded

						while (input.read(buffer).also { bytesRead = it } != -1) {
							output.write(buffer, 0, bytesRead)
							currentDownloaded += bytesRead
							
							val now = System.currentTimeMillis()
							if (now - lastReportTime >= 1000) {
								val percent = if (totalBytes > 0) ((currentDownloaded * 100) / totalBytes).toInt() else 0
                                val timeDelta = now - lastReportTime
								val speed = if (timeDelta > 0) ((currentDownloaded - lastBytes) * 1000) / timeDelta else 0L
								
								lastBytes = currentDownloaded
								lastReportTime = now
								
								val remainingBytes = if (totalBytes > 0) totalBytes - currentDownloaded else 0L
								val remainingSeconds = if (speed > 0) remainingBytes / speed else 0L
								
								val eta = formatEta(remainingSeconds)
                                // Add resume indicator to log
								Log.d("RomDownloader", "progress $currentDownloaded/$totalBytes ($percent%) speed=${speed}Bps eta=$eta")
								onProgress(Progress(percent, eta))
							}
						}
                        output.flush()
					}
				}

				if (totalBytes > 0 && outputFile.length() != totalBytes) {
					throw IOException("Download size mismatch. Expected=$totalBytes, Got=${outputFile.length()}")
				}

				onCompleted(outputFile)
			}
		} catch (t: Throwable) {
			Log.e("RomDownloader", "Download error: ${t.message}", t)
			onFailed(t)
		} finally {
			currentCall = null
		}
	}

	fun cancel() {
		try { currentCall?.cancel() } catch (_: Throwable) {}
	}

	fun getLastOutputFile(): File? = lastOutputFile

	private fun inferFileName(url: String): String {
		// Remove query parameters to get the base path for name inference
		val baseUrl = url.substringBefore("?")
		val path = try { URLDecoder.decode(baseUrl, "UTF-8") } catch (_: Exception) { baseUrl }
		val last = path.substringAfterLast('/')
		return if (last.isNotBlank()) last else "rom.zip"
	}

	private fun formatEta(seconds: Long): String {
		var s = seconds
		val h = (s / 3600).toInt()
		s %= 3600
		val m = (s / 60).toInt()
		s %= 60
		val sec = s.toInt()
		return context.getString(R.string.time_remaining_format, h, m, sec)
	}

	private fun parseFileNameFromContentDisposition(contentDisposition: String): String? {
		try {
			// standard: filename="foo.zip"
			val filenameRegex = """filename="?([^";]+)"?""".toRegex(RegexOption.IGNORE_CASE)
			val match = filenameRegex.find(contentDisposition)
			if (match != null) {
				return match.groupValues[1]
			}
			
			// extended: filename*=UTF-8''foo%20bar.zip
			val filenameStarRegex = """filename\*=UTF-8''([^;]+)""".toRegex(RegexOption.IGNORE_CASE)
			val matchStar = filenameStarRegex.find(contentDisposition)
			if (matchStar != null) {
				return URLDecoder.decode(matchStar.groupValues[1], "UTF-8")
			}
		} catch (e: Exception) {
			Log.e("RomDownloader", "Error parsing Content-Disposition: ${e.message}")
		}
		return null
	}
}


