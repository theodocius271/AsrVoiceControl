package com.top.asrdemo.actions;

import android.app.Activity;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;

import com.top.asrdemo.utils.BrightnessPermission;
import com.top.asrdemo.utils.ChatboxManager;

import java.util.List;
import java.util.Locale;

import pub.devrel.easypermissions.EasyPermissions;

public class IncreaseBrightness implements Action {
    private static final String TAG = "IncreaseBrightness";
    private static final int MAX_BRIGHTNESS = 255;
    private static final int BRIGHTNESS_CHANGE = 50;

    private final Activity activity;
    private final ChatboxManager chatboxManager;
    private boolean applied;

    public IncreaseBrightness(Activity activity, ChatboxManager chatboxManager) {
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

        // Set manual update screen brightness
        boolean modeUpdated = Settings.System.putInt(
                activity.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        );

        int newBrightness = MAX_BRIGHTNESS;
        try {
            int currentBrightness = Settings.System.getInt(
                    activity.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS
            );
            if (currentBrightness >= MAX_BRIGHTNESS) {
                chatboxManager.addSystemText("亮度已经是最高了");
                return;
            }
            newBrightness = Math.min(currentBrightness + BRIGHTNESS_CHANGE, MAX_BRIGHTNESS);
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "SCREEN BRIGHTNESS settings not found", e);
        }

        // Set screen brightness to max
        boolean brightnessUpdated = Settings.System.putInt(
                activity.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS,
                newBrightness
        );
        chatboxManager.addSystemText(String.format(Locale.US, "已为您调高屏幕亮度, 当前亮度%d%%", (int) (newBrightness / 2.55)));

        if (!modeUpdated || !brightnessUpdated) {
            throw new IllegalStateException("Failed to write system brightness");
        }
        applied = true;
        Log.i(TAG, "System brightness increased");
    }

    @Override
    public void close() {}

}
