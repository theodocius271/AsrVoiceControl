package com.top.asrdemo.actions;

import android.app.Activity;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;

import com.top.asrdemo.utils.BrightnessPermission;

import java.util.List;

import pub.devrel.easypermissions.EasyPermissions;

public class IncreaseBrightness implements Action {
    private static final String TAG = "IncreaseBrightness";
    private static final int MAX_BRIGHTNESS = 255;

    private final Activity activity;
    private boolean applied;

    public IncreaseBrightness(Activity activity) {
        this.activity = activity;
        BrightnessPermission.requestIfNeeded(activity);
    }

    @Override
    public void run() {
        if (applied) {
            return;
        }

        if (!BrightnessPermission.canWrite(activity)) {
            Log.i(TAG, "Waiting for WRITE_SETTINGS permission");
            return;
        }

        // Set manual update screen brightness
        boolean modeUpdated = Settings.System.putInt(
                activity.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        );
        // Set screen brightness to max
        boolean brightnessUpdated = Settings.System.putInt(
                activity.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS,
                MAX_BRIGHTNESS
        );

        if (!modeUpdated || !brightnessUpdated) {
            throw new IllegalStateException("Failed to write system brightness");
        }
        applied = true;
        Log.i(TAG, "System brightness set to maximum");
    }

    @Override
    public void close() {}

}
