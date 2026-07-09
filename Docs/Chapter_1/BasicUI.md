# Chapter 1: Basic UI Implementation Guide

## Overview

This guide walks through implementing a clean, modern UI for the MainActivity with:
- **Upper section (4/5 height)**: Two glass-morphism TextViews for user input and system output
- **Lower section (1/5 height)**: Large circular microphone button
- **Features**: Auto-show/hide TextViews, prefix labels, no shadows on button

---

## Step 1: Update Layout XML

**File**: `app/src/main/res/layout/activity_main.xml`

Replace the entire content with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#F5F5F5">

    <!-- Upper section: 4/5 of screen -->
    <LinearLayout
        android:id="@+id/upper_section"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="4"
        android:orientation="vertical"
        android:gravity="center"
        android:padding="16dp">

        <!-- User Input TextView with glass-morphism background -->
        <TextView
            android:id="@+id/tv_user_input"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:padding="16dp"
            android:textSize="16sp"
            android:textColor="#000000"
            android:gravity="center"
            android:background="@drawable/glass_background"
            android:visibility="gone"
            android:layout_marginBottom="16dp" />

        <!-- System Output TextView with glass-morphism background -->
        <TextView
            android:id="@+id/tv_system_output"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:padding="16dp"
            android:textSize="16sp"
            android:textColor="#000000"
            android:gravity="center"
            android:background="@drawable/glass_background"
            android:visibility="gone" />

    </LinearLayout>

    <!-- Lower section: 1/5 of screen -->
    <FrameLayout
        android:id="@+id/lower_section"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:gravity="center">

        <!-- Circular Microphone Button -->
        <ImageButton
            android:id="@+id/btn_microphone"
            android:layout_width="80dp"
            android:layout_height="80dp"
            android:layout_gravity="center"
            android:background="@drawable/circular_button_background"
            android:src="@drawable/ic_microphone"
            android:scaleType="centerInside"
            android:contentDescription="@string/microphone_button" />

    </FrameLayout>

</LinearLayout>
```

---

## Step 2: Create Glass-Morphism Background

**File**: `app/src/main/res/drawable/glass_background.xml`

Create this new file:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    
    <!-- Semi-transparent white background for glass effect -->
    <solid android:color="#80FFFFFF" />
    
    <!-- Rounded corners -->
    <corners android:radius="16dp" />
    
    <!-- Subtle border -->
    <stroke
        android:width="1dp"
        android:color="#40FFFFFF" />
    
</shape>
```

---

## Step 3: Create Circular Button Background

**File**: `app/src/main/res/drawable/circular_button_background.xml`

Create this new file:

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- Pressed state -->
    <item android:state_pressed="true">
        <shape android:shape="oval">
            <solid android:color="#1976D2" />
        </shape>
    </item>
    
    <!-- Normal state -->
    <item>
        <shape android:shape="oval">
            <solid android:color="#2196F3" />
        </shape>
    </item>
    
</selector>
```

**Note**: This creates a perfect circle with no shadows. The button will be blue normally, darker blue when pressed.

---

## Step 4: Create Microphone Icon

**File**: `app/src/main/res/drawable/ic_microphone.xml`

Create this new file:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M12,14c1.66,0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6C9,12.66 10.34,14 12,14z"/>
    
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M17,11c0,2.76 -2.24,5 -5,5s-5,-2.24 -5,-5H5c0,3.53 2.61,6.43 6,6.92V21h2v-3.08c3.39,-0.49 6,-3.39 6,-6.92H17z"/>
    
</vector>
```

---

## Step 5: Add String Resource

**File**: `app/src/main/res/values/strings.xml`

Add this string (if not already present):

```xml
<resources>
    <string name="app_name">TopVoiceControl</string>
    <string name="microphone_button">Microphone button</string>
</resources>
```

---

## Step 6: Update MainActivity.java

**File**: `app/src/main/java/com/top/asrdemo/MainActivity.java`

Replace with the following complete implementation:

