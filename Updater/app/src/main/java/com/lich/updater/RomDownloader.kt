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
			// Check network availability
			if (!isNetworkAvailable()) {
				onFailed(IOException("No network connection available"))
				return
			}
			
			// Check if mobile data is allowed
			if (!isMobileDataAllowed()) {
				onFailed(IOException("Mobile data usage is disabled in settings"))
				return
			}
			
			val cleanedUrl = cleanUrl(url)
			Log.d("RomDownloader", "Start download url=$cleanedUrl requestedFileName=$requestedFileName")

			val request = Request.Builder()
				.url(cleanedUrl)
				.build()

			val call = client.newCall(request)
			currentCall = call
			call.execute().use { response ->
				if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

				val contentType = response.header("Content-Type") ?: ""
				val totalBytes = response.header("Content-Length")?.toLongOrNull() ?: response.body?.contentLength() ?: -1L
				Log.d("RomDownloader", "HTTP ${response.code} contentType=$contentType length=$totalBytes")

				// Evitar guardar páginas HTML por error de servidor
				if (contentType.startsWith("text/html")) throw IOException("HTML response for download URL")

				val downloadDir = context.getExternalFilesDir(null) ?: context.filesDir
				if (!downloadDir.exists()) downloadDir.mkdirs()

				val fileName = requestedFileName ?: inferFileName(cleanedUrl)
				val outputFile = File(downloadDir, fileName)
				lastOutputFile = outputFile
				Log.d("RomDownloader", "Saving to: ${outputFile.absolutePath}")

				response.body?.byteStream()?.use { input ->
					FileOutputStream(outputFile, false).use { output ->
						val buffer = ByteArray(32 * 1024)
						var downloaded = 0L
						var lastReportTime = System.currentTimeMillis()
						var lastBytes = 0L
						while (true) {
							val read = input.read(buffer)
							if (read == -1) break
							output.write(buffer, 0, read)
							downloaded += read
							val now = System.currentTimeMillis()
							if (now - lastReportTime >= 1000) {
								val percent = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt() else 0
								val speed = if (now != lastReportTime) ((downloaded - lastBytes) * 1000) / (now - lastReportTime) else 0L // bytes/s
								lastBytes = downloaded
								lastReportTime = now
								val remaining = if (totalBytes > 0 && speed > 0) (totalBytes - downloaded) / speed else 0L
								val eta = formatEta(remaining)
								Log.d("RomDownloader", "progress bytes=$downloaded/$totalBytes percent=$percent speed=${speed}Bps eta=$eta")
								onProgress(Progress(percent, eta))
							}
						}
						output.flush()
					}
				}

				// Validación simple: si el server informó tamaño y no coincide, tratar como error
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

	private fun cleanUrl(url: String): String {
		return url.substringBefore("?")
	}

	private fun inferFileName(url: String): String {
		val path = try { URLDecoder.decode(url, "UTF-8") } catch (_: Exception) { url }
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
}


