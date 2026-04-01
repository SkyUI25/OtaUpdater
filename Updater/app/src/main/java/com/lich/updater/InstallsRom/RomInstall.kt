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
    private fun rebootToRecovery() {
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
            Log.d(TAG, "Trying alternative reboot method via RootManager")
            
            val rootManager = com.lich.updater.root.RootManager()
            
            CoroutineScope(Dispatchers.IO).launch {
                val rebootResult = rootManager.executeCommand("su -mm -c 'svc power reboot recovery'")
                if (!rebootResult.success) {
                    val altRebootResult = rootManager.executeCommand("su -c 'svc power reboot recovery'")
                    if (!altRebootResult.success) {
                        Log.e(TAG, "All reboot methods failed. Manual reboot to recovery required.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Su reboot failed: ${e.message}")
        }
    }
    
    /**
     * Check if installation is possible
     * This method can be used to verify prerequisites before installation
     */
    fun canInstall(): Boolean {
        // Since we are enforcing root access globally in MainActivity,
        // we already have the necessary privileges via libsu to reboot.
        Log.d(TAG, "Can install check - relying on root access")
        
        // TODO: Add other checks for:
        // - Sufficient storage space
        // - Battery level
        // - Device compatibility
        
        return true
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
