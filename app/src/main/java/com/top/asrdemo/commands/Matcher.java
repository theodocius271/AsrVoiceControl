package com.top.asrdemo.commands;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.top.asrdemo.service.AsrService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Matcher extends BroadcastReceiver {
    private static final String TAG = "Matcher";
    private static final float SIMILARITY_THRESHOLD = 0.4f; // mim similarity gate


    public static final String ACTION_COMMAND_MATCHED = "com.top.asrdemo.action.COMMAND_MATCHED";
    public static final String EXTRA_COMMAND_ID = "command_id";
    public static final String EXTRA_COMMAND_TEXT = "command_text";
    public static final String EXTRA_SIMILARITY = "similarity";
    public static final String EXTRA_ORIGINAL_TEXT = "original_text";

    private static Matcher instance;
    private Embedder embedder;
    private Context appContext;

    // Pre-defined commands with their embeddings
    private final Map<String, List<CommandEmbedding>> commandEmbeddings;
    private Map<String, String> commandTexts; // logging debugging

    private static final class CommandEmbedding {
        private final String commandText;
        private final float[] embedding;
        private CommandEmbedding(String commandText, float[] embedding) {
            this.commandText = commandText;
            this.embedding = embedding;
        }
    }

    private static final class MatchResult {
        private final String commandId;
        private final String commandText;
        private final float similarity;

        private MatchResult(String commandId, String commandText, float similarity) {
            this.commandText = commandText;
            this.commandId = commandId;
            this.similarity = similarity;
        }
    }

    private volatile boolean isInitialized = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private Matcher() {
        embedder = new Embedder();
        commandEmbeddings = new LinkedHashMap<>();
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
        commandEmbeddings.clear();

        Log.i(TAG, "Pre-computing embeddings for commands ...");
        long start = System.currentTimeMillis();
        int loadedPhraseCount = 0;

        for (Map.Entry<String, List<String>> command : Commands.all().entrySet()) {
            String commandId = command.getKey();
            List<CommandEmbedding> embeddings = new ArrayList<>();

            for (String commandText : command.getValue()) {
                float[] embedding = embedder.embed(commandText);
                if (embedding == null) {
                    Log.e(TAG, "Failed to embed command phrase: " + commandText);
                    continue;
                }

                embeddings.add(new CommandEmbedding(commandText, embedding));
                loadedPhraseCount++;
                Log.d(TAG, String.format(
                        "Loaded command phrase: %s (id=%s, dim=%d)", commandText, commandId, embedding.length
                ));
            }

            if (embeddings.isEmpty()) {
                Log.e(TAG, "No embeddings loaded for command: " + commandId);
            } else {
                commandEmbeddings.put(commandId, embeddings);
            }

        }

        long elapsed = System.currentTimeMillis() - start;
        Log.i(TAG, String.format("%d command phrase(s) loaded in %dms", loadedPhraseCount, elapsed));

    }

    private MatchResult findBestMatch(float[] inputEmbedding) {
        String bestCommandId = null;
        String bestCommandText = null;
        float bestSimilarity = 0.0f;

        for (Map.Entry<String, List<CommandEmbedding>> command : commandEmbeddings.entrySet()) {
            for (CommandEmbedding candidate : command.getValue()) {
                float similarity = cosineSimilarity(inputEmbedding, candidate.embedding);
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestCommandId = command.getKey();
                    bestCommandText = candidate.commandText;
                }
            }
        }

        return new MatchResult(bestCommandId, bestCommandText, bestSimilarity);
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
            broadcastMatchResult(null, null, 0.0f, text);
            return;
        }

        // Find best match
        MatchResult match = findBestMatch(inputEmbedding);
        long elapsed = System.currentTimeMillis() - start;

        // Check threshold
        if (match.similarity >= SIMILARITY_THRESHOLD && match.commandId != null) {
            Log.i(TAG, String.format("Match found: '%s' -> '%s' (score=%.3f, %dms)",
                    text, match.commandText, match.similarity, elapsed));
            broadcastMatchResult(match.commandId, match.commandText, match.similarity, text);
        } else {
            Log.i(TAG, String.format("No match above threshold (best=%.3f, %dms)",
                    match.similarity, elapsed));
            broadcastMatchResult(null, match.commandText, match.similarity, text);
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
    private void broadcastMatchResult(String commandId, String commandText, float similarity, String originalText) {
        Intent intent = new Intent(ACTION_COMMAND_MATCHED);
        intent.putExtra(EXTRA_COMMAND_ID, commandId);
        intent.putExtra(EXTRA_SIMILARITY, similarity);
        intent.putExtra(EXTRA_ORIGINAL_TEXT, originalText);
        intent.putExtra(EXTRA_COMMAND_TEXT, commandText);

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

        MatchResult res = findBestMatch(inputEmbedding);

        return (res.similarity >= SIMILARITY_THRESHOLD) ? res.commandId : null;
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
