# Detailed Implementation Plan: NFC App Blocker

This document outlines the technical implementation for an Android app that blocks all non-whitelisted apps when toggled via an NFC tag tap.

## 1. Project Setup & Permissions

**1.1. AndroidManifest.xml Requirements**
Declare the necessary permissions to control system-level features, read NFC tags, and draw overlays over blocked apps.

```xml
<uses-permission android:name="android.permission.NFC" />
<uses-feature android:name="android.hardware.nfc" android:required="true" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />

<uses-permission android:name="android.permission.BIND_DEVICE_ADMIN" />

```

## 2. Whitelist Management (Room Database)

**2.1. UI Implementation**

* Create a `RecyclerView` that lists all installed applications using `PackageManager.getInstalledApplications()`.
* Filter out core system apps to prevent bricking the phone experience (always whitelist the launcher, phone dialer, and system UI).
* Add a `Switch` next to each app to toggle its whitelist status.

**2.2. Data Persistence**

* Use Android Room (SQLite) to store the Whitelisted apps.
* **Entity:** `AppEntity (packageName: String, isWhitelisted: Boolean)`
* Alternatively, for a simpler implementation, store a `Set<String>` of whitelisted package names in `SharedPreferences` or `DataStore`.

## 3. NFC Tag Interception & State Management

**3.1. Writing to the Tag**

* Provide a utility in the app to write a specific NDEF text record (e.g., `APP_BLOCKER_TOGGLE`) to an NFC tag. This ensures random tags don't trigger the lock.

**3.2. Reading & Toggling**

* In `MainActivity`, set up `NfcAdapter.getDefaultAdapter(this)`.
* Use `enableForegroundDispatch` to catch NFC taps when the app is open.
* **Logic Flow on Tap:**
1. Read the NDEF payload.
2. If payload == `APP_BLOCKER_TOGGLE`:
3. Read current state from `SharedPreferences` (`isBlockedMode = true/false`).
4. Invert state (`isBlockedMode = !isBlockedMode`).
5. Save new state.
6. If true: Start the `BlockerAccessibilityService` (if not already running). If false: Stop blocking logic.



## 4. App Monitoring (Accessibility Service)

Using an `AccessibilityService` is the most battery-efficient and responsive way to detect app launches.

**4.1. Configuration (`accessibility_service_config.xml`)**
Define the service to listen for window state changes.

```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault"
    android:canRetrieveWindowContent="false"
    android:description="@string/accessibility_desc" />

```

**4.2. Implementation Logic (`AppBlockerService.kt`)**

```kotlin
override fun onAccessibilityEvent(event: AccessibilityEvent) {
    if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
        val packageName = event.packageName?.toString() ?: return
        
        // 1. Check if Block Mode is active
        val isBlockedMode = sharedPrefs.getBoolean("isBlockedMode", false)
        if (!isBlockedMode) return
        
        // 2. Check if app is whitelisted
        val isWhitelisted = db.isAppWhitelisted(packageName)
        
        // 3. Block if not whitelisted
        if (!isWhitelisted) {
            showBlockingOverlay()
        }
    }
}

```

## 5. The Blocking Overlay

When an unauthorized app opens, the user needs to be intercepted immediately.

**5.1. Creating the View**

* Create a layout XML for the blocked screen (e.g., a lock icon with text "Focus Mode Active. Tap NFC to Unlock.").
* Add a "Go Home" button.

**5.2. WindowManager Implementation**

```kotlin
private fun showBlockingOverlay() {
    val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // Requires SYSTEM_ALERT_WINDOW
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    )
    
    val overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_blocked, null)
    
    // Send user back to home screen on click
    overlayView.findViewById<Button>(R.id.btnHome).setOnClickListener {
        val homeIntent = Intent(Intent.ACTION_MAIN)
        homeIntent.addCategory(Intent.CATEGORY_HOME)
        homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(homeIntent)
        windowManager.removeView(overlayView)
    }
    
    windowManager.addView(overlayView, params)
}

```

## 6. Edge Cases & Security ("Anti-Cheat")

To prevent the user from simply disabling the app when they lack self-control:

* **Block the Settings App:** Ensure `com.android.settings` is NEVER whitelisted while Focus Mode is active. If they try to open settings to uninstall your app or revoke accessibility permissions, the overlay blocks it.
* **Device Administrator:** Implement `DeviceAdminReceiver`. Prompt the user to activate Device Admin for your app. This prevents the app from being uninstalled. You can intercept attempts to deactivate the Device Admin via your Accessibility Service and block that too.
* **Boot Persistence:** Register a `BroadcastReceiver` for `Intent.ACTION_BOOT_COMPLETED`. If the phone restarts, check `isBlockedMode` in SharedPreferences. If it's `true`, ensure the blocking logic kicks in immediately upon startup.
