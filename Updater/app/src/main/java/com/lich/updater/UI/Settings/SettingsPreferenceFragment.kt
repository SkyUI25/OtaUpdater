package com.lich.updater.UI.Settings

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.SeslSwitchPreferenceScreen

import com.lich.updater.BuildPropReader
import com.lich.updater.R
import com.lich.updater.UpdatePrefs
import com.lich.updater.UI.About.AboutActivity
import com.lich.updater.InstallsRom.RecoveryScript.GenerateScript
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import com.lich.updater.AppUpdateChecker
import com.lich.updater.AppUpdateDownloader

class SettingsPreferenceFragment : PreferenceFragmentCompat() {
    
    private lateinit var buildPropReader: BuildPropReader
    private lateinit var updatePrefs: UpdatePrefs
    private val devKey = "platform"
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.config_preferences, rootKey)
        
        // Initialize components
        buildPropReader = BuildPropReader(requireContext())
        updatePrefs = UpdatePrefs(requireContext())
        
        // Update preference summaries with current device info
        updatePreferenceSummaries()
        
        // Setup about preference
        setupAboutPreference()
        
        // Setup switch preferences
        setupSwitchPreferences()
        
        // Setup App Updates preference
        setupAppUpdatesPreference()
        
        // Setup Developer Options
        setupDeveloperOptions()
        
        // Check if opened from notification
        checkIntentFromNotification()
    }
    
    override fun onResume() {
        super.onResume()
        updateDeveloperOptionsVisibility()
    }

    private fun updatePreferenceSummaries() {
        // Update OneUI version
        findPreference<androidx.preference.Preference>(getString(R.string.oneui_version))?.summary = 
            buildPropReader.getOneUIVersion()
        
        // Update SkyUI version
        findPreference<androidx.preference.Preference>(getString(R.string.skyui_version))?.summary = 
            buildPropReader.getSkyUIVersion()
    }
    
    private fun setupAboutPreference() {
        // About preference
        val aboutPreference = findPreference<androidx.preference.Preference>(getString(R.string.about))
        aboutPreference?.setOnPreferenceClickListener {
            val intent = Intent(requireContext(), AboutActivity::class.java)
            startActivity(intent)
            true
        }
    }
    
    private fun setupAppUpdatesPreference() {
        val appUpdatesPref = findPreference<androidx.preference.Preference>("pref_check_app_updates")
        appUpdatesPref?.setOnPreferenceClickListener {
            checkAppUpdateManually()
            true
        }
    }
    
    private fun checkAppUpdateManually() {
        Toast.makeText(requireContext(), getString(R.string.checking_for_updates), Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.Main).launch {
            val updateInfo = AppUpdateChecker(requireContext()).checkForAppUpdate()
            if (updateInfo != null && updateInfo.isUpdateAvailable) {
                showAppUpdateDialog(updateInfo.tagName, updateInfo)
            } else {
                Toast.makeText(requireContext(), getString(R.string.app_up_to_date), Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun checkIntentFromNotification() {
        val activity = requireActivity() as? SettingsActivity ?: return
        val (showAppUpdate, version) = activity.getAppUpdateIntentInfo()
        if (showAppUpdate && version != null) {
            // Provide a dummy AppReleaseInfo since we know an update exists but we don't have the full object from the intent.
            // A quick re-fetch is safer.
            checkAppUpdateManually()
        }
    }
    
    private fun showAppUpdateDialog(version: String, updateInfo: com.lich.updater.AppReleaseInfo) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.app_update_available_title)
            .setMessage(getString(R.string.app_update_message, version))
            .setPositiveButton(R.string.download) { dialog, _ ->
                dialog.dismiss()
                downloadAndInstallAppUpdate(updateInfo)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun downloadAndInstallAppUpdate(updateInfo: com.lich.updater.AppReleaseInfo) {
        Toast.makeText(requireContext(), getString(R.string.app_update_downloading), Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.Main).launch {
            val success = AppUpdateDownloader(requireContext()).downloadAndInstall(updateInfo)
            if (!success) {
                Toast.makeText(requireContext(), getString(R.string.app_update_download_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupSwitchPreferences() {
        // Mobile data switch
        val mobileDataSwitch = findPreference<SwitchPreferenceCompat>(getString(R.string.data_network))
        mobileDataSwitch?.apply {
            isChecked = updatePrefs.isUseMobileDataEnabled()
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                updatePrefs.setUseMobileData(enabled)
                true
            }
        }
        
        // Update notifications switch
        val notificationsSwitch = findPreference<androidx.preference.SeslSwitchPreferenceScreen>(getString(R.string.notifications))
        notificationsSwitch?.apply {
            isChecked = updatePrefs.isUpdateNotificationsEnabled()
            
            // Notification Summary update
            summary = getFrequencySummary()
            
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                updatePrefs.setUpdateNotificationsEnabled(enabled)
                
                // Start or stop background update checker service
                if (enabled) {
                    com.lich.updater.UpdatesCheckReceiver.schedule(requireContext())
                } else {
                    com.lich.updater.UpdatesCheckReceiver.cancel(requireContext())
                }
                
                true
            }
            
            // Show frequency dialog when clicking the preference (not the switch)
            setOnPreferenceClickListener {
                showFrequencyDialog()
                true
            }
        }
    }

    private fun getFrequencySummary(): String {
        return when (updatePrefs.getCheckInterval()) {
            1 -> getString(R.string.interval_1h)
            24 -> getString(R.string.interval_1d)
            168 -> getString(R.string.interval_1w)
            else -> getString(R.string.notifications_summary)
        }
    }

    private fun showFrequencyDialog() {
        val options = arrayOf(
            getString(R.string.interval_1h),
            getString(R.string.interval_1d),
            getString(R.string.interval_1w)
        )
        val values = intArrayOf(1, 24, 168)
        
        val currentInterval = updatePrefs.getCheckInterval()
        var selectedIndex = values.indexOf(currentInterval)
        if (selectedIndex == -1) selectedIndex = 1 // Default to Daily if not found
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.notification_frequency_title)
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                val newInterval = values[which]
                updatePrefs.setCheckInterval(newInterval)
                
                // Update summary
                val notificationsSwitch = findPreference<androidx.preference.SeslSwitchPreferenceScreen>(getString(R.string.notifications))
                notificationsSwitch?.summary = options[which]
                
                // Reschedule if enabled
                if (updatePrefs.isUpdateNotificationsEnabled()) {
                    com.lich.updater.UpdatesCheckReceiver.schedule(requireContext())
                }
                
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupDeveloperOptions() {
        val devCategory = findPreference<PreferenceCategory>("category_developer_options")
        val devSwitch = findPreference<SwitchPreferenceCompat>("pref_developer_mode")
        val testRecovery = findPreference<Preference>("pref_test_recovery")

        devSwitch?.apply {
            isChecked = updatePrefs.isDeveloperModeEnabled()
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                if (!enabled) {
                    updatePrefs.setDeveloperModeEnabled(false)
                    updateDeveloperOptionsVisibility()
                }
                true
            }
        }

        testRecovery?.setOnPreferenceClickListener {
            testRecoveryScript()
            true
        }
    }

    private fun updateDeveloperOptionsVisibility() {
        val devCategory = findPreference<PreferenceCategory>("category_developer_options")
        val devSwitch = findPreference<SwitchPreferenceCompat>("pref_developer_mode")
        val testRecovery = findPreference<Preference>("pref_test_recovery")

        val enabled = updatePrefs.isDeveloperModeEnabled()
        devCategory?.isVisible = enabled
        devSwitch?.isChecked = enabled
    }

    private fun testRecoveryScript() {
        Toast.makeText(requireContext(), getString(R.string.test_script_generating), Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val generateScript = GenerateScript(requireContext())
                val success = generateScript.generateTestScript()
                if (success) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), getString(R.string.test_script_rebooting), Toast.LENGTH_LONG).show()
                    }
                    val romInstall = com.lich.updater.InstallsRom.RomInstall(requireContext())
                    romInstall.rebootToRecovery()
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to generate test script", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
