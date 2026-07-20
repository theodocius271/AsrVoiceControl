package com.top.asrdemo;

import android.Manifest;
import android.annotation.SuppressLint;
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

import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.top.asrdemo.actions.Actor;
import com.top.asrdemo.commands.Commands;
import com.top.asrdemo.commands.Matcher;
import com.top.asrdemo.service.AsrService;
import com.top.asrdemo.utils.ChatboxManager;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {
    private static final String TAG = "MAIN ACTIVITY";
    private static final int RC_AUDIO_PERMISSIONS = 1001;

    // UI
    private ImageButton micBtn;
    private ChatboxManager chatboxManager;

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
                    chatboxManager.showPartialUserText(partialText);
                    break;
                case AsrService.ACTION_FINAL_RESULT:
                    String finalText = intent.getStringExtra(AsrService.EXTRA_TEXT);
                    chatboxManager.commitUserText(finalText);
                    break;
                case AsrService.ACTION_ERROR:
                    String error = intent.getStringExtra(AsrService.EXTRA_ERROR);
                    chatboxManager.addSystemText("ASR error: " + error);
                    stopListening();
                    break;

            }
        }
    };

    private BroadcastReceiver matcherReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!Matcher.ACTION_COMMAND_MATCHED.equals(intent.getAction())) {
                return;
            }

            String commandId = intent.getStringExtra(Matcher.EXTRA_COMMAND_ID);
            String commandText = intent.getStringExtra(Matcher.EXTRA_COMMAND_TEXT);
            float similarity = intent.getFloatExtra(Matcher.EXTRA_SIMILARITY, 0.0f);
            String originalText = intent.getStringExtra(Matcher.EXTRA_ORIGINAL_TEXT);

            if (commandId != null) {
                String label = commandText != null ? commandText : commandId;
                String output = String.format(Locale.US, "Matched Result: %s at %.2f%%", label, similarity * 100f);
                // chatboxManager.addSystemText(output);
                Log.i(TAG, output);
            } else {
                actor.closeCurrentAction();
                chatboxManager.addSystemText(Commands.NO_MATCHING_COMMAND);
                Log.i(TAG, "No matched for: " + originalText);
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
        chatboxManager = new ChatboxManager(findViewById(R.id.chat_messages));

        micBtn = findViewById(R.id.btn_microphone);
        micBtn.setOnClickListener(v -> toggleListening());

        // init actor
        actor = new Actor(this, chatboxManager);
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

    @Override
    protected void onResume() {
        super.onResume();

        if (!isListening) {
            startListening();
        }

        if (actor != null) {
            actor.retryCurrentAction();
        }
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

        if (chatboxManager != null) {
            try {
                chatboxManager.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing chatboxManager", e);
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
        if (actor != null) {
            actor.closeCurrentAction();
        }
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
