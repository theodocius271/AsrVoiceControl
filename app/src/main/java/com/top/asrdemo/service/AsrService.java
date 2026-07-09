package com.top.asrdemo.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.top.asrdemo.asr.AsrManager;
import com.top.asrdemo.asr.AudioCapture;

public class AsrService extends Service {

    private static final String TAG = "ASR Service";
    private static final String CHANNEL_ID = "VoiceControlChannel";

    private AsrManager asrManager;
    private AudioCapture audioCapture;
    private String lastText;

    @Override
    public void onCreate() {
        super.onCreate();

        // init ASR
        asrManager = new AsrManager(getAssets());
        if (!asrManager.initialize()) {
            Log.e(TAG, "Failed to init ASR");
            stopSelf();
            return;
        }
        // init audio capture
        audioCapture = new AudioCapture(this::onAudioData);
        // start foreground service
        startForeground(1, createNotification());
        // start capturing
        audioCapture.start();

        Log.i(TAG, "ASR Service created and started");
    }

    private Notification createNotification() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                TAG,
                NotificationManager.IMPORTANCE_LOW
        );

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Voice Control Active")
                .setContentText("Listening for commands...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build();
    }

    private void onAudioData(float[] samples, int sampleRate) {
        // audio >> ASR
        asrManager.acceptWaveform(samples, sampleRate);
        asrManager.decode();
        // get current result
        String text = asrManager.getResult();

        // check for endpoint
        if (asrManager.isEndpoint()) {
            // add tail padding for better endpoint detection
            float[] padding = new float[(int) (0.8 * sampleRate)]; // 800ms padding
            asrManager.acceptWaveform(padding, sampleRate);
            asrManager.decode();

            text = asrManager.getResult();

            if (!text.isEmpty()) {
                Log.i(TAG, "Final result: " + text);
                onFinalResult(text);
            }
            asrManager.reset();
        } else if (!text.equals(lastText)) {
            Log.d(TAG, "Partial result: " + text);
            lastText = text;
        }
    }

    private void onFinalResult(String text) {
        // TODO: Send to command matcher
        Log.i(TAG, "Recognized: " + text);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (audioCapture != null) {
            audioCapture.stop();
        }
        if (asrManager != null) {
            asrManager.release();
        }

        Log.i(TAG, "ASR service stopped");
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
