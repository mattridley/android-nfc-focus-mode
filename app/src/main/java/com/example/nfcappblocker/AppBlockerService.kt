package com.example.nfcappblocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AppBlockerService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var db: AppDatabase

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        db = AppDatabase.getDatabase(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val isBlockedMode = sharedPrefs.getBoolean("isBlockedMode", false)
            
            if (!isBlockedMode) {
                removeBlockingOverlay()
                return
            }

            serviceScope.launch {
                val isWhitelisted = db.appDao().isAppWhitelisted(packageName)
                val isSelf = packageName == this@AppBlockerService.packageName
                
                // Always allow the launcher (standard approach)
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_HOME)
                val resolveInfo = packageManager.resolveActivity(intent, 0)
                val launcherPackage = resolveInfo?.activityInfo?.packageName

                if (!isWhitelisted && !isSelf && packageName != launcherPackage) {
                    showBlockingOverlay()
                } else {
                    removeBlockingOverlay()
                }
            }
        }
    }

    private fun showBlockingOverlay() {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
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
            val homeIntent = Intent(Intent.ACTION_MAIN)
            homeIntent.addCategory(Intent.CATEGORY_HOME)
            homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(homeIntent)
            removeBlockingOverlay()
        }

        txtForgotTag.setOnClickListener {
            layoutMain.visibility = View.GONE
            layoutPin.visibility = View.VISIBLE
        }

        btnCancelPin.setOnClickListener {
            layoutPin.visibility = View.GONE
            layoutMain.visibility = View.VISIBLE
            editPinEntry.setText("")
        }

        btnUnlockPin.setOnClickListener {
            val enteredPin = editPinEntry.text.toString()
            val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val masterPin = sharedPrefs.getString("master_pin", "0000")

            if (enteredPin == masterPin) {
                sharedPrefs.edit().putBoolean("isBlockedMode", false).apply()
                Toast.makeText(this, "Focus Mode Disabled via PIN", Toast.LENGTH_SHORT).show()
                removeBlockingOverlay()
            } else {
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                editPinEntry.setText("")
            }
        }

        windowManager.addView(overlayView, params)
    }

    private fun removeBlockingOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) { }
            overlayView = null
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
