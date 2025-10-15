package com.lich.updater.Md5Verifier

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class Md5Comprobations {
	data class Progress(val percent: Int)

	fun verify(
		file: File,
		expectedMd5: String,
		onProgress: (Progress) -> Unit,
		onResult: (Boolean) -> Unit
	) {
		try {
			val buffer = ByteArray(1024 * 1024)
			val total = file.length().coerceAtLeast(1L)
			var readTotal = 0L
			val md = MessageDigest.getInstance("MD5")
			FileInputStream(file).use { fis ->
				while (true) {
					val read = fis.read(buffer)
					if (read == -1) break
					md.update(buffer, 0, read)
					readTotal += read
					val percent = ((readTotal * 100) / total).toInt()
					onProgress(Progress(percent))
				}
			}
			val digest = md.digest()
			val actual = digest.joinToString(separator = "") { b -> "%02x".format(b) }
			onResult(actual.equals(expectedMd5, ignoreCase = true))
		} catch (_: Exception) {
			onResult(false)
		}
	}
}
