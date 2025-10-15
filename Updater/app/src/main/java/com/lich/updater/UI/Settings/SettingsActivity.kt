package com.lich.updater.UI.Settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lich.updater.R
import dev.oneuiproject.oneui.layout.ToolbarLayout

class SettingsActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_main)
        
        // Setup toolbar
        setupToolbar()
    }
    
    private fun setupToolbar() {
        // Configure ActionBar with ToolbarLayout like MainActivity
        val toolbarLayout = findViewById<ToolbarLayout>(R.id.drawerLayout)
        setSupportActionBar(toolbarLayout.toolbar)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
