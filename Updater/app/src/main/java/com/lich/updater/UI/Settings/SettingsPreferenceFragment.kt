package com.lich.updater.UI.Settings

import android.content.Intent
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.lich.updater.BackgroundUpdateChecker
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
        val mobileDataSwitch = findPreference<SwitchPreference>(getString(R.string.data_network))
        mobileDataSwitch?.apply {
            isChecked = updatePrefs.isUseMobileDataEnabled()
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                updatePrefs.setUseMobileData(enabled)
                true
            }
        }
        
        // Update notifications switch
        val notificationsSwitch = findPreference<SwitchPreference>(getString(R.string.notifications))
        notificationsSwitch?.apply {
            isChecked = updatePrefs.isUpdateNotificationsEnabled()
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                updatePrefs.setUpdateNotificationsEnabled(enabled)
                
                // Start or stop background update checker service
                val serviceIntent = Intent(requireContext(), BackgroundUpdateChecker::class.java)
                if (enabled) {
                    requireContext().startService(serviceIntent)
                } else {
                    requireContext().stopService(serviceIntent)
                }
                
                true
            }
        }
    }
}