```java
package com.top.asrdemo;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.top.asrdemo.service.AsrService;

import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {
    
    private static final String TAG = "MainActivity";
    private static final int RC_AUDIO_PERMISSION = 1001;
    
    // UI components
    private TextView tvUserInput;
    private TextView tvSystemOutput;
    private ImageButton btnMicrophone;
    
    // State
    private boolean isListening = false;
    
    // Broadcast receiver for ASR results
    private BroadcastReceiver asrReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            
            switch (action) {
                case AsrService.ACTION_PARTIAL_RESULT:
                    String partialText = intent.getStringExtra(AsrService.EXTRA_TEXT);
                    setUserInput(partialText);
                    break;
                    
                case AsrService.ACTION_FINAL_RESULT:
                    String finalText = intent.getStringExtra(AsrService.EXTRA_TEXT);
                    setUserInput(finalText);
                    // System output will be set later by command matcher
                    break;
                    
                case AsrService.ACTION_ERROR:
                    String error = intent.getStringExtra(AsrService.EXTRA_ERROR);
                    Toast.makeText(MainActivity.this, "ASR Error: " + error, Toast.LENGTH_SHORT).show();
                    stopListening();
                    break;
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize views
        tvUserInput = findViewById(R.id.tv_user_input);
        tvSystemOutput = findViewById(R.id.tv_system_output);
        btnMicrophone = findViewById(R.id.btn_microphone);
        
        // Set up button click listener
        btnMicrophone.setOnClickListener(v -> toggleListening());
        
        // Register broadcast receiver
        registerAsrReceiver();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(asrReceiver);
        
        // Stop service if still running
        if (isListening) {
            stopAsrService();
        }
    }
    
    /**
     * Register broadcast receiver for ASR results
     */
    private void registerAsrReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(AsrService.ACTION_PARTIAL_RESULT);
        filter.addAction(AsrService.ACTION_FINAL_RESULT);
        filter.addAction(AsrService.ACTION_ERROR);
        LocalBroadcastManager.getInstance(this).registerReceiver(asrReceiver, filter);
    }
    
    /**
     * Toggle listening state
     */
    private void toggleListening() {
        if (isListening) {
            stopListening();
        } else {
            startListening();
        }
    }
    
    /**
     * Start listening with permission check
     */
    @AfterPermissionGranted(RC_AUDIO_PERMISSION)
    private void startListening() {
        String[] perms = getRequiredPermissions();
        
        if (EasyPermissions.hasPermissions(this, perms)) {
            // Permissions granted, start ASR
            isListening = true;
            btnMicrophone.setImageResource(R.drawable.ic_microphone_active);
            clearDisplays();
            startAsrService();
            Log.i(TAG, "Started listening");
        } else {
            // Request permissions
            EasyPermissions.requestPermissions(
                this,
                "This app needs microphone permission for voice recognition",
                RC_AUDIO_PERMISSION,
                perms
            );
        }
    }
    
    /**
     * Stop listening
     */
    private void stopListening() {
        isListening = false;
        btnMicrophone.setImageResource(R.drawable.ic_microphone);
        stopAsrService();
        Log.i(TAG, "Stopped listening");
    }
    
    /**
     * Get required permissions based on Android version
     */
    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            return new String[]{Manifest.permission.RECORD_AUDIO};
        }
    }
    
    /**
     * Start ASR service
     */
    private void startAsrService() {
        Intent intent = new Intent(this, AsrService.class);
        intent.setAction(AsrService.ACTION_START);
        startService(intent);
    }
    
    /**
     * Stop ASR service
     */
    private void stopAsrService() {
        Intent intent = new Intent(this, AsrService.class);
        intent.setAction(AsrService.ACTION_STOP);
        startService(intent);
    }
    
    /**
     * Update user input display
     * Automatically shows/hides TextView and adds "User: " prefix
     */
    public void setUserInput(String text) {
        runOnUiThread(() -> {
            if (text == null || text.trim().isEmpty()) {
                tvUserInput.setVisibility(View.GONE);
                tvUserInput.setText("");
            } else {
                tvUserInput.setText("User: " + text);
                tvUserInput.setVisibility(View.VISIBLE);
            }
        });
    }
    
    /**
     * Update system output display
     * Automatically shows/hides TextView and adds "System: " prefix
     */
    public void setSystemOutput(String text) {
        runOnUiThread(() -> {
            if (text == null || text.trim().isEmpty()) {
                tvSystemOutput.setVisibility(View.GONE);
                tvSystemOutput.setText("");
            } else {
                tvSystemOutput.setText("System: " + text);
                tvSystemOutput.setVisibility(View.VISIBLE);
            }
        });
    }
    
    /**
     * Clear all displays
     */
    public void clearDisplays() {
        setUserInput(null);
        setSystemOutput(null);
    }
    
    // EasyPermissions callbacks
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }
    
    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        Log.i(TAG, "Permissions granted: " + perms);
    }
    
    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        Log.w(TAG, "Permissions denied: " + perms);
        Toast.makeText(this, "Microphone permission is required for voice control", Toast.LENGTH_LONG).show();
    }
}
```

