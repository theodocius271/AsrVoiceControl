package com.top.asrdemo.commands;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.LongBuffer;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

public class Embedder {
    private static final String TAG = "Embedder";
    private static final String MODEL_PATH = "Embeddings/paraphrase_int8.onnx";
    private static final String TOKENIZER_PATH = "Embeddings/tokenizer.json";
    private static final int MAX_SEQ_LENGTH = 128;

    private OrtEnvironment env;
    private OrtSession session;
    private boolean isInitialized = false;

    private HuggingFaceTokenizer tokenizer;

    public boolean initialize(Context context) {
        try {
            env = OrtEnvironment.getEnvironment();

            // load model
            byte[] modelBytes = loadModelFromAssets(context.getAssets(), MODEL_PATH);
            session = env.createSession(modelBytes);

            // create tokenizer
            File tokenizerFile = new File(context.getFilesDir(), "tokenizer.json");
            try (InputStream is = context.getAssets().open(TOKENIZER_PATH);
                 FileOutputStream os = new FileOutputStream(tokenizerFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
            }
            tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(Paths.get(tokenizerFile.getAbsolutePath()))
                    .optMaxLength(MAX_SEQ_LENGTH)
                    .optTruncation(true)
                    .build();

            // Complete
            isInitialized = true;
            Log.i(TAG, "Embedder initialized successfully");

            warmUp();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to init embedder", e);
            return false;
        }
    }

    private byte[] loadModelFromAssets(AssetManager assetManager, String path) throws IOException {
        InputStream inputStream = assetManager.open(path);
        byte[] buffer = new byte[inputStream.available()];
        if (inputStream.read(buffer) <= 0) {
            Log.e(TAG, "No embedding model found");
        }
        inputStream.close();
        return buffer;
    }

    /**
     * Warm up the model to accelerate first formal embedding.
     */
    private void warmUp() {
        Log.i(TAG, "Starting warm-up inference...");
        long start = System.currentTimeMillis();
        // inference on a dummy
        float[] warmupEmbedding = embed("One Two Three");
        long elapsed = System.currentTimeMillis() - start;
        Log.i(TAG, String.format("Warm-up completed in %dms, embedding dim: %d",
                elapsed, warmupEmbedding != null ? warmupEmbedding.length : 0));
    }

    public float[] embed(String text) {
        if (!isInitialized) {
            Log.e(TAG, "Embed before init");
            return null;
        }

        try {
            Encoding encoding = tokenizer.encode(text);
            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();
            long[] tokenTypeIds = encoding.getTypeIds();

            if (inputIds.length != attentionMask.length || inputIds.length != tokenTypeIds.length) {
                throw new IllegalStateException("Tokenizer returned inconsistent input lengths");
            }

            long[] shape = {1, inputIds.length};
            try (OnnxTensor inputIdsTensor = OnnxTensor.createTensor(
                    env, LongBuffer.wrap(inputIds), shape);
                 OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(
                         env, LongBuffer.wrap(attentionMask), shape);
                 OnnxTensor tokenTypeIdsTensor = OnnxTensor.createTensor(
                         env, LongBuffer.wrap(tokenTypeIds), shape)) {
                Map<String, OnnxTensor> inputs = new HashMap<>();
                inputs.put("input_ids", inputIdsTensor);
                inputs.put("attention_mask", attentionMaskTensor);
                inputs.put("token_type_ids", tokenTypeIdsTensor);

                try (OrtSession.Result result = session.run(inputs)) {
                    float[][][] lastHiddenState = (float[][][]) result.get(0).getValue();
                    float[] vector = meanPool(lastHiddenState[0], attentionMask);
                    return normalize(vector);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Embed failed", e);
            return null;
        }
    }

    private float[] meanPool(float[][] tokenEmbeddings, long[] attentionMask) {
        if (tokenEmbeddings.length != attentionMask.length || tokenEmbeddings.length == 0) {
            throw new IllegalArgumentException("Invalid model output shape");
        }

        float[] pooled = new float[tokenEmbeddings[0].length];
        long tokenCount = 0;
        for (int token = 0; token < tokenEmbeddings.length; token++) {
            if (attentionMask[token] == 0) {
                continue;
            }
            tokenCount++;
            for (int dimension = 0; dimension < pooled.length; dimension++) {
                pooled[dimension] += tokenEmbeddings[token][dimension];
            }
        }

        if (tokenCount == 0) {
            throw new IllegalArgumentException("Cannot pool an empty token sequence");
        }
        for (int dimension = 0; dimension < pooled.length; dimension++) {
            pooled[dimension] /= tokenCount;
        }
        return pooled;
    }

    private float[] normalize(float[] vector) {
        float sum = 0;
        for (float v : vector) {
            sum += v * v;
        }
        float norm = (float) Math.sqrt(sum);

        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / (norm + 1e-8f);
        }
        return normalized;
    }


    public void release() {
        if (session != null) {
            try {
                session.close();
            } catch (OrtException e) {
                Log.e(TAG, "Failed to close session", e);
            }
        }
        isInitialized = false;
        Log.i(TAG, "Embedder released");
    }

    public boolean isInitialized() {
        return isInitialized;
    }

}
