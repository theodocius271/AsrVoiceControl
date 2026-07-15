# Chapter 2: Command Catalog and Volume Actions

## Goal

This guide makes two related changes:

1. Move command IDs and command phrases out of `Matcher` and into `commands.Commands`.
2. Add `IncreaseVolume` and `DecreaseVolume` actions.

Each command ID is mapped to several phrases. `Matcher` embeds every phrase at initialization and
matches recognized speech against every resulting embedding. This gives one action several semantic
examples without duplicating action-routing code.

Android has separate media, ring, alarm, notification, and call volume streams. The generic
"increase volume" and "decrease volume" commands in this guide control
`AudioManager.STREAM_MUSIC`, the device's media volume.

The resulting ownership is:

```text
Commands.java
    -> stable command IDs
    -> multiple phrases for each ID

Matcher.java
    -> embeds every configured phrase
    -> finds the best phrase across all command IDs
    -> broadcasts the winning command ID and phrase

Actor.java
    -> maps the winning command ID to an Action
```

## Step 1: Declare Audio Settings Permission

Add this permission near the other permissions in `app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

`MODIFY_AUDIO_SETTINGS` is a normal install-time permission. Do not add it to
`MainActivity.getRequiredPermissions()` and do not request it with `EasyPermissions`.

## Step 2: Create Commands.java

Create `app/src/main/java/com/top/asrdemo/commands/Commands.java`:

```java
package com.top.asrdemo.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Static command IDs and the phrases used to recognize each command. */
public final class Commands {
    public static final String COMMAND_GREET = "greet";
    public static final String COMMAND_INCREASE_BRIGHTNESS = "increase_brightness";
    public static final String COMMAND_DECREASE_BRIGHTNESS = "decrease_brightness";
    public static final String COMMAND_INCREASE_VOLUME = "increase_volume";
    public static final String COMMAND_DECREASE_VOLUME = "decrease_volume";

    public static final String TEXT_GREET = "Greet";
    public static final String TEXT_SAY_HELLO = "Say hello";
    public static final String TEXT_HELLO = "Hello";

    public static final String TEXT_INCREASE_BRIGHTNESS = "Increase the brightness";
    public static final String TEXT_TURN_UP_BRIGHTNESS = "Turn up the brightness";
    public static final String TEXT_MAKE_SCREEN_BRIGHTER = "Make the screen brighter";

    public static final String TEXT_DECREASE_BRIGHTNESS = "Decrease the brightness";
    public static final String TEXT_TURN_DOWN_BRIGHTNESS = "Turn down the brightness";
    public static final String TEXT_MAKE_SCREEN_DARKER = "Make the screen darker";

    public static final String TEXT_INCREASE_VOLUME = "Increase the volume";
    public static final String TEXT_TURN_UP_VOLUME = "Turn up the volume";
    public static final String TEXT_MAKE_IT_LOUDER = "Make it louder";

    public static final String TEXT_DECREASE_VOLUME = "Decrease the volume";
    public static final String TEXT_TURN_DOWN_VOLUME = "Turn down the volume";
    public static final String TEXT_MAKE_IT_QUIETER = "Make it quieter";

    private static final Map<String, List<String>> COMMAND_TEXTS;

    static {
        Map<String, List<String>> commands = new LinkedHashMap<>();

        commands.put(COMMAND_GREET, texts(
                TEXT_GREET,
                TEXT_SAY_HELLO,
                TEXT_HELLO));

        commands.put(COMMAND_INCREASE_BRIGHTNESS, texts(
                TEXT_INCREASE_BRIGHTNESS,
                TEXT_TURN_UP_BRIGHTNESS,
                TEXT_MAKE_SCREEN_BRIGHTER));

        commands.put(COMMAND_DECREASE_BRIGHTNESS, texts(
                TEXT_DECREASE_BRIGHTNESS,
                TEXT_TURN_DOWN_BRIGHTNESS,
                TEXT_MAKE_SCREEN_DARKER));

        commands.put(COMMAND_INCREASE_VOLUME, texts(
                TEXT_INCREASE_VOLUME,
                TEXT_TURN_UP_VOLUME,
                TEXT_MAKE_IT_LOUDER));

        commands.put(COMMAND_DECREASE_VOLUME, texts(
                TEXT_DECREASE_VOLUME,
                TEXT_TURN_DOWN_VOLUME,
                TEXT_MAKE_IT_QUIETER));

        COMMAND_TEXTS = Collections.unmodifiableMap(commands);
    }

    private Commands() {
    }

    public static Map<String, List<String>> all() {
        return COMMAND_TEXTS;
    }

    public static List<String> textsFor(String commandId) {
        List<String> commandTexts = COMMAND_TEXTS.get(commandId);
        return commandTexts != null ? commandTexts : Collections.emptyList();
    }