---

## Step 6.5: Update AsrService.java to Broadcast Results

The Activity and Service communicate through `LocalBroadcastManager`. The service already recognizes speech (in `onAudioData`); we now need it to:
1. Define action constants and start/stop handling
2. Broadcast partial and final results back to the Activity

**File**: `app/src/main/java/com/top/asrdemo/service/AsrService.java`

Replace with the following implementation:

```java
package com.top.asrdemo.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.top.asrdemo.asr.AsrManager;
import com.top.asrdemo.asr.AudioCapture;

public class AsrService extends Service {

    private static final String TAG = "ASR Service";
    private static final String CHANNEL_ID = "VoiceControlChannel";

    // Action constants for controlling the service
    public static final String ACTION_START = "com.top.asrdemo.action.START";
    public static final String ACTION_STOP = "com.top.asrdemo.action.STOP";

    // Action constants for broadcasting results
    public static final String ACTION_PARTIAL_RESULT = "com.top.asrdemo.action.PARTIAL_RESULT";
    public static final String ACTION_FINAL_RESULT = "com.top.asrdemo.action.FINAL_RESULT";
    public static final String ACTION_ERROR = "com.top.asrdemo.action.ERROR";

    // Extra keys for broadcast payloads
    public static final String EXTRA_TEXT = "extra_text";
    public static final String EXTRA_ERROR = "extra_error";

    private AsrManager asrManager;
    private AudioCapture audioCapture;
    private String lastText = "";
    private boolean isRunning = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // ACTION_START (or null / first launch): initialize and start
        if (!isRunning) {
            if (!startAsr()) {
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        return START_STICKY;
    }

    /**
     * Initialize ASR, audio capture, and start the foreground service.
     * @return true on success, false if initialization failed.
     */
    private boolean startAsr() {
        // Start foreground first so the OS does not kill us during model init
        startForeground(1, createNotification());

        // init ASR
        asrManager = new AsrManager(getAssets());
        if (!asrManager.initialize()) {
            Log.e(TAG, "Failed to init ASR");
            broadcastError("Failed to initialize ASR model");
            return false;
        }

        // init audio capture
        audioCapture = new AudioCapture(this::onAudioData);

        // start capturing
        audioCapture.start();
        isRunning = true;

        Log.i(TAG, "ASR Service created and started");
        return true;
    }

    private Notification createNotification() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                TAG,
                NotificationManager.IMPORTANCE_LOW
        );

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Voice Control Active")
                .setContentText("Listening for commands...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build();
    }

    private void onAudioData(float[] samples, int sampleRate) {
        // audio >> ASR
        asrManager.acceptWaveform(samples, sampleRate);
        asrManager.decode();
        // get current result
        String text = asrManager.getResult();

        // check for endpoint
        if (asrManager.isEndpoint()) {
            // add tail padding for better endpoint detection
            float[] padding = new float[(int) (0.8 * sampleRate)]; // 800ms padding
            asrManager.acceptWaveform(padding, sampleRate);
            asrManager.decode();

            text = asrManager.getResult();

            if (!text.isEmpty()) {
                Log.i(TAG, "Final result: " + text);
                onFinalResult(text);
            }
            asrManager.reset();
            lastText = "";
        } else if (!text.equals(lastText)) {
            Log.d(TAG, "Partial result: " + text);
            lastText = text;
            // Broadcast partial result to the UI
            broadcastResult(ACTION_PARTIAL_RESULT, text);
        }
    }

    private void onFinalResult(String text) {
        // Broadcast final result to the UI
        broadcastResult(ACTION_FINAL_RESULT, text);
        // TODO: Send to command matcher (future chapter)
        Log.i(TAG, "Recognized: " + text);
    }

    /**
     * Broadcast a recognition result to the Activity.
     */
    private void broadcastResult(String action, String text) {
        Intent intent = new Intent(action);
        intent.putExtra(EXTRA_TEXT, text);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /**
     * Broadcast an error to the Activity.
     */
    private void broadcastError(String error) {
        Intent intent = new Intent(ACTION_ERROR);
        intent.putExtra(EXTRA_ERROR, error);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        isRunning = false;

        if (audioCapture != null) {
            audioCapture.stop();
            audioCapture = null;
        }
        if (asrManager != null) {
            asrManager.release();
            asrManager = null;
        }

        Log.i(TAG, "ASR service stopped");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
```

