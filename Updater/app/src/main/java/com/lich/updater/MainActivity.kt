package com.lich.updater

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import android.view.View
import android.view.ViewGroup
import com.airbnb.lottie.LottieAnimationView
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.layout.DrawerLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.appcompat.widget.SeslProgressBar
import android.widget.Button
import android.content.Intent
import com.lich.updater.GetManifest
import com.lich.updater.ChangelogReader
import com.lich.updater.InstallsRom.RomInstall
import com.lich.updater.Notifications.InstallNotification
import androidx.preference.Preference
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.PermissionChecker

class MainActivity : AppCompatActivity() {
    private lateinit var getManifest: GetManifest
    private lateinit var changelogReader: ChangelogReader
    private lateinit var updatePrefs: UpdatePrefs
    private lateinit var romInstall: RomInstall
    private lateinit var installNotification: InstallNotification
    private var initialStateApplied = false
    private var allowFetchChangelog = true
    private var dotsJob: kotlinx.coroutines.Job? = null
    private var currentDotsBaseText: String? = null
    private var revealAnimator: android.animation.ValueAnimator? = null

    private val requestNotifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        updatePrefs.setNotificationPermissionAsked(granted)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        // Configure ActionBar with ToolbarLayout
        val toolbarLayout = findViewById<ToolbarLayout>(R.id.drawerLayout)
        setSupportActionBar(toolbarLayout.toolbar)
        
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.nav_host_main, SettingsFragment())
            }
        }
        
        // Initialize GetManifest and ChangelogReader
        getManifest = GetManifest(this)
        changelogReader = ChangelogReader(this)
        updatePrefs = UpdatePrefs(this)
        romInstall = RomInstall(this)
        installNotification = InstallNotification(this)
        
        // Ask notification permission on first open (Android 13+)
        maybeRequestNotificationPermission()
        
        // Setup download button
        setupDownloadButton()
        
        // Start background update checker if notifications are enabled
        startBackgroundUpdateChecker()
        
        // Defer applying persisted state until fragment is attached (handled in onResume)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!updatePrefs.isNotificationPermissionAsked()) {
                val hasPerm = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) {
                    updatePrefs.setNotificationPermissionAsked(true)
                } else {
                    requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!initialStateApplied) {
            applyPersistedUpdateStateOrFetch()
            initialStateApplied = true
        }

        // Always reconcile UI when returning to the foreground
        // Check MD5 state first, then install state, then download state
        if (!reconcileMd5UiFromPrefs()) {
            if (!reconcileInstallUiFromPrefs()) {
                reconcileDownloadUiFromPrefs()
            }
        }
        
        // Additional check: if install state exists but file is missing, clear it
        val installStatus = updatePrefs.getInstallStatus()
        if (installStatus == UpdatePrefs.INSTALL_STATUS_READY || installStatus == UpdatePrefs.INSTALL_STATUS_INSTALLING) {
            val filePath = updatePrefs.getInstallFilePath()
            val installFile = if (filePath != null) java.io.File(filePath) else null
            if (installFile?.exists() != true) {
                android.util.Log.d("MainActivity", "Install state exists but file missing, clearing install state")
                updatePrefs.clearInstallState()
                // Reset button to download state
                setDownloadButtonCancel(false)
                setDownloadButtonEnabled(true)
            }
        }
        
        // Hide install notification when app comes to foreground
        installNotification.hide()

        // Register for download progress broadcasts
        val filter = android.content.IntentFilter().apply {
            addAction(DownloadForegroundService.ACTION_PROGRESS)
            addAction(DownloadForegroundService.ACTION_COMPLETE)
            addAction(DownloadForegroundService.ACTION_FAILED)
            addAction(DownloadForegroundService.ACTION_CANCELED)
            addAction(DownloadForegroundService.ACTION_MD5_PROGRESS)
            addAction(DownloadForegroundService.ACTION_MD5_SUCCESS)
            addAction(DownloadForegroundService.ACTION_MD5_FAILED)
        }
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).registerReceiver(downloadReceiver, filter)
    }

    private fun applyPersistedUpdateStateOrFetch() {
        // If there is no internet, show NO_INTERNET UI regardless of saved state
        if (!isInternetAvailable()) {
            updateUIForComparison(DateComparisonResult.NO_INTERNET)
            return
        }
        // Check persisted state first: if an update was detected before, validate against current build
        if (updatePrefs.isUpdateAvailable()) {
            val saved = updatePrefs.getSavedManifestData()
            val buildPropReader = BuildPropReader(this)
            val currentBuildDate = buildPropReader.getBuildDate()

            if (saved != null && currentBuildDate != getString(R.string.unknown)) {
                val comparison = getManifest.compareDates(currentBuildDate, saved.buildDate)
                if (comparison == DateComparisonResult.UP_TO_DATE) {
                    // Device is now on saved (or newer) build → clear persisted flag and fetch fresh
                    updatePrefs.clearUpdate()
                    allowFetchChangelog = true
                    checkForUpdates()
                } else {
                    // Still older than saved manifest → keep showing update available without fetching
                    allowFetchChangelog = false
                    updateUIForComparison(DateComparisonResult.UPDATE_AVAILABLE, saved)
                }
            } else {
                // Missing data; fallback to showing saved state if present
                allowFetchChangelog = false
                updateUIForComparison(DateComparisonResult.UPDATE_AVAILABLE, saved)
            }
        } else {
            // Preferences are hidden by default in SettingsFragment.onViewCreated()
            allowFetchChangelog = true
            checkForUpdates()
        }
    }

    private fun reconcileDownloadUiFromPrefs() {
        if (updatePrefs.isDownloadActive()) {
            showDownloadProgress(true)
            val percent = updatePrefs.getDownloadPercent()
            val eta = updatePrefs.getDownloadEta()
            val progressBar = findViewById<SeslProgressBar>(R.id.progress_bar_download)
            val timeRemainingText = findViewById<TextView>(R.id.text_time_remaining)
            val percentText = findViewById<TextView>(R.id.text_download_percent)
            progressBar?.progress = percent
            timeRemainingText?.text = eta
            percentText?.text = "$percent%"
            setDownloadButtonCancel(true)
            setDownloadButtonEnabled(true)
            
            // Set and play GIF during active download
            val lottie = findViewById<LottieAnimationView>(R.id.lottie_rom_update)
            lottie.setAnimation(R.raw.new_update)
            lottie.playAnimation()
            
            // Consolidated dots animation to main status text
            startDotsAnimation(getString(R.string.downloading))
        } else {
            showDownloadProgress(false)
            setDownloadButtonCancel(false)
            setDownloadButtonEnabled(true)
            stopDotsAnimation()
        }
    }

    private fun reconcileMd5UiFromPrefs(): Boolean {
        val status = updatePrefs.getMd5Status()
        val isActive = updatePrefs.isMd5Active()
        val percent = updatePrefs.getMd5Percent()
        android.util.Log.d("MainActivity", "reconcileMd5UiFromPrefs: status=$status, isActive=$isActive, percent=$percent")
        
        when (status) {
            UpdatePrefs.MD5_STATUS_ACTIVE -> {
                android.util.Log.d("MainActivity", "Restoring MD5 active state")
                showDownloadProgress(true)
                findViewById<TextView>(R.id.text_time_remaining)?.text = getString(R.string.verifying_md5)
                findViewById<TextView>(R.id.text_download_percent)?.text = "$percent%"
                findViewById<SeslProgressBar>(R.id.progress_bar_download)?.progress = percent
                setDownloadButtonEnabled(false)
                
                // Set and play GIF during active MD5 verification
                val lottie = findViewById<LottieAnimationView>(R.id.lottie_rom_update)
                lottie.setAnimation(R.raw.new_update)
                lottie.playAnimation()
                
                startDotsAnimation(getString(R.string.verifying_md5))
                showDownloadButton(true)
                switchToNewVersionPreferences(updatePrefs.getSavedManifestData())
                return true // MD5 state handled
            }
            UpdatePrefs.MD5_STATUS_SUCCESS -> {
                android.util.Log.d("MainActivity", "Restoring MD5 success state - showing Install button")
                revealAnimator?.cancel()
                showDownloadProgress(false) // Hide progress bar
                setInstallButton() // Show Install button
                showDownloadButton(true)
                switchToNewVersionPreferences(updatePrefs.getSavedManifestData())
                
                // Ensure text and GIF reflect "Ready to install"
                findViewById<TextView>(R.id.text_below_gif)?.text = getString(R.string.install_ready)
                val lottie = findViewById<LottieAnimationView>(R.id.lottie_rom_update)
                lottie.setAnimation(R.raw.new_update)
                lottie.playAnimation()
                
                return true // MD5 state handled
            }
            else -> { 
                android.util.Log.d("MainActivity", "No MD5 state to restore")
                // No MD5 state to restore
                return false
            }
        }
    }
    
    private fun reconcileInstallUiFromPrefs(): Boolean {
        val status = updatePrefs.getInstallStatus()
        android.util.Log.d("MainActivity", "reconcileInstallUiFromPrefs: status=$status")
        
        when (status) {
            UpdatePrefs.INSTALL_STATUS_READY -> {
                // Check if the install file still exists
                val filePath = updatePrefs.getInstallFilePath()
                val installFile = if (filePath != null) java.io.File(filePath) else null
                
                if (installFile?.exists() == true) {
                    android.util.Log.d("MainActivity", "Restoring Install ready state")
                    revealAnimator?.cancel()
                    setInstallButton()
                    showDownloadButton(true)
                    switchToNewVersionPreferences(updatePrefs.getSavedManifestData())
                    
                    // Ensure text and GIF reflect "Ready to install"
                    findViewById<TextView>(R.id.text_below_gif)?.text = getString(R.string.install_ready)
                    val lottie = findViewById<LottieAnimationView>(R.id.lottie_rom_update)
                    lottie.setAnimation(R.raw.new_update)
                    lottie.playAnimation()
                    
                    return true // Install state handled
                } else {
                    android.util.Log.d("MainActivity", "Install file not found, clearing install state")
                    updatePrefs.clearInstallState()
                    return false
                }
            }
            UpdatePrefs.INSTALL_STATUS_INSTALLING -> {
                // Check if the install file still exists
                val filePath = updatePrefs.getInstallFilePath()
                val installFile = if (filePath != null) java.io.File(filePath) else null
                
                if (installFile?.exists() == true) {
                    android.util.Log.d("MainActivity", "Restoring Install installing state")
                    setInstallButtonInstalling()
                    showDownloadButton(true)
                    switchToNewVersionPreferences(updatePrefs.getSavedManifestData())
                    
                    // Set appropriate text for installing state
                    findViewById<TextView>(R.id.text_below_gif)?.text = getString(R.string.install_generating_script)
                    
                    return true // Install state handled
                } else {
                    android.util.Log.d("MainActivity", "Install file not found during installing, clearing install state")
                    updatePrefs.clearInstallState()
                    return false
                }
            }
            else -> {
                android.util.Log.d("MainActivity", "No Install state to restore")
                return false
            }
        }
    }

    private fun isInternetAvailable(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    }

    private val downloadReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                DownloadForegroundService.ACTION_PROGRESS -> {
                    val percent = intent.getIntExtra(DownloadForegroundService.EXTRA_PERCENT, 0)
                    val eta = intent.getStringExtra(DownloadForegroundService.EXTRA_ETA) ?: "time remaining: 00:00:00"
                    val progressBar = findViewById<SeslProgressBar>(R.id.progress_bar_download)
                    val timeRemainingText = findViewById<TextView>(R.id.text_time_remaining)
                    val percentText = findViewById<TextView>(R.id.text_download_percent)
                    progressBar?.progress = percent
                    timeRemainingText?.text = eta
                    percentText?.text = "$percent%"
                    // updatePrefs.saveDownloadState(...) is now handled by the Service
                    setDownloadButtonCancel(true)
                    setDownloadButtonEnabled(true)
                    // Ensure progress bar is visible during active download broadcast
                    showDownloadProgress(true)
                    // Use stripped string for dots animation
                    startDotsAnimation(getString(R.string.downloading).replace("…", ""))
                }
                DownloadForegroundService.ACTION_MD5_PROGRESS -> {
                    findViewById<TextView>(R.id.text_time_remaining)?.text = getString(R.string.verifying_md5)
                    val p = intent.getIntExtra(DownloadForegroundService.EXTRA_PERCENT, 0)
                    findViewById<SeslProgressBar>(R.id.progress_bar_download)?.progress = p
                    findViewById<TextView>(R.id.text_download_percent)?.text = "$p%"
                    setDownloadButtonEnabled(false)
                    startDotsAnimation(getString(R.string.verifying_md5).replace("…", ""))
                }
                DownloadForegroundService.ACTION_MD5_SUCCESS -> {
                    updatePrefs.setMd5Result(UpdatePrefs.MD5_STATUS_SUCCESS, 100)
                    // Set install state and show Install button
                    val filePath = intent.getStringExtra(DownloadForegroundService.EXTRA_PATH)
                    if (filePath != null) {
                        updatePrefs.setInstallStatus(UpdatePrefs.INSTALL_STATUS_READY, filePath)
                    }
                    showDownloadProgress(false) // Hide progress bar
                    setInstallButton() // Show Install button
                    showDownloadButton(true)
                    switchToNewVersionPreferences(updatePrefs.getSavedManifestData())
                    stopDotsAnimation()
                    
                    // Show "Ready for installation" state with animation
                    val statusView = findViewById<TextView>(R.id.text_below_gif)
                    animateTextReveal(statusView, getString(R.string.install_ready))
                    
                    val lottie = findViewById<LottieAnimationView>(R.id.lottie_rom_update)
                    lottie.setAnimation(R.raw.new_update)
                    lottie.playAnimation()
                }
                DownloadForegroundService.ACTION_MD5_FAILED -> {
                    findViewById<TextView>(R.id.text_time_remaining)?.text = getString(R.string.md5_verified_failed)
                    val p = 100
                    findViewById<TextView>(R.id.text_download_percent)?.text = "$p%"
                    findViewById<SeslProgressBar>(R.id.progress_bar_download)?.progress = p
                    // Do not persist failed state; enable button
                    updatePrefs.clearMd5State()
                    setDownloadButtonEnabled(true)
                    stopDotsAnimation()
                    animateTextReveal(findViewById(R.id.text_below_gif), getString(R.string.md5_verified_failed))
                }
                DownloadForegroundService.ACTION_COMPLETE -> {
                    showDownloadProgress(false)
                    updatePrefs.clearDownload()
                    setDownloadButtonCancel(false)
                    setDownloadButtonEnabled(true)
                    stopDotsAnimation()
                    
                    // Only update UI if not currently in MD5 or Install ready state
                    if (!updatePrefs.isMd5Active() && !updatePrefs.isInstallReady()) {
                        updateUIForComparison(DateComparisonResult.UP_TO_DATE)
                    }
                }
                DownloadForegroundService.ACTION_FAILED -> {
                    showDownloadProgress(false)
                    updatePrefs.clearDownload()
                    android.widget.Toast.makeText(this@MainActivity, "Download failed", android.widget.Toast.LENGTH_SHORT).show()
                    setDownloadButtonCancel(false)
                    setDownloadButtonEnabled(true)
                    stopDotsAnimation()
                    
                    val manifest = updatePrefs.getSavedManifestData()
                    if (manifest != null) {
                        updateUIForComparison(DateComparisonResult.UPDATE_AVAILABLE, manifest)
                    } else {
                        updateUIForComparison(DateComparisonResult.UNKNOWN)
                    }
                }
                DownloadForegroundService.ACTION_CANCELED -> {
                    showDownloadProgress(false)
                    updatePrefs.clearDownload()
                    android.widget.Toast.makeText(this@MainActivity, "Download canceled", android.widget.Toast.LENGTH_SHORT).show()
                    setDownloadButtonCancel(false)
                    setDownloadButtonEnabled(true)
                    stopDotsAnimation()
                    
                    val manifest = updatePrefs.getSavedManifestData()
                    if (manifest != null) {
                        updateUIForComparison(DateComparisonResult.UPDATE_AVAILABLE, manifest)
                    } else {
                        updateUIForComparison(DateComparisonResult.UNKNOWN)
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadReceiver)
        } catch (_: Exception) {}
    }

    
    // Method to play animation again (can be called from other parts of the app)
    fun playRomUpdateAnimation() {
        val lottieAnimationView = findViewById<LottieAnimationView>(R.id.lottie_rom_update)
        lottieAnimationView.playAnimation()
    }
    
    private fun checkForUpdates() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Get build prop date
                val buildPropReader = BuildPropReader(this@MainActivity)
                val buildPropDate = buildPropReader.getBuildDate()
                
                // Get manifest data
                val getManifest = GetManifest(this@MainActivity)
                val manifestData = withContext(Dispatchers.IO) {
                    getManifest.fetchManifestData()
                }
                
                if (manifestData != null && buildPropDate != getString(R.string.unknown)) {
                    // Compare dates
                    val comparisonResult = getManifest.compareDates(buildPropDate, manifestData.buildDate)
                    
                    // Update UI based on comparison
                    updateUIForComparison(comparisonResult, manifestData)

                    // Persist or clear state depending on result
                    when (comparisonResult) {
                        DateComparisonResult.UPDATE_AVAILABLE -> updatePrefs.saveManifestData(manifestData)
                        DateComparisonResult.UP_TO_DATE -> updatePrefs.clearUpdate()
                        else -> {}
                    }
                } else if (manifestData == null) {
                    // If we can't get manifest data, it's likely no internet
                    updateUIForComparison(DateComparisonResult.NO_INTERNET)
                } else {
                    // If we can't get build prop data, keep "Unknown"
                    updateUIForComparison(DateComparisonResult.UNKNOWN)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateUIForComparison(DateComparisonResult.UNKNOWN)
            }
        }
    }
    
    private fun updateUIForComparison(result: DateComparisonResult, manifestData: ManifestData? = null) {
        val textView = findViewById<TextView>(R.id.text_below_gif)
        val lottieAnimationView = findViewById<LottieAnimationView>(R.id.lottie_rom_update)
        val progressBar = findViewById<SeslProgressBar>(R.id.progress_bar_manifest)
        
        // Hide progress bar and show GIF and text
        progressBar.visibility = View.GONE
        lottieAnimationView.visibility = View.VISIBLE
        textView.visibility = View.VISIBLE
        
        // Show preferences again
        showPreferences()

        // 1. Initial Preference Switching & Basic GIF setup
        when (result) {
            DateComparisonResult.UPDATE_AVAILABLE -> {
                switchToNewVersionPreferences(manifestData)
                lottieAnimationView.setAnimation(R.raw.new_update)
                lottieAnimationView.playAnimation()
                
                // Show changelog (from saved prefs if restoring, else fetch)
                if (allowFetchChangelog) {
                    showChangelog()
                } else {
                    val savedChangelog = updatePrefs.getSavedChangelog()
                    if (savedChangelog.isNullOrEmpty()) {
                        showChangelog()
                    } else {
                        getSettingsFragment()?.updateChangelogPreference(savedChangelog)
                    }
                }
            }
            DateComparisonResult.UP_TO_DATE -> {
                switchToCurrentVersionPreferences()
                lottieAnimationView.setAnimation(R.raw.rom_is_update)
                lottieAnimationView.playAnimation()
                hideChangelog()
            }
            DateComparisonResult.UNKNOWN -> {
                switchToCurrentVersionPreferences()
                lottieAnimationView.setAnimation(R.raw.rom_is_update)
                lottieAnimationView.playAnimation()
                hideChangelog()
            }
            DateComparisonResult.NO_INTERNET -> {
                switchToCurrentVersionPreferences()
                lottieAnimationView.setAnimation(R.raw.error)
                lottieAnimationView.playAnimation()
                hideChangelog()
            }
        }

        // 2. State Protection Guards (Only for main status text and specific buttons)
        val md5Status = updatePrefs.getMd5Status()
        val installStatus = updatePrefs.getInstallStatus()
        val isDownloading = updatePrefs.isDownloadActive()

        if (result == DateComparisonResult.UPDATE_AVAILABLE) {
            if (md5Status == UpdatePrefs.MD5_STATUS_SUCCESS || installStatus == UpdatePrefs.INSTALL_STATUS_READY) {
                // If already verified or ready to install, show "Ready" state
                animateTextReveal(textView, getString(R.string.install_ready))
                showDownloadButton(true)
                setInstallButton()
                return
            } else if (isDownloading || md5Status == UpdatePrefs.MD5_STATUS_ACTIVE) {
                 // If actively downloading/verifying, let those handlers manage text/Dots
                 showDownloadButton(true)
                 return
            }
        }
        
        // 3. Final Text Reveal and specific button logic
        when (result) {
            DateComparisonResult.UP_TO_DATE -> {
                animateTextReveal(textView, getString(R.string.rom_up_to_date))
                showDownloadButton(false)
            }
            DateComparisonResult.UPDATE_AVAILABLE -> {
                // Verify bootloader compatibility before showing download button
                if (manifestData != null) {
                    val bootloaderVerify = com.lich.updater.bootloader.BootloaderVerify(this)
                    val bootloaderResult = bootloaderVerify.verifyBootloaderCompatibility(manifestData)
                    
                    when (bootloaderResult) {
                        com.lich.updater.bootloader.BootloaderVerificationResult.COMPATIBLE -> {
                            showDownloadButton(true)
                            setDownloadButtonCancel(updatePrefs.isDownloadActive())
                        }
                        com.lich.updater.bootloader.BootloaderVerificationResult.INCOMPATIBLE -> {
                            showDownloadButton(true)
                            setDownloadButtonText("Update bootloader")
                            setDownloadButtonEnabled(false)
                            updatePrefs.saveBootloaderIncompatible(true)
                        }
                        com.lich.updater.bootloader.BootloaderVerificationResult.UNKNOWN -> {
                            showDownloadButton(true)
                            setDownloadButtonCancel(updatePrefs.isDownloadActive())
                        }
                    }
                } else {
                    showDownloadButton(true)
                    setDownloadButtonCancel(updatePrefs.isDownloadActive())
                }
                animateTextReveal(textView, getString(R.string.new_update_available))
            }
            DateComparisonResult.UNKNOWN -> {
                animateTextReveal(textView, getString(R.string.unknown))
                showDownloadButton(false)
            }
            DateComparisonResult.NO_INTERNET -> {
                animateTextReveal(textView, getString(R.string.internet_not_available))
                showDownloadButton(false)
            }
        }
    }
    
    fun checkForUpdatesWithProgress() {
        CoroutineScope(Dispatchers.Main).launch {
            // Show progress bar and hide GIF and text
            showProgressBar(true)
            
            // Add delay to show progress bar
            delay(1500)
            
            try {
                // Get build prop date
                val buildPropReader = BuildPropReader(this@MainActivity)
                val buildPropDate = buildPropReader.getBuildDate()
                
                // Get manifest data
                val getManifest = GetManifest(this@MainActivity)
                val manifestData = withContext(Dispatchers.IO) {
                    getManifest.fetchManifestData()
                }
                
                if (manifestData != null && buildPropDate != getString(R.string.unknown)) {
                    // Compare dates
                    val comparisonResult = getManifest.compareDates(buildPropDate, manifestData.buildDate)
                    
                    // Update UI based on comparison
                    updateUIForComparison(comparisonResult, manifestData)

                    // Persist or clear state depending on result
                    when (comparisonResult) {
                        DateComparisonResult.UPDATE_AVAILABLE -> updatePrefs.saveManifestData(manifestData)
                        DateComparisonResult.UP_TO_DATE -> updatePrefs.clearUpdate()
                        else -> {}
                    }
                } else if (manifestData == null) {
                    // If we can't get manifest data, it's likely no internet
                    updateUIForComparison(DateComparisonResult.NO_INTERNET)
                } else {
                    // If we can't get build prop data, keep "Unknown"
                    updateUIForComparison(DateComparisonResult.UNKNOWN)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateUIForComparison(DateComparisonResult.UNKNOWN)
            } finally {
                // Hide progress bar and show GIF and text
                showProgressBar(false)
            }
        }
    }
    
    private fun showProgressBar(show: Boolean) {
        val progressBar = findViewById<SeslProgressBar>(R.id.progress_bar_manifest)
        val lottieAnimationView = findViewById<LottieAnimationView>(R.id.lottie_rom_update)
        val textView = findViewById<TextView>(R.id.text_below_gif)
        
        if (show) {
            // Hide GIF and text, show progress bar
            lottieAnimationView.visibility = View.GONE
            textView.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            // Hide preferences, download button and changelog during progress
            hidePreferences()
            showDownloadButton(false)
        } else {
            // Show GIF and text, hide progress bar
            lottieAnimationView.visibility = View.VISIBLE
            textView.visibility = View.VISIBLE
            progressBar.visibility = View.GONE
            
            // Animation will be played by updateUIForComparison method
        }
    }
    
    private fun getSettingsFragment(): SettingsFragment? {
        return supportFragmentManager.findFragmentById(R.id.nav_host_main) as? SettingsFragment
    }
    
    private fun switchToNewVersionPreferences(manifestData: ManifestData?) {
        getSettingsFragment()?.switchToNewVersionPreferences(manifestData)
    }
    
    private fun switchToCurrentVersionPreferences() {
        getSettingsFragment()?.switchToCurrentVersionPreferences()
    }
    
    private fun hidePreferences() {
        // Hide the FragmentContainerView directly
        findViewById<View>(R.id.nav_host_main)?.visibility = View.GONE
        // Also hide preferences through fragment
        getSettingsFragment()?.hidePreferences()
    }
    
    private fun showPreferences() {
        // Show the FragmentContainerView directly
        findViewById<View>(R.id.nav_host_main)?.visibility = View.VISIBLE
        // Also show preferences through fragment
        getSettingsFragment()?.showPreferences()
    }
    
    private fun setupDownloadButton() {
        val downloadButton = findViewById<Button>(R.id.button_download)
        downloadButton.setOnClickListener {
            // Check if we're in install mode
            if (updatePrefs.isInstallReady()) {
                // Handle Install button click
                handleInstallClick()
                return@setOnClickListener
            }
            
            // Disable immediately to avoid double taps
            setDownloadButtonEnabled(false)
            if (updatePrefs.isDownloadActive()) {
                // Cancel current download
                val cancel = Intent(this, DownloadForegroundService::class.java)
                cancel.putExtra(DownloadForegroundService.EXTRA_COMMAND, DownloadForegroundService.COMMAND_CANCEL)
                androidx.core.content.ContextCompat.startForegroundService(this, cancel)
            } else {
                val saved = updatePrefs.getSavedManifestData()
                val url = saved?.url
                if (url.isNullOrEmpty()) {
                    android.widget.Toast.makeText(this, getString(R.string.unknown), android.widget.Toast.LENGTH_SHORT).show()
                    setDownloadButtonEnabled(true)
                    return@setOnClickListener
                }
                // Show progress UI immediately
                showDownloadProgress(true)
                setDownloadButtonCancel(true)
                updatePrefs.saveDownloadState(active = true, percent = 0, eta = "time remaining: 00:00:00")
                // Start foreground service to keep download alive
                val svc = Intent(this, DownloadForegroundService::class.java)
                svc.putExtra(DownloadForegroundService.EXTRA_URL, url)
                // Do not set EXTRA_FILE_NAME to preserve server filename
                androidx.core.content.ContextCompat.startForegroundService(this, svc)
            }
        }
    }
    
    private fun showDownloadButton(show: Boolean) {
        findViewById<Button>(R.id.button_download)?.visibility = if (show) View.VISIBLE else View.GONE
    }
    
    private    fun showDownloadProgress(show: Boolean) {
        findViewById<View>(R.id.download_progress_container)?.visibility = if (show) View.VISIBLE else View.GONE
        // Removed: updatePrefs.clearDownload() - State cleanup must be explicit (Cancel or Completion)
    }

    private fun setDownloadButtonCancel(isCancel: Boolean) {
        val btn = findViewById<Button>(R.id.button_download)
        btn?.text = if (isCancel) "Cancel" else getString(R.string.download)
    }

    private fun setDownloadButtonEnabled(enabled: Boolean) {
        val btn = findViewById<Button>(R.id.button_download)
        btn?.isEnabled = enabled
    }
    
    private fun setDownloadButtonText(text: String) {
        val btn = findViewById<Button>(R.id.button_download)
        btn?.text = text
    }
    
    private fun setInstallButton() {
        val btn = findViewById<Button>(R.id.button_download)
        btn?.text = getString(R.string.install)
        btn?.isEnabled = true
        android.util.Log.d("MainActivity", "Set Install button - ready for installation")
        
        // Show install notification
        installNotification.show()
    }
    
    private fun setInstallButtonInstalling() {
        val btn = findViewById<Button>(R.id.button_download)
        btn?.text = getString(R.string.install)
        btn?.isEnabled = false
        android.util.Log.d("MainActivity", "Set Install button - installing (disabled)")
    }
    
    private fun handleInstallClick() {
        android.util.Log.d("MainActivity", "Install button clicked")
        
        val filePath = updatePrefs.getInstallFilePath()
        if (filePath.isNullOrEmpty()) {
            android.widget.Toast.makeText(this, "No ROM file found for installation", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check if installation is possible
        if (!romInstall.canInstall()) {
            android.widget.Toast.makeText(this, getString(R.string.install_no_permission), android.widget.Toast.LENGTH_LONG).show()
            return
        }
        
        // Hide install notification
        installNotification.hide()
        
        // Set installing state
        updatePrefs.setInstallStatus(UpdatePrefs.INSTALL_STATUS_INSTALLING, filePath)
        setInstallButtonInstalling()
        
        // Show generating script message
        android.widget.Toast.makeText(this, getString(R.string.install_generating_script), android.widget.Toast.LENGTH_SHORT).show()
        
        // Start installation (will generate script and reboot to recovery)
        android.util.Log.d("MainActivity", "Starting ROM installation for: $filePath")
        romInstall.installRom(filePath)
        
        // Clear install state since the file will be deleted during installation
        updatePrefs.clearInstallState()
        
        // Show rebooting message
        android.widget.Toast.makeText(this, getString(R.string.install_rebooting), android.widget.Toast.LENGTH_LONG).show()
    }

    private fun showChangelog() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                android.util.Log.d("MainActivity", "Starting to fetch changelog...")
                val changelogLines = changelogReader.fetchChangelog()
                android.util.Log.d("MainActivity", "Changelog lines: $changelogLines")
                
                if (changelogLines != null && changelogLines.isNotEmpty()) {
                    val formattedChangelog = changelogReader.formatChangelog(changelogLines)
                    android.util.Log.d("MainActivity", "Formatted changelog: $formattedChangelog")
                    // Persist formatted changelog so we can restore it without network
                    updatePrefs.saveChangelog(formattedChangelog)
                    
                    val settingsFragment = getSettingsFragment()
                    android.util.Log.d("MainActivity", "SettingsFragment: $settingsFragment")
                    
                    if (settingsFragment != null) {
                        settingsFragment.updateChangelogPreference(formattedChangelog)
                        android.util.Log.d("MainActivity", "Changelog preference updated")
                    } else {
                        android.util.Log.e("MainActivity", "SettingsFragment is null!")
                    }
                } else {
                    android.util.Log.w("MainActivity", "Changelog lines are null or empty")
                    val saved = updatePrefs.getSavedChangelog()
                    getSettingsFragment()?.updateChangelogPreference(saved ?: getString(R.string.internet_not_available))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("MainActivity", "Error loading changelog: ${e.message}")
                val saved = updatePrefs.getSavedChangelog()
                getSettingsFragment()?.updateChangelogPreference(saved ?: "Error loading changelog")
            }
        }
    }
    
    private fun hideChangelog() {
        getSettingsFragment()?.updateChangelogPreference("")
    }

    private fun findFirstToolbar(root: View): Toolbar? {
        if (root is Toolbar) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findFirstToolbar(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }
    
    private fun startBackgroundUpdateChecker() {
        if (updatePrefs.isUpdateNotificationsEnabled()) {
            UpdatesCheckReceiver.schedule(this)
        } else {
            UpdatesCheckReceiver.cancel(this)
        }
    }

    private fun animateTextReveal(view: TextView, text: String) {
        val length = text.length
        if (length == 0) {
            view.text = ""
            return
        }

        view.text = text
        view.alpha = 1f
        view.translationX = 0f

        val spannable = android.text.SpannableString(text)
        val baseColor = view.currentTextColor
        
        // Multiplier to create a "sweep" effect where letters overlap their fade cycles
        val sweepFactor = 5 
        val animator = android.animation.ValueAnimator.ofInt(0, length * sweepFactor)
        revealAnimator = animator
        animator.duration = (length * 30L + 200).coerceAtMost(1000L)
        animator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Int
            for (i in 0 until length) {
                // Calculate individual alpha for each character based on progress
                // Character 'i' starts fading when progress reaches (i * sweepFactor)
                val charProgress = (progress - i * sweepFactor).coerceIn(0, sweepFactor)
                val alpha = (charProgress * 255 / sweepFactor)
                
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(androidx.core.graphics.ColorUtils.setAlphaComponent(baseColor, alpha)),
                    i, i + 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            view.text = spannable
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (revealAnimator == animation) revealAnimator = null
            }
        })
        animator.start()
    }

    private fun startDotsAnimation(baseText: String) {
        // Cancel any reveal animation before starting dots (prevents overlapping)
        revealAnimator?.cancel()
        revealAnimator = null

        // If the base text is the same, don't restart (prevents stuttering on progress updates)
        if (dotsJob?.isActive == true && currentDotsBaseText == baseText) return
        
        dotsJob?.cancel()
        currentDotsBaseText = baseText
        
        val statusText = findViewById<TextView>(R.id.text_below_gif) ?: return
        statusText.visibility = View.VISIBLE
        
        dotsJob = CoroutineScope(Dispatchers.Main).launch {
            var dotsCount = 0
            while (true) {
                val dotsStr = ".".repeat(dotsCount)
                statusText.text = "$baseText$dotsStr"
                dotsCount = (dotsCount + 1) % 4
                delay(600) // Slightly slower for better readability
            }
        }
    }

    private fun stopDotsAnimation() {
        dotsJob?.cancel()
        dotsJob = null
        currentDotsBaseText = null
    }
}

