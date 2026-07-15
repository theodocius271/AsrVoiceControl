package com.top.asrdemo.utils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

public class BrightnessPermission {
    private static final String TAG = "Brightness Permission";

    private BrightnessPermission() {}

    public static boolean canWrite(Activity activity) {
        return Settings.System.canWrite(activity);
    }

    public static void requestIfNeeded(Activity activity) {
        if (canWrite(activity)) {
            return;
        }

        Toast.makeText(
                activity,
                "Allow Modify system settings to control brightness",
                Toast.LENGTH_LONG
        ).show();

        // Jump to permission control page
        Intent appSettings = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        appSettings.setData(Uri.parse("package:" + activity.getPackageName()));

        try {
            activity.startActivity(appSettings);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "App-specific write settings page is unavailable", e);
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS));
        }
    }
}