    private static List<String> texts(String... values) {
        return Collections.unmodifiableList(Arrays.asList(values));
    }
}
```

`LinkedHashMap` gives deterministic initialization and log order. The map and each phrase list are
read-only after construction, so `Matcher` cannot accidentally modify the command catalog.

Command IDs are stable machine-facing values. Command texts are recognition examples and can be
expanded or tuned without changing `Actor`.

## Step 3: Change Matcher's Embedding Model

Remove these constants from `Matcher.java`:

```java
public static final String COMMAND_GREET = "greet";
public static final String COMMAND_INCREASE_BRIGHTNESS = "increase_brightness";
public static final String COMMAND_DECREASE_BRIGHTNESS = "decrease_brightness";
```

Keep the broadcast action and extra constants in `Matcher`; they describe the match-result protocol,
not the command catalog.

Update the collection imports in `Matcher.java`:

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
```

Replace the current `commandEmbeddings` and `commandTexts` fields with one map:

```java
private final Map<String, List<CommandEmbedding>> commandEmbeddings;
```

Replace the corresponding constructor initialization:

```java
private Matcher() {
    embedder = new Embedder();
    commandEmbeddings = new LinkedHashMap<>();
}
```

Add these two private value classes inside `Matcher`. `CommandEmbedding` keeps a phrase paired with
its successfully computed embedding; this avoids index mismatches if one phrase fails to embed.

```java
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
        this.commandId = commandId;
        this.commandText = commandText;
        this.similarity = similarity;
    }
}
```

## Step 4: Load Every Phrase from Commands

Replace `Matcher.loadCommands()` and remove the old single-command `addCommand()` helper:

```java
private void loadCommands() {
    commandEmbeddings.clear();

    Log.i(TAG, "Pre-computing command embeddings...");
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
                    "Loaded command phrase: %s (id=%s, dim=%d)",
                    commandText,
                    commandId,
                    embedding.length));
        }

        if (embeddings.isEmpty()) {
            Log.e(TAG, "No embeddings loaded for command: " + commandId);
        } else {
            commandEmbeddings.put(commandId, embeddings);
        }
    }

    long elapsed = System.currentTimeMillis() - start;
    Log.i(TAG, String.format(
            "Loaded %d command IDs and %d phrases in %dms",
            commandEmbeddings.size(),
            loadedPhraseCount,
            elapsed));
}
```

Initialization now computes three embeddings per command with the sample catalog above. This costs
more once during startup but does not add model inference during action execution.

## Step 5: Match Across All Command Phrases

Add this helper to `Matcher.java`:

```java
private MatchResult findBestMatch(float[] inputEmbedding) {
    String bestCommandId = null;
    String bestCommandText = null;
    float bestSimilarity = 0.0f;

    for (Map.Entry<String, List<CommandEmbedding>> command
            : commandEmbeddings.entrySet()) {
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
```

Replace `handleFinalResult()` with:

```java
private void handleFinalResult(String text) {
    Log.i(TAG, "Received final result: " + text);
    long start = System.currentTimeMillis();

    float[] inputEmbedding = embedder.embed(text);
    if (inputEmbedding == null) {
        Log.e(TAG, "Failed to embed input text");
        broadcastMatchResult(null, null, 0.0f, text);
        return;
    }

    MatchResult match = findBestMatch(inputEmbedding);
    long elapsed = System.currentTimeMillis() - start;

    if (match.commandId != null && match.similarity >= SIMILARITY_THRESHOLD) {
        Log.i(TAG, String.format(
                "Match found: '%s' -> '%s' [%s] (score=%.3f, %dms)",
                text,
                match.commandText,
                match.commandId,
                match.similarity,
                elapsed));
        broadcastMatchResult(
                match.commandId,
                match.commandText,
                match.similarity,
                text);
    } else {
        Log.i(TAG, String.format(
                "No match above threshold (best=%.3f, %dms)",
                match.similarity,
                elapsed));
        broadcastMatchResult(null, null, match.similarity, text);
    }
}
```

Replace `broadcastMatchResult()` with this version. It now receives the exact phrase that produced
the highest similarity instead of looking up one text per command ID:

```java
@SuppressWarnings("deprecation")
private void broadcastMatchResult(
        String commandId,
        String commandText,
        float similarity,
        String originalText) {
    Intent intent = new Intent(ACTION_COMMAND_MATCHED);
    intent.putExtra(EXTRA_COMMAND_ID, commandId);
    intent.putExtra(EXTRA_SIMILARITY, similarity);
    intent.putExtra(EXTRA_ORIGINAL_TEXT, originalText);

    if (commandId != null) {
        intent.putExtra(EXTRA_COMMAND_TEXT, commandText);
    }

    LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent);
}
```

Finally, replace the body of `match()` after the initialization check:

```java
float[] inputEmbedding = embedder.embed(text);
if (inputEmbedding == null) {
    return null;
}

MatchResult match = findBestMatch(inputEmbedding);
return match.commandId != null && match.similarity >= SIMILARITY_THRESHOLD
        ? match.commandId
        : null;
```

Remove `commandTexts.clear()` from `release()`. The command-text map no longer exists;
`commandEmbeddings.clear()` is sufficient.

