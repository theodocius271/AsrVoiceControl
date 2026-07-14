package com.top.asrdemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.top.asrdemo.actions.Actor;
import com.top.asrdemo.commands.Matcher;
import com.top.asrdemo.service.AsrService;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {
    private static final String TAG = "MAIN ACTIVITY";
    private static final int RC_AUDIO_PERMISSIONS = 1001;

    // UI
    private TextView tvUserInput;
    private TextView tvSystemOutput;
    private ImageButton micBtn;

    // State
    private boolean isListening = false;

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
                    // TODO: Set system output
                    break;
                case AsrService.ACTION_ERROR:
                    String error = intent.getStringExtra(AsrService.EXTRA_ERROR);
                    Toast.makeText(MainActivity.this, "ASR Error: " + error, Toast.LENGTH_SHORT).show();
                    stopListening();
                    break;

            }
        }
    };

    private BroadcastReceiver matcherReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            if ("com.top.asrdemo.action.COMMAND_MATCHED".equals(action)) {
                String commandId = intent.getStringExtra("command_id");
                String commandText = intent.getStringExtra("command_text");
                float similarity = intent.getFloatExtra("similarity", 0.0f);
                String originalText = intent.getStringExtra("original_text");

                if (commandId != null) {
                    // Match found
                    @SuppressLint("DefaultLocale") String output = String.format("Matched: %s (%.2f%%)",
                            commandText, similarity * 100);
                    setSystemOutput(output);
                    Log.i(TAG, output);
                } else {
                    // No match
                    setSystemOutput("No matching command");
                    Log.i(TAG, "No command matched for: " + originalText);
                }
            }
        }
    };

    private Matcher matcher;
    private Actor actor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // init Views
        tvUserInput = findViewById(R.id.tv_use_input);
        tvSystemOutput = findViewById(R.id.tv_system_output);
        micBtn = findViewById(R.id.btn_microphone);
        micBtn.setOnClickListener(v -> toggleListening());

        // init actor
        actor = new Actor(this);
        actor.start();

        // init matcher (Singleton)
        matcher = Matcher.getInstance();
        if (!matcher.isInitialized()) {
            boolean success = matcher.initialize(this);
            if (!success) {
                Toast.makeText(this, "Failed to init command Matcher", Toast.LENGTH_LONG).show();
            }
        }

        registerAsrReceiver();
        registerMatcherReceiver();
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(asrReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(matcherReceiver);

        matcher.release();
        if (actor != null) {
            try {
                actor.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing actor", e);
            }
        }

        if (isListening) {
            stopAsrService();
        }

        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private void registerAsrReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(AsrService.ACTION_FINAL_RESULT);
        filter.addAction(AsrService.ACTION_PARTIAL_RESULT);
        filter.addAction(AsrService.ACTION_ERROR);
        LocalBroadcastManager.getInstance(this).registerReceiver(asrReceiver, filter);
    }

    @SuppressWarnings("deprecation")
    private void registerMatcherReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.top.asrdemo.action.COMMAND_MATCHED");
        LocalBroadcastManager.getInstance(this).registerReceiver(matcherReceiver, filter);
    }

    private void toggleListening() {
        if (isListening) {
            stopListening();
        } else {
            startListening();
        }
    }

    @AfterPermissionGranted(RC_AUDIO_PERMISSIONS)
    private void startListening() {
        String[] perms = getRequiredPermissions();

        if (EasyPermissions.hasPermissions(this, perms)) {
            // granted
            isListening = true;
            micBtn.setBackgroundResource(R.drawable.circular_button_background_started);
            clearDisplays();
            startAsrService();
            Log.i(TAG, "Started listening");
        } else {
            EasyPermissions.requestPermissions(
                    this,
                    "Requires microphone permission for ASR service",
                    RC_AUDIO_PERMISSIONS,
                    perms
            );
        }
    }

    private void stopListening() {
        isListening = false;
        micBtn.setBackgroundResource(R.drawable.circular_button_background_paused);
        stopAsrService();
        clearDisplays();
        Log.i(TAG, "Stopped Listening");
    }


    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[] {
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            return new String[] {Manifest.permission.RECORD_AUDIO};
        }
    }

    private void startAsrService() {
        Intent intent = new Intent(this, AsrService.class);
        intent.setAction(AsrService.ACTION_START);
        startService(intent);
    }

    private void stopAsrService() {
        Intent intent = new Intent(this, AsrService.class);
        intent.setAction(AsrService.ACTION_STOP);
        startService(intent);
    }

    private void showTextViewWithAnimation(TextView tv, String text) {
        tv.setText(text);
        tv.setAlpha(0f);
        tv.setVisibility(View.VISIBLE);
        tv.animate()
                .alpha(1f)
                .setDuration(300)
                .setListener(null);
    }

    @SuppressLint("SetTextI18n")
    private void setUserInput(String text) {
        runOnUiThread(() -> {
            if (text == null || text.trim().isEmpty()) {
                tvUserInput.setVisibility(View.GONE);
                tvUserInput.setText("");
            } else {
                tvUserInput.setText("User: " + text);
                tvUserInput.setVisibility(View.VISIBLE);
                // showTextViewWithAnimation(tvUserInput, "User: " + text);
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void setSystemOutput(String text) {
        runOnUiThread(() -> {
            if (text == null || text.trim().isEmpty()) {
                tvSystemOutput.setVisibility(View.GONE);
                tvSystemOutput.setText("");
            } else {
//                tvSystemOutput.setText("User: " + text);
//                tvSystemOutput.setVisibility(View.VISIBLE);
                showTextViewWithAnimation(tvSystemOutput, "System: " + text);
            }
        });
    }

    private void clearDisplays() {
        setUserInput(null);
        setSystemOutput(null);
    }

    // EasyPermission callbacks
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
        Log.w(TAG, "Permission denied: " + perms);
        Toast.makeText(this, "Microphone permission is required for ASR", Toast.LENGTH_LONG).show();
    }
}
