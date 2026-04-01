package com.lich.updater.InstallsRom.RecoveryScript

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GenerateScript(private val context: Context) {
    
    companion object {
        private const val TAG = "GenerateScript"
        private const val SCRIPT_FILE = "/cache/recovery/openrecoveryscript"
        private const val NEW_LINE = "\n"
    }
    
    /**
     * Generate and write recovery script for ROM installation
     * @param zipFilePath Path to the downloaded ROM zip file
     * @return true if script was created successfully, false otherwise
     */
    suspend fun generateAndExecute(zipFilePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting recovery script generation for: $zipFilePath")
            
            // Validate the ROM file
            val updateFile = File(zipFilePath)
            if (!updateFile.exists()) {
                Log.e(TAG, "ROM file not found: $zipFilePath")
                throw Exception("ROM file not found")
            }
            
            if (!updateFile.name.lowercase().endsWith(".zip")) {
                Log.e(TAG, "Invalid file format. Must be a ZIP file: ${updateFile.name}")
                throw Exception("Invalid file format. Must be a ZIP file")
            }
            
            Log.d(TAG, "ROM file validated: ${updateFile.absolutePath}")
            
            // Build the recovery script
            val script = buildRecoveryScript(updateFile.absolutePath)
            Log.d(TAG, "Recovery script generated:\n$script")
            
            // Create recovery directory
            val createDirResult = createRecoveryDirectory()
            if (!createDirResult) {
                throw Exception("Failed to create recovery directory")
            }
            
            // Write the script to recovery cache
            val writeResult = writeRecoveryScript(script)
            if (!writeResult) {
                throw Exception("Failed to write recovery script")
            }
            
            // Verify the script was created and show directory contents
            verifyScriptCreation()
            
            Log.i(TAG, "Recovery script created successfully at: $SCRIPT_FILE")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate recovery script: ${e.message}", e)
            false
        }
    }
    
    /**
     * Build the recovery script content
     */
    private fun buildRecoveryScript(zipFilePath: String): String {
        return buildString {
            // Clear cache and dalvik for clean installation
            append("wipe cache$NEW_LINE")
            append("wipe dalvik$NEW_LINE")
            
            // Install the ROM
            append("install $zipFilePath$NEW_LINE")
            
            // Clean up downloaded files after installation
            append("cmd rm -rf /sdcard/Android/data/${context.packageName}/files/*.zip$NEW_LINE")
            
            // Optional: Reboot after installation
            append("reboot$NEW_LINE")
        }
    }
    
    private suspend fun createRecoveryDirectory(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating recovery directory: /cache/recovery/")
            
            val rootManager = com.lich.updater.root.RootManager()
            
            // Mejorar los comandos root para usar -mm si es necesario
            val createDirResult = rootManager.executeCommand("su -mm -c 'mkdir -p /cache/recovery/'")
            if (!createDirResult.success) {
                // Intentar sin -mm si falla
                val altCreateDirResult = rootManager.executeCommand("su -c 'mkdir -p /cache/recovery/'")
                if (!altCreateDirResult.success) {
                    throw Exception("Failed to create recovery directory")
                }
            }
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create recovery directory: ${e.message}", e)
            false
        }
    }
    
    private suspend fun writeRecoveryScript(script: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Writing recovery script to: $SCRIPT_FILE")
            
            val rootManager = com.lich.updater.root.RootManager()
            
            val writeResult = rootManager.executeCommand("su -mm -c 'echo \"$script\" > $SCRIPT_FILE'")
            if (!writeResult.success) {
                // Intentar sin -mm si falla
                val altWriteResult = rootManager.executeCommand("su -c 'echo \"$script\" > $SCRIPT_FILE'")
                if (!altWriteResult.success) {
                    throw Exception("Failed to write recovery script")
                }
            }
            
            // Verify the script was written using root since normal app cannot read /cache/
            val verifyResult = rootManager.executeCommand("test -s $SCRIPT_FILE")
            if (verifyResult.success) {
                Log.d(TAG, "Script verification successful")
                return@withContext true
            } else {
                Log.w(TAG, "Script verification failed - file doesn't exist or is empty")
                return@withContext false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write recovery script: ${e.message}", e)
            false
        }
    }
    
    /**
     * Check if recovery script exists
     */
    suspend fun hasRecoveryScript(): Boolean = withContext(Dispatchers.IO) {
        try {
            val rootManager = com.lich.updater.root.RootManager()
            val result = rootManager.executeCommand("test -f $SCRIPT_FILE")
            result.success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check recovery script: ${e.message}", e)
            false
        }
    }
    
    suspend fun deleteRecoveryScript(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Deleting recovery script: $SCRIPT_FILE")
            
            val rootManager = com.lich.updater.root.RootManager()
            
            val deleteResult = rootManager.executeCommand("su -mm -c 'rm -f $SCRIPT_FILE'")
            if (!deleteResult.success) {
                val altDeleteResult = rootManager.executeCommand("su -c 'rm -f $SCRIPT_FILE'")
                if (!altDeleteResult.success) {
                    return@withContext false
                }
            }
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete recovery script: ${e.message}", e)
            false
        }
    }
    
    /**
     * Verify script creation and show directory contents
     */
    private suspend fun verifyScriptCreation() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Verifying script creation...")
            
            // Check if /cache/recovery directory exists
            val recoveryDir = File("/cache/recovery")
            if (recoveryDir.exists()) {
                Log.d(TAG, "/cache/recovery directory exists")
                val files = recoveryDir.listFiles()
                if (files != null) {
                    Log.d(TAG, "Files in /cache/recovery: ${files.map { it.name }}")
                } else {
                    Log.w(TAG, "Cannot list files in /cache/recovery")
                }
            } else {
                Log.e(TAG, "/cache/recovery directory does not exist")
            }
            
            // Check if script file exists
            val scriptFile = File(SCRIPT_FILE)
            if (scriptFile.exists()) {
                Log.d(TAG, "Script file exists: ${scriptFile.absolutePath}")
                Log.d(TAG, "Script file size: ${scriptFile.length()} bytes")
                
                // Try to read and log the script content
                try {
                    val content = scriptFile.readText()
                    Log.d(TAG, "Script content:\n$content")
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot read script content: ${e.message}")
                }
            } else {
                Log.e(TAG, "Script file does not exist: $SCRIPT_FILE")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify script creation: ${e.message}", e)
        }
    }
}
