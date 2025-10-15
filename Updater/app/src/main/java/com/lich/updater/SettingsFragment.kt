package com.lich.updater

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceFragmentCompat
import dev.oneuiproject.oneui.layout.ToolbarLayout

class SettingsFragment : PreferenceFragmentCompat() {
    private var toolbarLayout: ToolbarLayout? = null
    private lateinit var buildPropReader: BuildPropReader
    private var isShowingNewVersion = false
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Start with current version preferences
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)
        activity?.title = getString(R.string.updater_title)
        
        // Initialize BuildPropReader
        buildPropReader = BuildPropReader(requireContext())
        
        // Update preference summaries
        updatePreferenceSummaries()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarLayout = requireActivity().findViewById(R.id.drawerLayout)
        
        // Hide preferences initially until verification is complete
        view.visibility = View.GONE
        
        // Set up navigation icon click listener using ActionBar
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Set custom navigation icon with proper tinting
        val backIcon = androidx.core.content.ContextCompat.getDrawable(requireContext(), dev.oneuiproject.oneui.R.drawable.ic_oui_back)
        backIcon?.let { icon ->
            // Apply the same tinting as menu icons
            val typedArray = requireContext().obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.actionMenuTextColor))
            val tintColor = typedArray.getColor(0, 0)
            typedArray.recycle()
            icon.setTint(tintColor)
            (requireActivity() as AppCompatActivity).supportActionBar?.setHomeAsUpIndicator(icon)
        }
        
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_main, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                        true
                    }
                    R.id.menu_search -> {
                        // Trigger manifest check with progress bar
                        (requireActivity() as MainActivity).checkForUpdatesWithProgress()
                        true
                    }
                    R.id.menu_settings -> {
                        val intent = android.content.Intent(requireContext(), com.lich.updater.UI.Settings.SettingsActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }
    
    private fun updatePreferenceSummaries() {
        if (isShowingNewVersion) {
            updateNewVersionSummaries()
        } else {
            updateCurrentVersionSummaries()
        }
    }
    
    private fun updateCurrentVersionSummaries() {
        val kv = findPreference<com.lich.updater.ui.preference.CurrentVersionKeyValuePreference>("current_version_info")
        kv?.oneUiValue = buildPropReader.getOneUIVersion()
        kv?.lichUiValue = buildPropReader.getLichUIVersion()
        kv?.buildDateValue = buildPropReader.getBuildDate()
    }
    
    private fun updateNewVersionSummaries() {
        val kv = findPreference<com.lich.updater.ui.preference.NewVersionKeyValuePreference>("new_version_info")
        kv?.buildDateValue = getString(R.string.unknown)
        kv?.sizeValue = getString(R.string.unknown)
        kv?.versionValue = getString(R.string.unknown)
    }
    
    fun switchToNewVersionPreferences(manifestData: ManifestData?) {
        if (!isShowingNewVersion) {
            setPreferencesFromResource(R.xml.new_update_preferences, null)
            isShowingNewVersion = true
            activity?.title = getString(R.string.new_version)
            
            if (manifestData != null) {
                updateNewVersionSummariesWithManifest(manifestData)
            } else {
                updateNewVersionSummaries()
            }
        }
    }
    
    fun switchToCurrentVersionPreferences() {
        if (isShowingNewVersion) {
            setPreferencesFromResource(R.xml.settings_preferences, null)
            isShowingNewVersion = false
            activity?.title = getString(R.string.updater_title)
            updateCurrentVersionSummaries()
        }
    }
    
    fun updateNewVersionSummariesWithManifest(manifestData: ManifestData) {
        if (isShowingNewVersion) {
            val kv = findPreference<com.lich.updater.ui.preference.NewVersionKeyValuePreference>("new_version_info")
            kv?.buildDateValue = manifestData.buildDate
            kv?.sizeValue = manifestData.size
            kv?.versionValue = manifestData.version
        }
    }
    
    fun hidePreferences() {
        view?.visibility = View.GONE
    }
    
    fun showPreferences() {
        view?.visibility = View.VISIBLE
    }
    
    fun updateChangelogPreference(changelogText: String) {
        android.util.Log.d("SettingsFragment", "Updating changelog preference with: $changelogText")
        val changelogPreference = findPreference<androidx.preference.Preference>("changelog_content")
        android.util.Log.d("SettingsFragment", "Changelog preference found: $changelogPreference")
        
        if (changelogPreference != null) {
            changelogPreference.summary = changelogText
            android.util.Log.d("SettingsFragment", "Changelog preference updated successfully")
        } else {
            android.util.Log.e("SettingsFragment", "Changelog preference not found!")
        }
    }
}


