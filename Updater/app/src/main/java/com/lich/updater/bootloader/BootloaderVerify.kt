package com.lich.updater.bootloader

import android.content.Context
import android.util.Log
import com.lich.updater.BuildPropReader
import com.lich.updater.ManifestData

class BootloaderVerify(private val context: Context) {
    
    companion object {
        private const val TAG = "BootloaderVerify"
        private const val VENDOR_BUILD_VERSION_PROP = "ro.vendor.build.version.incremental"
        private const val BOOTLOADER_SUFFIX_LENGTH = 8
    }
    
    private val buildPropReader = BuildPropReader(context)
    
    /**
     * Verifica si el bootloader del dispositivo es compatible con el manifest
     * @param manifestData Los datos del manifest que contienen la información del bootloader
     * @return BootloaderVerificationResult con el resultado de la verificación
     */
    fun verifyBootloaderCompatibility(manifestData: ManifestData): BootloaderVerificationResult {
        return try {
            val deviceBootloader = buildPropReader.getVendorBuildVersion()
            
            Log.d(TAG, "Device bootloader: $deviceBootloader")
            Log.d(TAG, "Manifest bootloader: ${manifestData.bl}")
            
            if (deviceBootloader == context.getString(com.lich.updater.R.string.unknown)) {
                Log.w(TAG, "Could not read device bootloader version")
                return BootloaderVerificationResult.UNKNOWN
            }
            
            if (manifestData.bl.isBlank()) {
                Log.w(TAG, "Manifest bootloader info is empty")
                return BootloaderVerificationResult.UNKNOWN
            }
            
            val deviceBootloaderSuffix = extractBootloaderSuffix(deviceBootloader)
            val manifestBootloaderSuffix = manifestData.bl
            
            Log.d(TAG, "Device bootloader suffix: $deviceBootloaderSuffix")
            Log.d(TAG, "Manifest bootloader suffix: $manifestBootloaderSuffix")
            
            if (deviceBootloaderSuffix == manifestBootloaderSuffix) {
                Log.d(TAG, "Bootloader compatible - proceeding with download")
                BootloaderVerificationResult.COMPATIBLE
            } else {
                Log.w(TAG, "Bootloader incompatible - update required")
                BootloaderVerificationResult.INCOMPATIBLE
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying bootloader compatibility", e)
            BootloaderVerificationResult.UNKNOWN
        }
    }
    
    /**
     * Extrae los últimos 8 caracteres del bootloader del dispositivo
     * @param bootloaderVersion La versión completa del bootloader (ej: A546EXXSFDYH1)
     * @return Los últimos 8 caracteres (ej: XXSFDYH1)
     */
    private fun extractBootloaderSuffix(bootloaderVersion: String): String {
        return if (bootloaderVersion.length >= BOOTLOADER_SUFFIX_LENGTH) {
            bootloaderVersion.takeLast(BOOTLOADER_SUFFIX_LENGTH)
        } else {
            bootloaderVersion // Si es más corto, usar todo el string
        }
    }
    
    /**
     * Obtiene la versión completa del bootloader del dispositivo
     * @return La versión completa del bootloader o "Unknown" si no se puede leer
     */
    fun getDeviceBootloaderVersion(): String {
        return buildPropReader.getVendorBuildVersion()
    }
    
    /**
     * Verifica si se puede leer la información del bootloader del dispositivo
     * @return true si se puede leer, false en caso contrario
     */
    fun canReadBootloaderInfo(): Boolean {
        return buildPropReader.isPropertyAvailable(VENDOR_BUILD_VERSION_PROP)
    }
}

/**
 * Resultado de la verificación del bootloader
 */
enum class BootloaderVerificationResult {
    COMPATIBLE,      // El bootloader es compatible, se puede proceder con la descarga
    INCOMPATIBLE,    // El bootloader no es compatible, se requiere actualización
    UNKNOWN          // No se pudo determinar el estado del bootloader
}
