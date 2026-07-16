package com.top.asrdemo.actions;

import android.app.Activity;
import android.provider.Settings;
import android.util.Log;

import com.top.asrdemo.utils.BrightnessPermission;
import com.top.asrdemo.utils.ChatboxManager;

import java.util.Locale;

public class DecreaseBrightness implements Action {
    private static final String TAG = "DecreaseBrightness";
    private static final int MIN_BRIGHTNESS = 5;
    private static final int BRIGHTNESS_CHANGE = 50;

    private final Activity activity;
    private final ChatboxManager chatboxManager;
    private boolean applied;

    public DecreaseBrightness(Activity activity, ChatboxManager chatboxManager) {
        this.activity = activity;
        BrightnessPermission.requestIfNeeded(activity);
        this.chatboxManager = chatboxManager;
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

        int newBrightness = MIN_BRIGHTNESS;
        try {
            int currentBrightness = Settings.System.getInt(
                    activity.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS
            );
            newBrightness = Math.max(currentBrightness - BRIGHTNESS_CHANGE, MIN_BRIGHTNESS);
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "SCREEN BRIGHTNESS settings not found", e);
        }

        // Set screen brightness to min
        boolean brightnessUpdated = Settings.System.putInt(
                activity.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS,
                newBrightness
        );
        chatboxManager.addSystemText(String.format(Locale.US, "Screen brightness decreased to %d.", newBrightness));
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
