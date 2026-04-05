package com.example.nfcappblocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.nfcappblocker.data.AppDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class AppBlockerService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var db: AppDatabase
    
    private var whitelistedPackages = setOf<String>()
    private var lastPackageName: String? = null
    
    private lateinit var sharedPrefs: SharedPreferences
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "isBlockedMode") {
            val isBlocked = sharedPrefs.getBoolean("isBlockedMode", false)
            if (!isBlocked) {
                serviceScope.launch { removeBlockingOverlay() }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        db = AppDatabase.getDatabase(this)
        sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener)
        
        // Synchronously load initial whitelist to prevent first-event flicker
        serviceScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) {
                    db.appDao().getAllWhitelistedApps().first()
                }
                whitelistedPackages = apps.filter { it.isWhitelisted }.map { it.packageName }.toSet()
            } catch (e: Exception) { }
            
            // Continue collecting updates
            db.appDao().getAllWhitelistedApps().collect { apps ->
                whitelistedPackages = apps.filter { it.isWhitelisted }.map { it.packageName }.toSet()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""
        
        // Prevent flickering: The overlay itself triggers a WINDOW_STATE_CHANGED event.
        // If the event is from our app, but the class is a basic Android view (not our Activities),
        // it is likely the overlay. We must ignore it so we don't accidentally remove the overlay.
        if (packageName == this.packageName && !className.startsWith("com.example.nfcappblocker")) {
            return
        }
        
        // Ignore packages like SystemUI or keyboards that shouldn't alter the overlay state.
        if (isIgnoredPackage(packageName)) {
            return
        }
        
        // Optimization: Ignore repeated events for the same package if state is already correct
        if (packageName == lastPackageName && overlayView != null && !isPackageSafe(packageName)) return
        if (packageName == lastPackageName && overlayView == null && isPackageSafe(packageName)) return
        
        lastPackageName = packageName

        if (!sharedPrefs.getBoolean("isBlockedMode", false)) {
            removeBlockingOverlay()
            return
        }

        if (isPackageSafe(packageName)) {
            removeBlockingOverlay()
        } else {
            showBlockingOverlay()
        }
    }

    private fun isPackageSafe(packageName: String): Boolean {
        // 1. App's own package (Settings, Permissions, etc.)
        if (packageName == this.packageName) return true
        
        // 2. Launcher detection (Custom and System)
        if (isLauncherPackage(packageName)) return true
        
        // 3. System UI and Critical Components
        if (isSystemPackage(packageName)) return true
        
        // 4. User-defined whitelist
        if (whitelistedPackages.contains(packageName)) return true
        
        return false
    }

    private fun isLauncherPackage(packageName: String): Boolean {
        // Hardcoded known launchers for speed
        val launchers = listOf(
            "bitpit.launcher", // Niagara
            "com.google.android.apps.nexuslauncher", // Pixel
            "com.google.android.googlequicksearchbox", // Google Search / Search Drawer
            "com.teslacoilsw.launcher", // Nova
            "com.sec.android.app.launcher" // Samsung
        )
        if (launchers.contains(packageName)) return true
        if (packageName.lowercase().contains("launcher")) return true
        
        // Dynamic fallback: catch all installed launchers in case multiple exist
        // or a default hasn't been set yet.
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfos.any { it.activityInfo?.packageName == packageName }
    }

    private fun isSystemPackage(packageName: String): Boolean {
        val systemApps = listOf(
            "com.android.settings",
            "com.google.android.settings",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.phone",
            "com.android.server.telecom",
            "com.samsung.android.incallui"
        )
        if (systemApps.contains(packageName)) return true
        if (packageName.lowercase().contains("dialer")) return true
        if (packageName.lowercase().contains("incallui")) return true
        return false
    }

    private fun isIgnoredPackage(packageName: String): Boolean {
        val ignoredPackages = listOf(
            "android",
            "com.android.systemui",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller"
        )
        if (ignoredPackages.contains(packageName)) return true
        
        // Ignore the active keyboard
        val defaultIme = try {
            android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore("/")
        } catch (e: Exception) { null }
        
        if (packageName == defaultIme) return true
        
        return false
    }

    private fun showBlockingOverlay() {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_blocked, null)
        
        val layoutMain = overlayView!!.findViewById<LinearLayout>(R.id.layoutMain)
        val layoutPin = overlayView!!.findViewById<LinearLayout>(R.id.layoutPin)
        val txtForgotTag = overlayView!!.findViewById<TextView>(R.id.txtForgotTag)
        val editPinEntry = overlayView!!.findViewById<EditText>(R.id.editPinEntry)
        val btnUnlockPin = overlayView!!.findViewById<Button>(R.id.btnUnlockPin)
        val btnCancelPin = overlayView!!.findViewById<Button>(R.id.btnCancelPin)

        overlayView?.findViewById<Button>(R.id.btnHome)?.setOnClickListener {
            // Remove first for instant feedback, then go home
            removeBlockingOverlay()
            val homeIntent = Intent(Intent.ACTION_MAIN)
            homeIntent.addCategory(Intent.CATEGORY_HOME)
            homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(homeIntent)
        }

        txtForgotTag.setOnClickListener {
            layoutMain.visibility = View.GONE
            layoutPin.visibility = View.VISIBLE
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager.updateViewLayout(overlayView, params)
        }

        btnCancelPin.setOnClickListener {
            layoutPin.visibility = View.GONE
            layoutMain.visibility = View.VISIBLE
            editPinEntry.setText("")
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager.updateViewLayout(overlayView, params)
        }

        btnUnlockPin.setOnClickListener {
            val enteredPin = editPinEntry.text.toString()
            val masterPin = sharedPrefs.getString("master_pin", "0000")

            if (enteredPin == masterPin) {
                sharedPrefs.edit().putBoolean("isBlockedMode", false).apply()
                Toast.makeText(this, "Focus Mode Disabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                editPinEntry.setText("")
            }
        }

        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) { }
    }

    private fun removeBlockingOverlay() {
        overlayView?.let {
            try {
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
            } catch (e: Exception) { }
            overlayView = null
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        serviceJob.cancel()
    }
}
