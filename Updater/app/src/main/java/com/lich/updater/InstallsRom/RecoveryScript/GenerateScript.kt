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
    
    /**
     * Create the recovery directory if it doesn't exist
     */
    private suspend fun createRecoveryDirectory(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating recovery directory: /cache/recovery/")
            
            // Try using Java File API first since app is in system
            try {
                val recoveryDir = File("/cache/recovery")
                if (!recoveryDir.exists()) {
                    val created = recoveryDir.mkdirs()
                    if (created) {
                        Log.d(TAG, "Directory created successfully using Java File API")
                        return@withContext true
                    } else {
                        Log.w(TAG, "Java File API failed to create directory")
                    }
                } else {
                    Log.d(TAG, "Directory already exists")
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Java File API failed: ${e.message}")
            }
            
            // Fallback to shell commands
            val commands = arrayOf(
                "mkdir -p /cache/recovery/",
                "su -c 'mkdir -p /cache/recovery/'",
                "su -mm -c 'mkdir -p /cache/recovery/'"
            )
            
            for (command in commands) {
                try {
                    Log.d(TAG, "Trying command: $command")
                    val process = Runtime.getRuntime().exec(command)
                    val exitCode = process.waitFor()
                    if (exitCode == 0) {
                        Log.d(TAG, "Directory created successfully with command: $command")
                        return@withContext true
                    } else {
                        Log.w(TAG, "Command failed with exit code $exitCode: $command")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Command failed: $command - ${e.message}")
                }
            }
            
            Log.e(TAG, "All directory creation methods failed")
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create recovery directory: ${e.message}", e)
            false
        }
    }
    
    /**
     * Write the recovery script to the cache
     */
    private suspend fun writeRecoveryScript(script: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Writing recovery script to: $SCRIPT_FILE")
            
            // Try using Java File API first since app is in system
            try {
                val scriptFile = File(SCRIPT_FILE)
                scriptFile.writeText(script)
                
                if (scriptFile.exists() && scriptFile.length() > 0) {
                    Log.d(TAG, "Script written successfully using Java File API: ${scriptFile.length()} bytes")
                    return@withContext true
                } else {
                    Log.w(TAG, "Java File API failed - file doesn't exist or is empty")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Java File API failed: ${e.message}")
            }
            
            // Fallback to shell commands
            val commands = arrayOf(
                "echo \"$script\" > $SCRIPT_FILE",
                "su -c 'echo \"$script\" > $SCRIPT_FILE'",
                "su -mm -c 'echo \"$script\" > $SCRIPT_FILE'"
            )
            
            for (command in commands) {
                try {
                    Log.d(TAG, "Trying command: $command")
                    val process = Runtime.getRuntime().exec(command)
                    val exitCode = process.waitFor()
                    if (exitCode == 0) {
                        Log.d(TAG, "Script written successfully with command: $command")
                        
                        // Verify the script was written
                        val scriptFile = File(SCRIPT_FILE)
                        if (scriptFile.exists() && scriptFile.length() > 0) {
                            Log.d(TAG, "Script verification successful: ${scriptFile.length()} bytes")
                            return@withContext true
                        } else {
                            Log.w(TAG, "Script verification failed - file doesn't exist or is empty")
                        }
                    } else {
                        Log.w(TAG, "Command failed with exit code $exitCode: $command")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Command failed: $command - ${e.message}")
                }
            }
            
            Log.e(TAG, "All script writing methods failed")
            false
            
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
            val scriptFile = File(SCRIPT_FILE)
            scriptFile.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check recovery script: ${e.message}", e)
            false
        }
    }
    
    /**
     * Delete the recovery script
     */
    suspend fun deleteRecoveryScript(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Deleting recovery script: $SCRIPT_FILE")
            
            val commands = arrayOf(
                "rm -f $SCRIPT_FILE",
                "su -c 'rm -f $SCRIPT_FILE'",
                "su -mm -c 'rm -f $SCRIPT_FILE'"
            )
            
            for (command in commands) {
                try {
                    val process = Runtime.getRuntime().exec(command)
                    val exitCode = process.waitFor()
                    if (exitCode == 0) {
                        Log.d(TAG, "Script deleted successfully with command: $command")
                        return@withContext true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Command failed: $command - ${e.message}")
                }
            }
            
            false
            
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
