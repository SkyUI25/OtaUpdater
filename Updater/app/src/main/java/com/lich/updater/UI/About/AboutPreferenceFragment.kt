package com.lich.updater.UI.About

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import androidx.preference.PreferenceFragmentCompat
import com.lich.updater.BuildPropReader
import com.lich.updater.R
import com.lich.updater.formatVersion

class AboutPreferenceFragment : PreferenceFragmentCompat() {
    
    private lateinit var buildPropReader: BuildPropReader
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.about_preferences, rootKey)
        
        // Initialize BuildPropReader
        buildPropReader = BuildPropReader(requireContext())
        
        // Update preference summaries with current device info
        updatePreferenceSummaries()
        
        // Setup click listeners for developer preferences
        setupDeveloperClickListeners()
    }
    
    private fun updatePreferenceSummaries() {
        // Update OneUI version
        findPreference<androidx.preference.Preference>(getString(R.string.oneui_version))?.summary = 
            buildPropReader.getOneUIVersion()
        
         // Update SkyUI version
        findPreference<androidx.preference.Preference>(getString(R.string.lichui_version))?.summary = 
            formatVersion(buildPropReader.getSkyUIVersion())
    }
    
    private fun setupDeveloperClickListeners() {
        // MrDemon-LICH
        findPreference<androidx.preference.Preference>(getString(R.string.demon))?.setOnPreferenceClickListener {
            openUrl("https://github.com/MrDemonc")
            true
        }
        
        // Yanndroid
        findPreference<androidx.preference.Preference>(getString(R.string.yanndroid))?.setOnPreferenceClickListener {
            openUrl("https://github.com/Yanndroid")
            true
        }
        
        // Salvo Giangreco
        findPreference<androidx.preference.Preference>(getString(R.string.salvo))?.setOnPreferenceClickListener {
            openUrl("https://github.com/salvogiangri")
            true
        }
        
        // Tribalfs
        findPreference<androidx.preference.Preference>(getString(R.string.tribalfs))?.setOnPreferenceClickListener {
            openUrl("https://github.com/tribalfs")
            true
        }
    }
    
    private fun openUrl(urlString: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlString))
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("openUrl", e.message.toString())
        }
    }
}
