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
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.top.asrdemo.asr.AsrManager;
import com.top.asrdemo.asr.AudioCapture;

import java.security.PrivateKey;

public class AsrService extends Service {

    private static final String TAG = "ASR Service";
    private static final String CHANNEL_ID = "VoiceControlChannel";

    // Action for controlling the service
    public static final String ACTION_START = "com.top.asrdemo.START";
    public static final String ACTION_STOP = "com.top.asrdemo.STOP";

    //Action for broadcasting results
    public static final String ACTION_PARTIAL_RESULT = "com.top.asrdemo.action.PARTIAL_RESULT";
    public static final String ACTION_FINAL_RESULT = "com.top.asrdemo.action.FINAL_RESULT";
    public static final String ACTION_ERROR = "com.top.asrdemo.action.ERROR";

    public static final String EXTRA_TEXT = "extra_text";
    public static final String EXTRA_ERROR = "extra_error";

    private AsrManager asrManager;
    private AudioCapture audioCapture;
    private String lastText = "";
    private boolean isRunning = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // ACTION_START: init & start
        if (!isRunning) {
            if (!startAsr()) {
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        return START_STICKY;
    }

    private boolean startAsr() {
        startForeground(1, createNotification());

        // init ASR
        asrManager = new AsrManager(getAssets());
        if (!asrManager.initialize()) {
            Log.e(TAG, "Failed to init ASR");
            broadcastError("Failed to init ASR model");
            return false;
        }

        // init audio capture & start
        audioCapture = new AudioCapture(this::onAudioData);
        audioCapture.start();
        isRunning = true;

        Log.i(TAG, "ASR service initialized and started");
        return true;
    }

    private Notification createNotification() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                TAG,
                NotificationManager.IMPORTANCE_HIGH
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
            lastText = "";
        } else if (!text.equals(lastText)) {
            Log.d(TAG, "Partial result: " + text);
            lastText = text;
            broadcastResult(ACTION_PARTIAL_RESULT, text);
        }
    }

    private void onFinalResult(String text) {
        broadcastResult(ACTION_FINAL_RESULT, text);
        // TODO: Send to command matcher
        Log.i(TAG, "Recognized: " + text);
    }

    @SuppressWarnings("deprecation")
    private void broadcastResult(String action, String text) {
        Intent intent = new Intent(action);
        intent.putExtra(EXTRA_TEXT, text);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @SuppressWarnings("deprecation")
    private void broadcastError(String error) {
        Intent intent = new Intent(ACTION_ERROR);
        intent.putExtra(EXTRA_ERROR, error);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;

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
