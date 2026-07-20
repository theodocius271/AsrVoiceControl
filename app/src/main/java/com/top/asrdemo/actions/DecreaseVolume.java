package com.top.asrdemo.actions;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import com.top.asrdemo.utils.ChatboxManager;

import java.util.Locale;

public class DecreaseVolume implements Action {
    private static final String TAG = "DecreaseVolume";
    private static final int STREAM_TYPE = AudioManager.STREAM_MUSIC;

    private final AudioManager audioManager;
    private final ChatboxManager chatboxManager;
    private boolean applied;

    public DecreaseVolume(Context context, ChatboxManager chatboxManager) {
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

        int minimum = audioManager.getStreamMinVolume(STREAM_TYPE);
        int maximum = audioManager.getStreamMaxVolume(STREAM_TYPE);
        int current = audioManager.getStreamVolume(STREAM_TYPE);
        if (current <= minimum) {
            chatboxManager.addSystemText("音量已经是最小了");
            return;
        }

        int next = Math.max(minimum, (int) (current - (maximum - minimum) / 5));
        audioManager.setStreamVolume(
                STREAM_TYPE,
                next,
                AudioManager.FLAG_SHOW_UI
        );
        chatboxManager.addSystemText(String.format(Locale.US, "已为您调小音量, 当前音量%d%%",
                (int) (((float) (next - minimum) / (float) (maximum - minimum)) * 100)));
        applied = true;
        Log.i(TAG, "Media volume decreased: " + next);
    }

    @Override
    public void close() {}
}
