package com.top.asrdemo.asr;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class AudioCapture {
    private static String TAG = "Audio Capture";
    private static final int SAMPLE_RATE = 16000; // 16kHz required by ASR
    private static final float CHUNKING_TIME = 0.1f; // 100ms chunking
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord audioRecord;
    private Thread captureThread;
    private volatile boolean isCapturing = false;

    public interface AudioCallback {
        void onAudioData(float[] samples, int sampleRate);
    }

    private AudioCallback callback;

    public AudioCapture(AudioCallback callback) {
        this.callback = callback;
    }

    @SuppressLint("MissingPermission")
    public void start() {
        try {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize * 2
            );

            audioRecord.startRecording();
            isCapturing = true;

            captureThread = new Thread(this::captureLoop);
            captureThread.start();

            Log.i(TAG, "Audio capture started");

        } catch (Exception e) {
            Log.e(TAG, "AudioCapture.start failed", e);
        }
    }

    private void captureLoop() {
        // process audio in 100ms chunks
        int chunkSize = (int) (CHUNKING_TIME * SAMPLE_RATE);
        short[] buffer = new short[chunkSize];

        while (isCapturing) {
            int samplesRead = audioRecord.read(buffer, 0, buffer.length);

            if (samplesRead > 0) {
                // int16 -> float32 [-1, 1]
                float[] samples = new float[samplesRead];
                for (int i = 0; i < samplesRead; i++) {
                    samples[i] = buffer[i] / 32768.0f;
                }

                if (callback != null) {
                    callback.onAudioData(samples, SAMPLE_RATE);
                }
            }
        }
    }

    public void stop() {
        isCapturing = false;

        if (captureThread != null) {
            try {
                captureThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }

        Log.i(TAG, "Audio Capture stopped");
    }
}


