### Key Changes Explained

| Change | Why |
|--------|-----|
| Moved init from `onCreate()` to `onStartCommand()` | Allows START/STOP actions to control the service lifecycle |
| Added `ACTION_START` / `ACTION_STOP` handling | Activity can start and stop the service via intents |
| Added broadcast constants and `EXTRA_TEXT` | Activity knows what to listen for and where to read text |
| `broadcastResult()` on partial results | `tv_user_input` updates live as the user speaks |
| `broadcastResult()` on final results | Final text stays displayed after endpoint |
| `broadcastError()` on init failure | Activity can show a Toast and reset the button |
| Reset `lastText = ""` after endpoint | Prevents stale text from blocking the next utterance's first partial |
| Null out references in `onDestroy()` | Avoids reuse of released native resources |

**Note on `tv_system_output`**: The service never broadcasts to the system output view in this chapter. Since the TextView starts with `visibility="gone"` and we only call `setUserInput()`, `tv_system_output` remains invisible — exactly the expected behavior until we add the command matcher in a later chapter.

---

## Step 6.6: Add Required Dependency and Manifest Entries

### Verify `localbroadcastmanager` dependency

`LocalBroadcastManager` lives in a separate AndroidX artifact. Confirm it is in **`app/build.gradle`**:

```gradle
dependencies {
    // ... existing dependencies (easypermissions, etc.)
    implementation 'androidx.localbroadcastmanager:localbroadcastmanager:1.1.0'
}
```

### Verify AndroidManifest.xml

Confirm the following permissions and service declaration exist in **`app/src/main/AndroidManifest.xml`** (inside `<manifest>` and `<application>` respectively):

```xml
<!-- Permissions -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Inside <application> -->
<service
    android:name=".service.AsrService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="microphone" />
```

**Note**: `LocalBroadcastManager` is deprecated by Google but is perfectly fine for this in-process Activity↔Service communication and requires no extra security configuration. If you prefer a modern alternative, a bound service with a callback interface or a shared `ViewModel` + `LiveData` are options for a future refactor.

---


## Step 7: Create Active Microphone Icon (Optional)

**File**: `app/src/main/res/drawable/ic_microphone_active.xml`

