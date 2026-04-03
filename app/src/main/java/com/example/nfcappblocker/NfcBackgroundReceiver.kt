package com.example.nfcappblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.widget.Toast
import com.example.nfcappblocker.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NfcBackgroundReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
            val tagId = NfcUtils.bytesToHexString(tag.id)
            
            val db = AppDatabase.getDatabase(context)
            CoroutineScope(Dispatchers.IO).launch {
                if (db.appDao().isTagEnrolled(tagId)) {
                    val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val currentMode = sharedPrefs.getBoolean("isBlockedMode", false)
                    val newMode = !currentMode
                    sharedPrefs.edit().putBoolean("isBlockedMode", newMode).apply()
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Focus Mode: ${if (newMode) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
