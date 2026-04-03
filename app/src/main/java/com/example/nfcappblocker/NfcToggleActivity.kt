package com.example.nfcappblocker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast
import com.example.nfcappblocker.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NfcToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val intent = intent
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                val tagId = NfcUtils.bytesToHexString(tag.id)
                val db = AppDatabase.getDatabase(this)
                
                CoroutineScope(Dispatchers.IO).launch {
                    if (db.appDao().isTagEnrolled(tagId)) {
                        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        val currentMode = sharedPrefs.getBoolean("isBlockedMode", false)
                        val newMode = !currentMode
                        sharedPrefs.edit().putBoolean("isBlockedMode", newMode).apply()
                        
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(this@NfcToggleActivity, "Focus Mode: ${if (newMode) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } else {
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(this@NfcToggleActivity, "Tag not enrolled!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
            } else {
                finish()
            }
        } else {
            finish()
        }
    }
}
