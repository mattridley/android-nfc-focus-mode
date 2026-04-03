package com.example.nfcappblocker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nfcappblocker.data.AppDatabase
import com.example.nfcappblocker.data.AppEntity
import com.example.nfcappblocker.data.EnrolledTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var statusText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppAdapter
    private lateinit var db: AppDatabase
    private var isEnrollMode = false
    private var isUnrollMode = false

    private lateinit var sharedPrefs: SharedPreferences
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "isBlockedMode") {
            runOnUiThread { updateStatusUI() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        db = AppDatabase.getDatabase(this)
        sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener)

        statusText = findViewById(R.id.statusText)
        recyclerView = findViewById(R.id.recyclerView)
        val btnEnrollTag = findViewById<Button>(R.id.btnEnrollTag)
        val btnUnrollTag = findViewById<Button>(R.id.btnUnrollTag)
        val editMasterPin = findViewById<EditText>(R.id.editMasterPin)
        val btnSavePin = findViewById<Button>(R.id.btnSavePin)

        val currentPin = sharedPrefs.getString("master_pin", "")
        editMasterPin.setText(currentPin)

        btnSavePin.setOnClickListener {
            val pin = editMasterPin.text.toString()
            if (pin.length == 4) {
                sharedPrefs.edit().putString("master_pin", pin).apply()
                Toast.makeText(this, "PIN Saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "PIN must be 4 digits", Toast.LENGTH_SHORT).show()
            }
        }

        updateStatusUI()

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AppAdapter(packageManager) { packageName, isWhitelisted ->
            lifecycleScope.launch {
                db.appDao().insertApp(AppEntity(packageName, isWhitelisted))
            }
        }
        recyclerView.adapter = adapter

        btnEnrollTag.setOnClickListener {
            isEnrollMode = true
            isUnrollMode = false
            Toast.makeText(this, "Tap NFC tag to ENROLL", Toast.LENGTH_SHORT).show()
        }

        btnUnrollTag.setOnClickListener {
            isUnrollMode = true
            isEnrollMode = false
            Toast.makeText(this, "Tap NFC tag to UNROLL", Toast.LENGTH_SHORT).show()
        }

        loadApps()
    }

    private fun loadApps() {
        lifecycleScope.launch {
            val installedApps = withContext(Dispatchers.IO) {
                packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || it.packageName == "com.android.settings" }
                    .sortedBy { it.loadLabel(packageManager).toString() }
            }

            val whitelistedApps = db.appDao().getAllWhitelistedApps().first().associateBy { it.packageName }

            val appItems = installedApps.map {
                AppAdapter.AppItem(
                    it.packageName,
                    it.loadLabel(packageManager).toString(),
                    whitelistedApps[it.packageName]?.isWhitelisted ?: false
                )
            }
            adapter.setApps(appItems)
        }
    }

    private fun updateStatusUI() {
        val isBlockedMode = sharedPrefs.getBoolean("isBlockedMode", false)
        statusText.text = if (isBlockedMode) "Focus Mode: ON" else "Focus Mode: OFF"
    }

    override fun onResume() {
        super.onResume()
        updateStatusUI()
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter.disableForegroundDispatch(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
        val tagId = NfcUtils.bytesToHexString(tag.id)

        lifecycleScope.launch {
            if (isEnrollMode) {
                db.appDao().enrollTag(EnrolledTag(tagId))
                Toast.makeText(this@MainActivity, "Tag Enrolled: $tagId", Toast.LENGTH_SHORT).show()
                isEnrollMode = false
            } else if (isUnrollMode) {
                db.appDao().unrollTag(EnrolledTag(tagId))
                Toast.makeText(this@MainActivity, "Tag Unrolled: $tagId", Toast.LENGTH_SHORT).show()
                isUnrollMode = false
            } else {
                if (db.appDao().isTagEnrolled(tagId)) {
                    toggleFocusMode()
                } else {
                    Toast.makeText(this@MainActivity, "Tag not enrolled!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun toggleFocusMode() {
        val currentMode = sharedPrefs.getBoolean("isBlockedMode", false)
        val newMode = !currentMode
        sharedPrefs.edit().putBoolean("isBlockedMode", newMode).apply()
        Toast.makeText(this, "Focus Mode: ${if (newMode) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
    }
}
