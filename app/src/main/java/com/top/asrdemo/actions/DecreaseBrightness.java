package com.top.asrdemo.actions;

import android.app.Activity;
import android.provider.Settings;
import android.util.Log;

import com.top.asrdemo.utils.BrightnessPermission;

public class DecreaseBrightness implements Action {
    private static final String TAG = "DecreaseBrightness";
    private static final int MIN_BRIGHTNESS = 5;

    private final Activity activity;
    private boolean applied;

    public DecreaseBrightness(Activity activity) {
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

        boolean modeUpdated = Settings.System.putInt(
                activity.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        );

        boolean brightnessUpdated = Settings.System.putInt(
                activity.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS,
                MIN_BRIGHTNESS
        );

        if (!modeUpdated || !brightnessUpdated) {
            throw new IllegalStateException("Failed to write minimum sys brightness");
        }
        applied = true;
        Log.i(TAG, "System brightness set to minimum");
    }

    @Override
    public void close() {
        // PASS
    }
}
