package com.lich.updater.InstallsRom

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.lich.updater.InstallsRom.RecoveryScript.GenerateScript
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

class RomInstall(private val context: Context) {
    
    companion object {
        private const val TAG = "RomInstall"
    }
    
    private val generateScript = GenerateScript(context)
    
    /**
     * Install the ROM file
     * Generate recovery script and reboot to recovery
     */
    fun installRom(filePath: String) {
        Log.d(TAG, "Starting ROM installation for: $filePath")
        
        // Generate recovery script and reboot
        CoroutineScope(Dispatchers.IO).launch {
            try {
            val scriptGenerated = generateScript.generateAndExecute(filePath)
            if (scriptGenerated) {
                Log.i(TAG, "Recovery script generated successfully, rebooting to recovery")
                rebootToRecovery()
            } else {
                Log.e(TAG, "Failed to generate recovery script")
            }
            } catch (e: Exception) {
                Log.e(TAG, "Error during ROM installation: ${e.message}", e)
            }
        }
    }
    
    /**
     * Reboot device to recovery mode
     */
    fun rebootToRecovery() {
        try {
            Log.d(TAG, "Attempting to reboot to recovery using PowerManager")
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.reboot("recovery")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
            tryAlternativeReboot()
        } catch (e: Exception) {
            Log.e(TAG, "PowerManager reboot failed: ${e.message}")
            tryAlternativeReboot()
        }
    }
    
    private fun tryAlternativeReboot() {
        try {
            Log.d(TAG, "Trying alternative reboot method")
            Runtime.getRuntime().exec("reboot recovery")
        } catch (e: IOException) {
            Log.e(TAG, "Runtime reboot failed: ${e.message}")
            try {
                Log.d(TAG, "Trying with su command")
                Runtime.getRuntime().exec("su -c 'reboot recovery'")
            } catch (ex: Exception) {
                Log.e(TAG, "Su reboot failed: ${ex.message}")
                Log.e(TAG, "All reboot methods failed. Manual reboot to recovery required.")
            }
        }
    }
    
    /**
     * Check if installation is possible
     * This method can be used to verify prerequisites before installation
     */
    fun canInstall(): Boolean {
        // Check if we have reboot permissions
        val hasRebootPermission = context.checkSelfPermission(android.Manifest.permission.REBOOT) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        
        Log.d(TAG, "Can install check - hasRebootPermission: $hasRebootPermission")
        
        // TODO: Add other checks for:
        // - Sufficient storage space
        // - Battery level
        // - Device compatibility
        
        return hasRebootPermission
    }
    
    /**
     * Get installation status
     * This method can be used to check current installation state
     */
    fun getInstallationStatus(): String {
        // TODO: Return current installation status
        return "ready" // For now, always return ready
    }
}
