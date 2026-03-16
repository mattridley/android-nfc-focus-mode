package com.example.nfcappblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val isBlockedMode = sharedPrefs.getBoolean("isBlockedMode", false)
            
            if (isBlockedMode) {
                // If focus mode was active, remind the user or ensure service is running.
                // Accessibility services are started by the system if enabled.
                Toast.makeText(context, "Focus Mode is still ACTIVE", Toast.LENGTH_LONG).show()
            }
        }
    }
}
