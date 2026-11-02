package com.lich.updater

import android.content.Context
import java.io.BufferedReader
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.*

class BuildPropReader(private val context: Context) {
    
    companion object {
        private const val BUILD_PROP_PATH = "/system/build.prop"
        private const val ONEUI_VERSION_PROP = "ro.build.version.oneui"
        private const val SKYUI_VERSION_PROP = "ro.skyui.version"
        private const val VENDOR_BUILD_VERSION_PROP = "ro.vendor.build.version.incremental"
    }
    
    private val buildPropMap = mutableMapOf<String, String>()
    
    init {
        loadBuildProp()
    }
    
    private fun loadBuildProp() {
        // Use getprop command directly (no root required)
        loadBuildPropViaGetprop()
    }
    
    private fun loadBuildPropViaGetprop() {
        try {
            val properties = listOf(ONEUI_VERSION_PROP, SKYUI_VERSION_PROP, VENDOR_BUILD_VERSION_PROP)
            
            for (prop in properties) {
                try {
                    val process = Runtime.getRuntime().exec("getprop $prop")
                    val reader = BufferedReader(process.inputStream.reader())
                    val value = reader.readLine()?.trim()
                    reader.close()
                    process.destroy()
                    
                    if (!value.isNullOrEmpty() && value != "null" && value != "undefined") {
                        buildPropMap[prop] = value
                        android.util.Log.d("BuildPropReader", "Successfully read $prop = $value")
                    } else {
                        android.util.Log.w("BuildPropReader", "Property $prop returned empty or null value: $value")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Continue with other properties even if one fails
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        android.util.Log.d("BuildPropReader", "Loaded ${buildPropMap.size} properties: $buildPropMap")
    }
    
    fun getOneUIVersion(): String {
        val version = buildPropMap[ONEUI_VERSION_PROP] ?: return context.getString(R.string.unknown)
        return try {
            // Parse version like "80000" to "8.0"
            val versionInt = version.toInt()
            val major = versionInt / 10000
            val minor = (versionInt % 10000) / 100
            "$major.$minor"
        } catch (e: NumberFormatException) {
            context.getString(R.string.unknown)
        }
    }
    
    fun getSkyUIVersion(): String {
        return buildPropMap[SKYUI_VERSION_PROP] ?: context.getString(R.string.unknown)
    }
    
    fun getBuildDate(): String {
        return getSkyUIVersion()
    }
    
	// Kompatibilitas sementara untuk kode lama.
    fun getLichUIVersion(): String {
        return getSkyUIVersion()
    }
	
    fun getVendorBuildVersion(): String {
        return buildPropMap[VENDOR_BUILD_VERSION_PROP] ?: context.getString(R.string.unknown)
    }
    
    fun getProperty(key: String): String {
        return buildPropMap[key] ?: context.getString(R.string.unknown)
    }
    
    fun isPropertyAvailable(key: String): Boolean {
        return buildPropMap.containsKey(key) && 
               buildPropMap[key] != context.getString(R.string.unknown)
    }
    
    fun getAllAvailableProperties(): Map<String, String> {
        return buildPropMap.toMap()
    }
}
