package com.lich.updater

import android.content.Context
import android.util.Log
import java.io.BufferedReader

class BuildPropReader(private val context: Context) {

    companion object {
        private const val ONEUI_VERSION_PROP = "ro.build.version.oneui"
        private const val SKYUI_VERSION_PROP = "ro.skyui.version"
        private const val VENDOR_BUILD_VERSION_PROP = "ro.vendor.build.version.incremental"
        private const val TAG = "BuildPropReader"
    }

    private val buildPropMap = mutableMapOf<String, String>()

    init {
        loadBuildProp()
    }

    private fun loadBuildProp() {
        loadBuildPropViaGetprop()
    }

    private fun loadBuildPropViaGetprop() {
        val properties = listOf(
            ONEUI_VERSION_PROP,
            SKYUI_VERSION_PROP,
            VENDOR_BUILD_VERSION_PROP
        )

        for (prop in properties) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("getprop", prop))
                val value = process.inputStream.bufferedReader().use {
                    it.readLine()?.trim()
                }
                process.waitFor()
                process.destroy()

                if (!value.isNullOrEmpty() && value != "null" && value != "undefined") {
                    buildPropMap[prop] = value
                    Log.d(TAG, "Successfully read $prop = $value")
                } else {
                    Log.w(TAG, "Property $prop returned empty or null value")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read property: $prop", e)
            }
        }

        Log.d(TAG, "Loaded ${buildPropMap.size} properties: $buildPropMap")
    }

    fun getOneUIVersion(): String {
        val version = buildPropMap[ONEUI_VERSION_PROP]
            ?: return context.getString(R.string.unknown)

        return try {
            val versionInt = version.toInt()
            val major = versionInt / 10000
            val minor = (versionInt % 10000) / 100
            "$major.$minor"
        } catch (e: NumberFormatException) {
            context.getString(R.string.unknown)
        }
    }

    fun getSkyUIVersion(): String {
        return buildPropMap[SKYUI_VERSION_PROP]
            ?: context.getString(R.string.unknown)
    }

    fun getVendorBuildVersion(): String {
        return buildPropMap[VENDOR_BUILD_VERSION_PROP]
            ?: context.getString(R.string.unknown)
    }

    fun getProperty(key: String): String {
        return buildPropMap[key] ?: context.getString(R.string.unknown)
    }

    fun isPropertyAvailable(key: String): Boolean {
        return buildPropMap.containsKey(key)
    }

    fun getAllAvailableProperties(): Map<String, String> {
        return buildPropMap.toMap()
    }
}
