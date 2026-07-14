package com.top.asrdemo.commands;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.top.asrdemo.service.AsrService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Matcher extends BroadcastReceiver {
    private static final String TAG = "Matcher";
    private static final float SIMILARITY_THRESHOLD = 0.7f; // mim similarity gate

    private static Matcher instance;
    private Embedder embedder;
    private Context appContext;

    // Pre-defined commands with their embeddings
    private Map<String, float[]> commandEmbeddings;
    private Map<String, String> commandTexts; // logging debugging

    private volatile boolean isInitialized = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private Matcher() {
        embedder = new Embedder();
        commandEmbeddings = new HashMap<>();
        commandTexts = new HashMap<>();
    }
    public static synchronized Matcher getInstance() {
        if (instance == null) {
            instance = new Matcher();
        }
        return instance;
    }

    /**
     * Initialize matcher with application context
     * Must be called before use (e.g., in Application.onCreate or MainActivity.onCreate)
     */
    public boolean initialize(Context context) {
        if (isInitialized) {
            Log.w(TAG, "Matcher already initialized");
            return true;
        }

        appContext = context.getApplicationContext();

        executor.execute(() -> {
            if (!embedder.initialize(appContext)) {
                Log.e(TAG, "Failed to initialize embedder");
                return;
            }
            loadCommands();
            registerReceiver();
            isInitialized = true;
            Log.i(TAG, "Matcher initialized with " + commandEmbeddings.size() + " commands");
        });

        return true;
    }

    /**
     * Load and embed all pre-defined commands
     */
    private void loadCommands() {
        // TODO: Load commands from config file or database
        // For now, use hardcoded examples
        String[] commands = {
                "Greet"
        };

        Log.i(TAG, "Pre-computing embeddings for " + commands.length + " commands...");
        long start = System.currentTimeMillis();

        for (int i = 0; i < commands.length; i++) {
            String cmd = commands[i];
            float[] embedding = embedder.embed(cmd);

            if (embedding != null) {
                String cmdId = "cmd_" + i;
                commandEmbeddings.put(cmdId, embedding);
                commandTexts.put(cmdId, cmd);
                Log.d(TAG, String.format("Loaded command: %s (id=%s, dim=%d)",
                        cmd, cmdId, embedding.length));
            } else {
                Log.e(TAG, "Failed to embed command: " + cmd);
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        Log.i(TAG, String.format("Command loading completed in %dms", elapsed));
    }

    @SuppressWarnings("deprecation")
    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(AsrService.ACTION_FINAL_RESULT);
        LocalBroadcastManager.getInstance(appContext).registerReceiver(this, filter);
        Log.i(TAG, "Registered for ACTION_FINAL_RESULT broadcasts");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if (AsrService.ACTION_FINAL_RESULT.equals(action)) {
            String text = intent.getStringExtra(AsrService.EXTRA_TEXT);
            if (text != null && !text.trim().isEmpty()) {
                executor.execute(() -> handleFinalResult(text));
            }
        }
    }

    /**
     * Process final ASR result and find best matching command
     */
    private void handleFinalResult(String text) {
        Log.i(TAG, "Received final result: " + text);

        long start = System.currentTimeMillis();

        // Compute embedding for user input
        float[] inputEmbedding = embedder.embed(text);
        if (inputEmbedding == null) {
            Log.e(TAG, "Failed to embed input text");
            broadcastMatchResult(null, 0.0f, text);
            return;
        }

        // Find best match
        String bestCommandId = null;
        float bestSimilarity = 0.0f;

        for (Map.Entry<String, float[]> entry : commandEmbeddings.entrySet()) {
            float similarity = cosineSimilarity(inputEmbedding, entry.getValue());

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestCommandId = entry.getKey();
            }
        }

        long elapsed = System.currentTimeMillis() - start;

        // Check threshold
        if (bestSimilarity >= SIMILARITY_THRESHOLD && bestCommandId != null) {
            String matchedText = commandTexts.get(bestCommandId);
            Log.i(TAG, String.format("Match found: '%s' -> '%s' (score=%.3f, %dms)",
                    text, matchedText, bestSimilarity, elapsed));
            broadcastMatchResult(bestCommandId, bestSimilarity, text);
        } else {
            Log.i(TAG, String.format("No match above threshold (best=%.3f, %dms)",
                    bestSimilarity, elapsed));
            broadcastMatchResult(null, bestSimilarity, text);
        }
    }

    private float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            Log.e(TAG, "Vector dimension mismatch");
            return 0.0f;
        }

        float dotProduct = 0.0f;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
        }

        // Vectors are already normalized, so dot product = cosine similarity
        return dotProduct;
    }

    @SuppressWarnings("deprecation")
    private void broadcastMatchResult(String commandId, float similarity, String originalText) {
        Intent intent = new Intent("com.top.asrdemo.action.COMMAND_MATCHED");
        intent.putExtra("command_id", commandId);
        intent.putExtra("similarity", similarity);
        intent.putExtra("original_text", originalText);

        if (commandId != null) {
            intent.putExtra("command_text", commandTexts.get(commandId));
        }

        LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent);
    }

    /**
     * Manually match text (for testing)
     */
    public String match(String text) {
        if (!isInitialized) {
            Log.e(TAG, "Matcher not initialized");
            return null;
        }

        float[] inputEmbedding = embedder.embed(text);
        if (inputEmbedding == null) {
            return null;
        }

        String bestCommandId = null;
        float bestSimilarity = 0.0f;

        for (Map.Entry<String, float[]> entry : commandEmbeddings.entrySet()) {
            float similarity = cosineSimilarity(inputEmbedding, entry.getValue());
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestCommandId = entry.getKey();
            }
        }

        return (bestSimilarity >= SIMILARITY_THRESHOLD) ? bestCommandId : null;
    }

    /**
     * Release resources
     */
    @SuppressWarnings("deprecation")
    public void release() {
        if (appContext != null) {
            LocalBroadcastManager.getInstance(appContext).unregisterReceiver(this);
        }

        if (embedder != null) {
            embedder.release();
        }

        commandEmbeddings.clear();
        commandTexts.clear();
        isInitialized = false;

        Log.i(TAG, "Matcher released");
    }

    public boolean isInitialized() {
        return isInitialized;
    }
}
