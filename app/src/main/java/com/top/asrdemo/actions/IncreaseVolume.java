package com.top.asrdemo.actions;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

public class IncreaseVolume implements Action {
    private static final String TAG = "IncreaseVolume";
    private static final int STREAM_TYPE = AudioManager.STREAM_MUSIC;

    private final AudioManager audioManager;
    private boolean applied;

    public IncreaseVolume(Context context) {
        audioManager = (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            throw new IllegalStateException("AudioManager is unavailable");
        }
    }

    @Override
    public void run() {
        if (applied) {
            return;
        }

        if (audioManager.isVolumeFixed()) {
            throw new IllegalStateException("Volume is fixed");
        }

        int maximum = audioManager.getStreamMaxVolume(STREAM_TYPE);
        audioManager.setStreamVolume(
                STREAM_TYPE,
                maximum,
                AudioManager.FLAG_SHOW_UI
        );
        applied = true;
        Log.i(TAG, "Media volume set to maximum: " + maximum);
    }

    @Override
    public void close() {}
}