## Step 6: Implement IncreaseVolume

Create `app/src/main/java/com/top/asrdemo/actions/IncreaseVolume.java`:

```java
package com.top.asrdemo.actions;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

public final class IncreaseVolume implements Action {
    private static final String TAG = "IncreaseVolume";
    private static final int STREAM_TYPE = AudioManager.STREAM_MUSIC;

    private final AudioManager audioManager;
    private boolean applied;

    public IncreaseVolume(Context context) {
        audioManager = (AudioManager) context.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
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
            throw new IllegalStateException("Volume is fixed on this device");
        }

        int maximum = audioManager.getStreamMaxVolume(STREAM_TYPE);
        audioManager.setStreamVolume(
                STREAM_TYPE,
                maximum,
                AudioManager.FLAG_SHOW_UI);

        applied = true;
        Log.i(TAG, "Media volume set to maximum: " + maximum);
    }

    @Override
    public void close() {
        // Volume is a persistent system setting. Closing this action does not restore it.
    }
}
```

## Step 7: Implement DecreaseVolume

Create `app/src/main/java/com/top/asrdemo/actions/DecreaseVolume.java`:

```java
package com.top.asrdemo.actions;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

public final class DecreaseVolume implements Action {
    private static final String TAG = "DecreaseVolume";
    private static final int STREAM_TYPE = AudioManager.STREAM_MUSIC;

    private final AudioManager audioManager;
    private boolean applied;

    public DecreaseVolume(Context context) {
        audioManager = (AudioManager) context.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
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
            throw new IllegalStateException("Volume is fixed on this device");
        }

        int minimum = audioManager.getStreamMinVolume(STREAM_TYPE);
        audioManager.setStreamVolume(
                STREAM_TYPE,
                minimum,
                AudioManager.FLAG_SHOW_UI);

        applied = true;
        Log.i(TAG, "Media volume set to minimum: " + minimum);
    }

    @Override
    public void close() {
        // Volume is a persistent system setting. Closing this action does not restore it.
    }
}
```

The project has `minSdk 28`, so `getStreamMinVolume()` is available. Using it is preferable to
assuming every stream's minimum is zero.

Like the brightness actions, volume actions change a persistent setting. Their `close()` methods are
intentionally no-ops so a later command does not undo the selected volume.

Android's safe-media-volume policy may cap the effective maximum or require user confirmation on
some devices. `setStreamVolume()` respects that system policy; the action must not try to bypass it.

## Step 8: Route Commands in Actor

Add this import to `Actor.java`:

```java
import com.top.asrdemo.commands.Commands;
```

Replace `createAction()` so every command ID comes from the new catalog:

```java
private Action createAction(String commandId) {
    switch (commandId) {
        case Commands.COMMAND_GREET:
            return new Greet(actionHost);
        case Commands.COMMAND_INCREASE_BRIGHTNESS:
            return new IncreaseBrightness(activity);
        case Commands.COMMAND_DECREASE_BRIGHTNESS:
            return new DecreaseBrightness(activity);
        case Commands.COMMAND_INCREASE_VOLUME:
            return new IncreaseVolume(activity);
        case Commands.COMMAND_DECREASE_VOLUME:
            return new DecreaseVolume(activity);
        default:
            return null;
    }
}
```

Keep the `Matcher` import because `Actor` still uses `Matcher.ACTION_COMMAND_MATCHED` and
`Matcher.EXTRA_COMMAND_ID` for the broadcast protocol.

Search for any remaining command-ID references under `Matcher` and migrate them to `Commands`:

```bash
rg 'Matcher\.COMMAND_' app/src/main/java
```

The command should return no matches after the migration.

## Step 9: Build and Verify

Build and install the application:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Test several phrases for the same command ID:

1. Say "increase the volume", "turn up the volume", and "make it louder".
2. Confirm that all three launch `IncreaseVolume` and move media volume to its maximum.
3. Say "decrease the volume", "turn down the volume", and "make it quieter".
4. Confirm that all three launch `DecreaseVolume` and move media volume to its minimum.
5. Repeat the brightness and greet commands to confirm that moving their IDs into `Commands` did not
   change action routing.

Inspect the media stream from ADB. Stream `3` is `STREAM_MUSIC`:

```bash
adb shell media volume --stream 3 --get
```

Useful logs:

```bash
adb logcat -s Matcher Actor IncreaseVolume DecreaseVolume
```

At startup, `Matcher` should report five command IDs and fifteen loaded phrases. Match logs should
show the recognized text, the exact catalog phrase that won, the stable command ID, and the
similarity score.

## Adding Future Commands

To add another command after this refactor:

1. Add one stable `COMMAND_*` ID to `Commands`.
2. Add several `TEXT_*` examples and map them to that ID in `COMMAND_TEXTS`.
3. Implement the new `Action`.
4. Add one case to `Actor.createAction()`.

`Matcher` requires no further changes. It discovers and embeds every entry exposed by
`Commands.all()` during initialization.