Create this for visual feedback when listening:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    
    <!-- Same as ic_microphone but with red color to indicate active -->
    <path
        android:fillColor="#FF5252"
        android:pathData="M12,14c1.66,0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6C9,12.66 10.34,14 12,14z"/>
    
    <path
        android:fillColor="#FF5252"
        android:pathData="M17,11c0,2.76 -2.24,5 -5,5s-5,-2.24 -5,-5H5c0,3.53 2.61,6.43 6,6.92V21h2v-3.08c3.39,-0.49 6,-3.39 6,-6.92H17z"/>
    
</vector>
```

---

## Step 8: Test the Complete UI with ASR Integration

### Pre-flight Checklist:

1. **Build the project** (Gradle sync + build)
2. **Verify no compile errors**
3. **Install on a real device** (emulator microphone may not work well)
4. **Grant microphone permission** when prompted

### End-to-End Test Flow:

1. **Launch the app**
   - Upper section should be empty (no TextViews visible)
   - Lower section should show a blue circular microphone button

2. **Tap the microphone button**
   - Icon should turn red (`ic_microphone_active`)
   - Permission dialog appears if first time → **grant permission**
   - ASR service starts (check Logcat for "ASR Service created and started")

3. **Speak a phrase** (e.g., "Hello World")
   - As you speak, `tv_user_input` should appear with partial results:
     - "User: Hello"
     - "User: Hello World"
   - Text updates live as ASR processes audio
   - Background is semi-transparent white (glass-morphism)
   - Text is centered

4. **Stop speaking and wait ~1 second**
   - ASR endpoint detection triggers
   - Final result appears: "User: Hello World"
   - Logcat shows: "Final result: Hello World"
   - `tv_system_output` remains invisible (expected — no command matcher yet)

5. **Tap the button again**
   - Icon reverts to white (`ic_microphone`)
   - ASR service stops
   - Displays remain visible with last result

6. **Tap the button a third time**
   - Displays clear (`clearDisplays()` called on new session)
   - Speak again to verify the cycle works

### Debug with Logcat:

Filter by tag `MainActivity` or `ASR Service` to see:
- "Started listening"
- "ASR Service created and started"
- "Partial result: ..."
- "Final result: ..."
- "Stopped listening"

### Expected Visual States:

| State | `tv_user_input` | `tv_system_output` | Button Icon |
|-------|-----------------|-------------------|-------------|
| Initial | Hidden | Hidden | White mic |
| Listening (silent) | Hidden | Hidden | Red mic |
| Listening (speaking) | "User: Hello..." | Hidden | Red mic |
| Stopped | "User: Hello World" | Hidden | White mic |
| Listening again | Hidden | Hidden | Red mic |

### Common Issues:

| Issue | Solution |
|-------|----------|
| Permission denied | Check Logcat for "Permissions denied"; re-install app and grant permission |
| No partial results | Ensure device has microphone; speak louder; check Logcat for "Partial result:" |
| Service crashes | Check Logcat for "Failed to init ASR"; verify model files are in `assets/` |
| Button doesn't respond | Check Logcat for errors; verify EasyPermissions dependency is in build.gradle |
| Text not appearing | Verify broadcast receiver is registered; check `registerAsrReceiver()` is called |

---

## Step 9: Optional Enhancements

### 9.1 Add Ripple Effect (Material Design)

If you want a subtle ripple on button press, update `circular_button_background.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="#40FFFFFF">
    <item>
        <shape android:shape="oval">
            <solid android:color="#2196F3" />
        </shape>
    </item>
</ripple>
```

**Note**: Requires API 21+. For older versions, stick with the selector approach.

### 9.2 Add Elevation to TextViews (Optional)

If you want a subtle shadow for depth, add to both TextViews in XML:

```xml
android:elevation="4dp"
```

**Note**: This adds a shadow. Skip if you want completely flat design.

### 9.3 Animate TextView Appearance

Add smooth fade-in animation when showing TextViews. In `MainActivity.java`:

```java
private void showTextViewWithAnimation(TextView textView, String text) {
    textView.setText(text);
    textView.setAlpha(0f);
    textView.setVisibility(View.VISIBLE);
    textView.animate()
        .alpha(1f)
        .setDuration(300)
        .setListener(null);
}

