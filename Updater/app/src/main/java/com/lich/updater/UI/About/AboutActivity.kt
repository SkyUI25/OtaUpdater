package com.lich.updater.UI.About

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import android.content.pm.PackageManager
import com.lich.updater.R
import dev.oneuiproject.oneui.layout.ToolbarLayout

class AboutActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.about_main)
        
        // Setup toolbar
        setupToolbar()
        
        // Setup menu
        setupMenu()
        
        // Initialize content
        initContent()
    }
    
    private fun setupToolbar() {
        // Configure ActionBar with ToolbarLayout like SettingsActivity
        val toolbarLayout = findViewById<ToolbarLayout>(R.id.drawerLayout)
        setSupportActionBar(toolbarLayout.toolbar)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.about)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
    
    private fun setupMenu() {
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_about, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        onBackPressedDispatcher.onBackPressed()
                        true
                    }
                    R.id.menu_info -> {
                        // Open Android app settings
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                        true
                    }
                    else -> false
                }
            }
        }, this, Lifecycle.State.RESUMED)
    }
    
    private fun initContent() {
        // Set app icon
        val appIcon = getDrawable(R.mipmap.ic_launcher)
        findViewById<androidx.appcompat.widget.AppCompatImageView>(R.id.about_header_app_icon)?.setImageDrawable(appIcon)
        
        // Set app version
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            findViewById<androidx.appcompat.widget.AppCompatTextView>(R.id.about_header_app_version)?.text = 
                "Version $versionName"
        } catch (e: PackageManager.NameNotFoundException) {
            findViewById<androidx.appcompat.widget.AppCompatTextView>(R.id.about_header_app_version)?.text = 
                "Version Unknown"
        }
    }
}
