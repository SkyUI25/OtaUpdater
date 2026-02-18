package com.lich.updater.UI.Settings

import android.content.Intent
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.SeslSwitchPreferenceScreen

import com.lich.updater.BuildPropReader
import com.lich.updater.R
import com.lich.updater.UpdatePrefs
import com.lich.updater.UI.About.AboutActivity

class SettingsPreferenceFragment : PreferenceFragmentCompat() {
    
    private lateinit var buildPropReader: BuildPropReader
    private lateinit var updatePrefs: UpdatePrefs
    
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
    }
    
    private fun updatePreferenceSummaries() {
        // Update OneUI version
        findPreference<androidx.preference.Preference>(getString(R.string.oneui_version))?.summary = 
            buildPropReader.getOneUIVersion()
        
        // Update LichUI version
        findPreference<androidx.preference.Preference>(getString(R.string.lichui_version))?.summary = 
            buildPropReader.getLichUIVersion()
        
        // Update build date
        findPreference<androidx.preference.Preference>(getString(R.string.build_date))?.summary = 
            buildPropReader.getBuildDate()
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
}