// Update setUserInput and setSystemOutput to use this:
public void setUserInput(String text) {
    if (text == null || text.trim().isEmpty()) {
        tvUserInput.setVisibility(View.GONE);
        tvUserInput.setText("");
    } else {
        showTextViewWithAnimation(tvUserInput, "User: " + text);
    }
}

public void setSystemOutput(String text) {
    if (text == null || text.trim().isEmpty()) {
        tvSystemOutput.setVisibility(View.GONE);
        tvSystemOutput.setText("");
    } else {
        showTextViewWithAnimation(tvSystemOutput, "System: " + text);
    }
}
```

---

## Step 10: Summary and Next Steps

### What You've Built

You now have a fully functional voice recognition UI that:

✅ Displays ASR partial results in real-time as the user speaks  
✅ Shows final recognized text after endpoint detection  
✅ Handles microphone permissions with EasyPermissions  
✅ Starts/stops the ASR service with button taps  
✅ Communicates between Activity and Service via LocalBroadcastManager  
✅ Auto-shows/hides TextViews with proper prefixes  
✅ Provides visual feedback (button icon changes)  

### File Summary

**Created/Modified Files:**

| File | Purpose |
|------|---------|
| `res/layout/activity_main.xml` | UI layout with 4:1 ratio split |
| `res/drawable/glass_background.xml` | Glass-morphism background for TextViews |
| `res/drawable/circular_button_background.xml` | Circular button with press states |
| `res/drawable/ic_microphone.xml` | White microphone icon (inactive) |
| `res/drawable/ic_microphone_active.xml` | Red microphone icon (active) |
| `MainActivity.java` | UI controller with ASR integration |
| `AsrService.java` | Background ASR service with broadcast communication |

**Key Classes and Methods:**

| Class | Key Methods | Purpose |
|-------|-------------|---------|
| `MainActivity` | `startListening()`, `stopListening()` | Control ASR lifecycle |
| `MainActivity` | `setUserInput()`, `setSystemOutput()` | Update UI displays |
| `MainActivity` | `registerAsrReceiver()` | Listen for ASR results |
| `AsrService` | `onStartCommand()` | Handle START/STOP actions |
| `AsrService` | `broadcastResult()` | Send results to Activity |
| `AsrService` | `onAudioData()` | Process audio and detect endpoints |

### Architecture Overview

```
User Tap Button
    ↓
MainActivity.startListening()
    ↓
MainActivity.startAsrService()
    ↓
AsrService.onStartCommand(ACTION_START)
    ↓
AsrService.startAsr() → AudioCapture.start()
    ↓
Audio frames → onAudioData() → AsrManager
    ↓
Partial results → broadcastResult(ACTION_PARTIAL_RESULT)
    ↓
MainActivity.asrReceiver.onReceive()
    ↓
