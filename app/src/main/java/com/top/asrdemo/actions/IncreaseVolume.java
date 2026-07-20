package com.top.asrdemo.actions;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import com.top.asrdemo.utils.ChatboxManager;

import java.util.Locale;

public class IncreaseVolume implements Action {
    private static final String TAG = "IncreaseVolume";
    private static final int STREAM_TYPE = AudioManager.STREAM_MUSIC;

    private final AudioManager audioManager;
    private final ChatboxManager chatboxManager;
    private boolean applied;

    public IncreaseVolume(Context context, ChatboxManager chatboxManager) {
        audioManager = (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        this.chatboxManager = chatboxManager;
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
        int minimum = audioManager.getStreamMinVolume(STREAM_TYPE);
        int current = audioManager.getStreamVolume(STREAM_TYPE);
        if (current >= maximum) {
            chatboxManager.addSystemText("音量已经是最大了");
            return;
        }

        int next = Math.min(maximum, (int) (current + (maximum - minimum) / 5));
        audioManager.setStreamVolume(
                STREAM_TYPE,
                next,
                AudioManager.FLAG_SHOW_UI
        );
        chatboxManager.addSystemText(String.format(Locale.US, "已为您调大音量, 当前音量%d%%",
                (int) (((float) (next - minimum) / (float) (maximum - minimum)) * 100)));
        applied = true;
        Log.i(TAG, "Media volume increased: " + next);
    }

    @Override
    public void close() {}
}
