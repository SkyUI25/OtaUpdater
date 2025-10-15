package com.lich.updater

import android.content.Context

class UpdatePrefs(context: Context) {
	private val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)

	fun isUpdateAvailable(): Boolean {
		return prefs.getBoolean(KEY_UPDATE_AVAILABLE, false)
	}

	fun saveManifestData(manifestData: ManifestData) {
		prefs.edit()
			.putBoolean(KEY_UPDATE_AVAILABLE, true)
			.putString(KEY_BUILD_DATE, manifestData.buildDate)
			.putString(KEY_MD5, manifestData.md5)
			.putString(KEY_PATCH, manifestData.patch)
			.putString(KEY_SIZE, manifestData.size)
			.putString(KEY_BOOTLOADER, manifestData.bl)
			.putString(KEY_URL, manifestData.url)
			.putString(KEY_VERSION, manifestData.version)
			.apply()
	}

	fun getSavedManifestData(): ManifestData? {
		if (!isUpdateAvailable()) return null
		val buildDate = prefs.getString(KEY_BUILD_DATE, null) ?: return null
		val md5 = prefs.getString(KEY_MD5, null) ?: return null
		val patch = prefs.getString(KEY_PATCH, null) ?: return null
		val size = prefs.getString(KEY_SIZE, null) ?: return null
		val bl = prefs.getString(KEY_BOOTLOADER, null) ?: return null
		val url = prefs.getString(KEY_URL, null) ?: return null
		val version = prefs.getString(KEY_VERSION, null) ?: return null
		return ManifestData(
			buildDate = buildDate,
			md5 = md5,
			patch = patch,
			size = size,
			bl = bl,
			url = url,
			version = version
		)
	}

	fun clearUpdate() {
		prefs.edit()
			.putBoolean(KEY_UPDATE_AVAILABLE, false)
			.remove(KEY_BUILD_DATE)
			.remove(KEY_MD5)
			.remove(KEY_PATCH)
			.remove(KEY_SIZE)
			.remove(KEY_BOOTLOADER)
			.remove(KEY_URL)
			.remove(KEY_VERSION)
			.remove(KEY_CHANGELOG)
			.remove(KEY_BOOTLOADER_INCOMPATIBLE)
			.apply()
	}

	fun saveChangelog(changelog: String) {
		prefs.edit()
			.putString(KEY_CHANGELOG, changelog)
			.apply()
	}

	fun getSavedChangelog(): String? {
		return prefs.getString(KEY_CHANGELOG, null)
	}

	fun saveDownloadState(active: Boolean, percent: Int = 0, eta: String = "time remaining: 00:00:00", path: String? = null) {
		prefs.edit()
			.putBoolean(KEY_DL_ACTIVE, active)
			.putInt(KEY_DL_PERCENT, percent)
			.putString(KEY_DL_ETA, eta)
			.apply()
		path?.let { prefs.edit().putString(KEY_DL_PATH, it).apply() }
	}

	fun isDownloadActive(): Boolean = prefs.getBoolean(KEY_DL_ACTIVE, false)
	fun getDownloadPercent(): Int = prefs.getInt(KEY_DL_PERCENT, 0)
	fun getDownloadEta(): String = prefs.getString(KEY_DL_ETA, "time remaining: 00:00:00") ?: "time remaining: 00:00:00"
	fun getDownloadPath(): String? = prefs.getString(KEY_DL_PATH, null)
	fun clearDownload() {
		prefs.edit()
			.putBoolean(KEY_DL_ACTIVE, false)
			.remove(KEY_DL_PERCENT)
			.remove(KEY_DL_ETA)
			.remove(KEY_DL_PATH)
			.apply()
	}

	fun setNotificationPermissionAsked(granted: Boolean) {
		prefs.edit()
			.putBoolean(KEY_NOTIF_ASKED, true)
			.putBoolean(KEY_NOTIF_GRANTED, granted)
			.apply()
	}

	fun isNotificationPermissionAsked(): Boolean = prefs.getBoolean(KEY_NOTIF_ASKED, false)
	fun isNotificationPermissionGranted(): Boolean = prefs.getBoolean(KEY_NOTIF_GRANTED, false)

	// MD5 verification state
	fun setMd5State(active: Boolean, percent: Int = 0) {
		prefs.edit()
			.putBoolean(KEY_MD5_ACTIVE, active)
			.putInt(KEY_MD5_PERCENT, percent)
			.putString(KEY_MD5_STATUS, if (active) MD5_STATUS_ACTIVE else MD5_STATUS_NONE)
			.apply()
	}

	fun setMd5Result(status: String, percent: Int = 100) {
		prefs.edit()
			.putString(KEY_MD5_STATUS, status)
			.putInt(KEY_MD5_PERCENT, percent)
			.putBoolean(KEY_MD5_ACTIVE, status == MD5_STATUS_ACTIVE)
			.apply()
	}

	fun getMd5Status(): String = prefs.getString(KEY_MD5_STATUS, MD5_STATUS_NONE) ?: MD5_STATUS_NONE
	fun isMd5Active(): Boolean = prefs.getBoolean(KEY_MD5_ACTIVE, false)
	fun getMd5Percent(): Int = prefs.getInt(KEY_MD5_PERCENT, 0)
	fun clearMd5State() {
		prefs.edit()
			.putBoolean(KEY_MD5_ACTIVE, false)
			.putString(KEY_MD5_STATUS, MD5_STATUS_NONE)
			.remove(KEY_MD5_PERCENT)
			.apply()
	}

	companion object {
		// Public MD5 status constants
		const val MD5_STATUS_NONE = "none"
		const val MD5_STATUS_ACTIVE = "active"
		const val MD5_STATUS_SUCCESS = "success"
		const val MD5_STATUS_FAILED = "failed"
		
		// Public Install status constants
		const val INSTALL_STATUS_NONE = "none"
		const val INSTALL_STATUS_READY = "ready"
		const val INSTALL_STATUS_INSTALLING = "installing"
		const val INSTALL_STATUS_SUCCESS = "success"
		const val INSTALL_STATUS_FAILED = "failed"

		// Private preference keys
		private const val KEY_UPDATE_AVAILABLE = "update_available"
		private const val KEY_BUILD_DATE = "build_date"
		private const val KEY_MD5 = "md5"
		private const val KEY_PATCH = "patch"
		private const val KEY_SIZE = "size"
		private const val KEY_BOOTLOADER = "bootloader"
		private const val KEY_URL = "url"
		private const val KEY_VERSION = "version"
		private const val KEY_CHANGELOG = "changelog"
		private const val KEY_DL_ACTIVE = "dl_active"
		private const val KEY_DL_PERCENT = "dl_percent"
		private const val KEY_DL_ETA = "dl_eta"
		private const val KEY_DL_PATH = "dl_path"
		private const val KEY_NOTIF_ASKED = "notif_asked"
		private const val KEY_NOTIF_GRANTED = "notif_granted"
		private const val KEY_MD5_ACTIVE = "md5_active"
		private const val KEY_MD5_PERCENT = "md5_percent"
		private const val KEY_MD5_STATUS = "md5_status"
		private const val KEY_INSTALL_STATUS = "install_status"
		private const val KEY_INSTALL_FILE_PATH = "install_file_path"
		private const val KEY_USE_MOBILE_DATA = "use_mobile_data"
		private const val KEY_UPDATE_NOTIFICATIONS = "update_notifications"
		private const val KEY_BOOTLOADER_INCOMPATIBLE = "bootloader_incompatible"
	}
	
	// Install state management
	fun setInstallStatus(status: String, filePath: String? = null) {
		prefs.edit()
			.putString(KEY_INSTALL_STATUS, status)
			.apply()
		if (filePath != null) {
			prefs.edit()
				.putString(KEY_INSTALL_FILE_PATH, filePath)
				.apply()
		}
	}
	
	fun getInstallStatus(): String = prefs.getString(KEY_INSTALL_STATUS, INSTALL_STATUS_NONE) ?: INSTALL_STATUS_NONE
	fun getInstallFilePath(): String? = prefs.getString(KEY_INSTALL_FILE_PATH, null)
	fun isInstallReady(): Boolean = getInstallStatus() == INSTALL_STATUS_READY
	fun isInstalling(): Boolean = getInstallStatus() == INSTALL_STATUS_INSTALLING
	
	fun clearInstallState() {
		prefs.edit()
			.putString(KEY_INSTALL_STATUS, INSTALL_STATUS_NONE)
			.remove(KEY_INSTALL_FILE_PATH)
			.apply()
	}
	
	// Mobile data preference management
	fun setUseMobileData(enabled: Boolean) {
		prefs.edit()
			.putBoolean(KEY_USE_MOBILE_DATA, enabled)
			.apply()
	}
	
	fun isUseMobileDataEnabled(): Boolean = prefs.getBoolean(KEY_USE_MOBILE_DATA, false) // Default: false (disabled)
	
	// Update notifications preference management
	fun setUpdateNotificationsEnabled(enabled: Boolean) {
		prefs.edit()
			.putBoolean(KEY_UPDATE_NOTIFICATIONS, enabled)
			.apply()
	}
	
	fun isUpdateNotificationsEnabled(): Boolean = prefs.getBoolean(KEY_UPDATE_NOTIFICATIONS, true) // Default: true (enabled)
	
	// Bootloader compatibility state management
	fun saveBootloaderIncompatible(incompatible: Boolean) {
		prefs.edit()
			.putBoolean(KEY_BOOTLOADER_INCOMPATIBLE, incompatible)
			.apply()
	}
	
	fun isBootloaderIncompatible(): Boolean = prefs.getBoolean(KEY_BOOTLOADER_INCOMPATIBLE, false)
	
	fun clearBootloaderIncompatible() {
		prefs.edit()
			.remove(KEY_BOOTLOADER_INCOMPATIBLE)
			.apply()
	}
}


