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
        private const val LICHUI_VERSION_PROP = "ro.on.core.version"
        private const val BUILD_DATE_PROP = "ro.on.core.build"
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
            val properties = listOf(ONEUI_VERSION_PROP, LICHUI_VERSION_PROP, BUILD_DATE_PROP, VENDOR_BUILD_VERSION_PROP)
            
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
            val minor = (versionInt % 10000) / 1000
            "$major.$minor"
        } catch (e: NumberFormatException) {
            context.getString(R.string.unknown)
        }
    }
    
    fun getLichUIVersion(): String {
        return buildPropMap[LICHUI_VERSION_PROP] ?: context.getString(R.string.unknown)
    }
    
    fun getBuildDate(): String {
        val buildDate = buildPropMap[BUILD_DATE_PROP] ?: return context.getString(R.string.unknown)
        return try {
            // Parse date like "20251006" to "2025/10/06"
            if (buildDate.length == 8) {
                val year = buildDate.substring(0, 4)
                val month = buildDate.substring(4, 6)
                val day = buildDate.substring(6, 8)
                "$year/$month/$day"
            } else {
                context.getString(R.string.unknown)
            }
        } catch (e: Exception) {
            context.getString(R.string.unknown)
        }
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