MainActivity.setUserInput() → tv_user_input updates
```

### Next Steps

Now that the basic UI and ASR integration are complete, future chapters will add:

1. **Chapter 2: Command Matcher**
   - Parse final ASR text into actionable commands
   - Display command results in `tv_system_output`
   - Example: "open settings" → "Opening Settings..."

2. **Chapter 3: Action Executor**
   - Execute Android intents based on commands
   - Launch apps, open URLs, control system settings
   - Example: "call John" → open dialer with contact

3. **Chapter 4: Wake Word Detection**
   - Add always-on wake word ("Hey Assistant")
   - Button becomes optional (hands-free mode)
   - Power-efficient keyword spotting

4. **Chapter 5: Advanced Features**
   - Multi-language support
   - Command history
   - Custom command training
   - Voice feedback (TTS responses)

### Testing Recommendations

Before moving to Chapter 2:

- [ ] Test on multiple devices (different Android versions)
- [ ] Test with background noise (ASR robustness)
- [ ] Test rapid button taps (race conditions)
- [ ] Test permission denial flow (graceful degradation)
- [ ] Test long utterances (>10 seconds)
- [ ] Monitor memory usage (check for leaks in Profiler)
- [ ] Verify service stops completely (check running services)

### Known Limitations (to be addressed in later chapters)

- No wake word (requires button press)
- No command matching (just displays raw ASR text)
- No action execution (read-only UI)
- No multi-turn dialogue
- No error recovery (service crash requires app restart)
- Button must be manually pressed to stop (no auto-stop after endpoint)

---

## Congratulations! 🎉

You've completed Chapter 1: Basic UI with live ASR integration. The app now recognizes speech and displays it in real-time with a clean, modern interface.

**Ready for Chapter 2?** The next guide will show you how to turn raw ASR text into actionable commands.

---

## Final Checklist

### UI Elements
- [ ] Layout displays correctly (4:1 ratio)
- [ ] Circular button is perfectly round with no shadow
- [ ] Microphone icon is white and centered
- [ ] TextViews have glass-morphism background
- [ ] TextViews hide when empty
- [ ] TextViews show with correct prefix when populated
- [ ] Text is centered in TextViews

### Functionality
- [ ] Button responds to clicks (toggles icon color)
- [ ] Microphone permission is requested on first tap
- [ ] ASR service starts after permission granted
- [ ] Partial results appear in `tv_user_input` while speaking
- [ ] Final result appears after endpoint detection
- [ ] `tv_system_output` remains hidden (expected behavior)
- [ ] Button stops the service on second tap
- [ ] Displays clear when starting new session

### Code Quality
- [ ] No compile errors
- [ ] No warnings in Logcat (check for permission/service issues)
- [ ] Service properly stops in `onDestroy()`
- [ ] Broadcast receiver properly unregistered

### Testing
- [ ] Tested on actual device (not just emulator)
- [ ] Tested with different phrases
- [ ] Tested rapid button taps
- [ ] Tested permission denial flow
- [ ] Verified service cleanup (check running services after app close)

---

## Troubleshooting

### Issue: Button not perfectly circular
**Solution**: Ensure button width and height are equal (e.g., both 80dp)

### Issue: Glass background not showing
**Solution**: Verify `glass_background.xml` is in `res/drawable/` folder

### Issue: Icon not showing
**Solution**: Check that `ic_microphone.xml` is in `res/drawable/` folder

### Issue: TextViews not centering text
**Solution**: Verify `android:gravity="center"` is set in XML

### Issue: Background not transparent enough
**Solution**: Adjust alpha value in glass_background.xml: `#80FFFFFF` (80 = 50% opacity)
- For more transparent: use `#60FFFFFF` (37.5% opacity)
- For less transparent: use `#A0FFFFFF` (62.5% opacity)

### Issue: TextViews too wide on tablets
**Solution**: Wrap upper_section LinearLayout in a constraining container:

```xml
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="0dp"
    android:layout_weight="4">
    
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:maxWidth="600dp"
        android:orientation="vertical"
        android:padding="16dp">
        
        <!-- TextViews here -->
        
    </LinearLayout>
</FrameLayout>
```

---

## Next Steps

After completing this UI:

1. **Test with real ASR output**: Connect `setUserInput()` to ASR service callback
2. **Test with command matcher**: Connect `setSystemOutput()` to matcher callback
3. **Refine visual design**: Adjust colors, sizes, spacing based on preference
4. **Add loading state**: Show progress indicator while ASR is processing
5. **Implement button state**: Disable button during processing to prevent double-tap

---

## Code Summary

**New files created**:
- `res/layout/activity_main.xml` (replaced)
- `res/drawable/glass_background.xml`
- `res/drawable/circular_button_background.xml`
- `res/drawable/ic_microphone.xml`
- `res/drawable/ic_microphone_active.xml` (optional)

**Modified files**:
- `MainActivity.java` (refactored)
- `res/values/strings.xml` (added string)

**Key methods**:
- `setUserInput(String text)` - Display ASR result
- `setSystemOutput(String text)` - Display command matcher result
- `clearDisplays()` - Reset both displays
- `toggleListening()` - Handle button clicks

