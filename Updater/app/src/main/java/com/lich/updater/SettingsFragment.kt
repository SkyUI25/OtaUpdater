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
import com.lich.updater.ui.preference.StatusCardPreference
import dev.oneuiproject.oneui.layout.ToolbarLayout
import com.lich.updater.UpdatePrefs

class SettingsFragment : PreferenceFragmentCompat() {
    private var toolbarLayout: ToolbarLayout? = null
    private lateinit var buildPropReader: BuildPropReader
    private lateinit var updatePrefs: UpdatePrefs
    private var isShowingNewVersion = false
    private var lastStatusText: String? = null
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Start with current version preferences
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)
        activity?.title = getString(R.string.updater_title)
        
        // Initialize BuildPropReader and UpdatePrefs
        buildPropReader = BuildPropReader(requireContext())
        updatePrefs = UpdatePrefs(requireContext())
        
        // Update preference summaries
        updatePreferenceSummaries()
        
        // Restore last status if available
        lastStatusText?.let { updateStatusText(it) }
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
            // 1. Move Rom version to the main status card subtitle
            updateStatusText(getString(R.string.new_update_available), subtitle = "Rom version ${manifestData.version}")
            
            // 2. Trigger changelog refresh (it will now include metadata automatically)
            val currentChangelog = updatePrefs.getSavedChangelog() ?: ""
            updateChangelogPreference(currentChangelog)
        }
    }
    
    fun hidePreferences() {
        view?.visibility = View.GONE
    }
    
    fun showPreferences() {
        view?.visibility = View.VISIBLE
    }
    
    fun updateChangelogPreference(changelogText: String) {
        if (changelogText.isEmpty()) {
            findPreference<androidx.preference.Preference>("changelog_content")?.summary = ""
            return
        }

        val manifest = updatePrefs.getSavedManifestData()
        val formattedContent = if (isShowingNewVersion && manifest != null) {
            "• Build date: ${manifest.buildDate}\n• Size: ${manifest.size}\n$changelogText"
        } else {
            changelogText
        }

        findPreference<androidx.preference.Preference>("changelog_content")?.summary = formattedContent
    }
    
    fun updateStatusText(text: String, blinking: Boolean = false, subtitle: String = "") {
        lastStatusText = text
        val statusPref = findPreference<StatusCardPreference>("pref_update_status")
        statusPref?.setStatus(text, blinking, subtitle)
    }
}


