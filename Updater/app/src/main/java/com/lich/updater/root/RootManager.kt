package com.lich.updater.root

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class RootManager {
    companion object {
        private var isShellInitialized = false
        
        init {
            try {
                if (!isShellInitialized) {
                    // Configuración de libsu
                    Shell.enableVerboseLogging = true
                    Shell.setDefaultBuilder(
                        Shell.Builder.create()
                            .setFlags(Shell.FLAG_MOUNT_MASTER)
                            .setTimeout(10)
                    )
                    isShellInitialized = true
                }
            } catch (e: Exception) {
                // Si ya está inicializado, ignorar el error
            }
        }
    }

    suspend fun hasRootAccess(): Boolean = withContext(Dispatchers.IO) {
        Shell.getShell().isRoot
    }

    suspend fun checkRootMethod(): RootMethod = withContext(Dispatchers.IO) {
        when {
            checkKernelSU() -> RootMethod.KERNELSU
            checkMagisk() -> RootMethod.MAGISK
            else -> RootMethod.NONE
        }
    }

    private suspend fun checkMagisk(): Boolean = withContext(Dispatchers.IO) {
        try {
            ShellUtils.fastCmd("magisk -v").isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun checkKernelSU(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Verificar múltiples indicadores de KernelSU
            val ksuBinary = File("/system/bin/ksu").exists() || 
                           File("/system/xbin/ksu").exists()
            
            val ksuModule = File("/sys/module/kernelsu").exists()
            
            // Intentar ejecutar ksu
            val ksuVersion = try {
                Shell.cmd("ksu -V").exec().out.isNotEmpty()
            } catch (e: Exception) {
                false
            }
            
            // Verificar si podemos ejecutar comandos como root
            val testRoot = try {
                Shell.cmd("su -c id").exec().out.any { it.contains("uid=0") }
            } catch (e: Exception) {
                false
            }

            return@withContext (ksuBinary || ksuModule || ksuVersion || testRoot)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        val result = try {
            // Intentar primero con el comando original
            val originalResult = Shell.cmd(command).exec()
            if (!originalResult.isSuccess) {
                // Si falla, intentar con -mm
                if (command.startsWith("su -c")) {
                    val mmCommand = command.replace("su -c", "su -mm -c")
                    Shell.cmd(mmCommand).exec()
                } else {
                    originalResult
                }
            } else {
                originalResult
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Shell.cmd(command).exec()
        }

        CommandResult(
            success = result.isSuccess,
            output = result.out,
            error = result.err
        )
    }

    suspend fun checkKernelSUPermission(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Intentar ejecutar un comando simple como root
            val result = Shell.cmd("su -c whoami").exec()
            result.isSuccess && result.out.any { it.trim() == "root" }
        } catch (e: Exception) {
            false
        }
    }
}

enum class RootMethod {
    MAGISK,
    KERNELSU,
    NONE
}

data class CommandResult(
    val success: Boolean,
    val output: List<String>,
    val error: List<String>
)
