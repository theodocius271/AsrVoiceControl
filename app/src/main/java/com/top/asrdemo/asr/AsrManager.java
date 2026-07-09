package com.top.asrdemo.asr;

import android.content.res.AssetManager;
import android.util.Log;

import com.k2fsa.sherpa.onnx.EndpointConfig;
import com.k2fsa.sherpa.onnx.EndpointRule;
import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizer;
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig;

public class AsrManager {
    private static final String TAG = "ASR Manager";
    private static final String MODEL_DIR = "zipformer-bilingual-20230220";

    private OnlineRecognizer recognizer;
    private OnlineStream stream;
    private AssetManager assetManager;

    public interface RecognitionCallback {
        void onPartialResult(String text);
        void onFinalResult(String text);
        void onError(String error);
    }

    public AsrManager(AssetManager manager) {
        this.assetManager = manager;
    }

    public boolean initialize() {
        try {
            // Transducer model
            OnlineTransducerModelConfig transducerConfig = new OnlineTransducerModelConfig();
            transducerConfig.setEncoder(MODEL_DIR + "/encoder-epoch-99-avg-1.int8.onnx");
            transducerConfig.setDecoder(MODEL_DIR + "/decoder-epoch-99-avg-1.onnx");
            transducerConfig.setJoiner(MODEL_DIR + "/joiner-epoch-99-avg-1.int8.onnx");

            // Configure model
            OnlineModelConfig modelConfig = new OnlineModelConfig();
            modelConfig.setTransducer(transducerConfig);
            modelConfig.setTokens(MODEL_DIR + "/tokens.txt");
            modelConfig.setNumThreads(4);
            modelConfig.setProvider("cpu");
            modelConfig.setModelType("zipformer");
            modelConfig.setDebug(false);

            // recognizer
            OnlineRecognizerConfig recognizerConfig = new OnlineRecognizerConfig();
            recognizerConfig.setModelConfig(modelConfig);
            recognizerConfig.setEnableEndpoint(true);

            // Endpoint configuration
            EndpointConfig endpointConfig = new EndpointConfig();
            endpointConfig.setRule1(new EndpointRule(false, 2.4f, 0.0f)); // 2.4s trailing silence
            endpointConfig.setRule2(new EndpointRule(true, 1.4f, 0.0f)); // 1.4s with speech
            endpointConfig.setRule3(new EndpointRule(false, 0.0f, 20.0f)); // max 20s utterance
            recognizerConfig.setEndpointConfig(endpointConfig);

            // Create recognizer
            recognizer = new OnlineRecognizer(assetManager, recognizerConfig);
            stream = recognizer.createStream("");

            Log.i(TAG, "ASR init successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to init ASR", e);
            return false;
        }
    }

    public void acceptWaveform(float[] samples, int sampleRate) {
        if (stream != null) {
            stream.acceptWaveform(samples, sampleRate);;
        }
    }

    public void decode() {
        if (recognizer != null && stream != null) {
            while (recognizer.isReady(stream)) {
                recognizer.decode(stream);
            }
        }
    }

    public String getResult() {
        if (recognizer != null && stream != null) {
            return recognizer.getResult(stream).getText();
        }
        return "";
    }

    public boolean isEndpoint() {
        if (recognizer != null && stream != null) {
            return recognizer.isEndpoint(stream);
        }
        return false;
    }

    public void reset() {
        if (recognizer != null && stream != null) {
            recognizer.reset(stream);
        }
    }

    public void release() {
        if (stream != null) {
            stream.release();
            stream = null;
        }
        if (recognizer != null) {
            recognizer.release();
            recognizer = null;
        }
    }

}
