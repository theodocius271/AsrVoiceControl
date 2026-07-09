package com.top.asrdemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.top.asrdemo.service.AsrService;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "VoiceControl";
    private static final int REQUEST_ASR_PERMISSIONS = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        EdgeToEdge.enable(this);
//        setContentView(R.layout.activity_main);
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
//            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            return insets;
//        });

        startAsrServiceWithPermissions();
    }

    private void startAsrServiceWithPermissions() {
        List<String> missingPermissions = new ArrayList<>();

        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            missingPermissions.add(Manifest.permission.RECORD_AUDIO);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (missingPermissions.isEmpty()) {
            startAsrService();
            return;
        }

        ActivityCompat.requestPermissions(
                this,
                missingPermissions.toArray(new String[0]),
                REQUEST_ASR_PERMISSIONS
        );
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startAsrService() {
        Intent intent = new Intent(this, AsrService.class);
        try {
            ContextCompat.startForegroundService(this, intent);
            Log.i(TAG, "ASR service start requested");
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to start ASR service", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_ASR_PERMISSIONS) {
            return;
        }

        if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                Log.w(TAG, "POST_NOTIFICATIONS permission denied; foreground service notification may be hidden");
            }
            startAsrService();
        } else {
            Log.w(TAG, "RECORD_AUDIO permission denied; ASR service was not started");
        }
    }
}
