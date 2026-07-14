# Chapter 2: Command Actions

## Goal

This chapter connects a command matched by `Matcher` to an application action. The first action,
`Greet`, adds a `TextView` containing `Hello` to `activity_main.upper_section`. When another known
command is matched, `Actor` closes the current action before it starts the new one.

The resulting flow is:

```text
AsrService
    -> Matcher
    -> COMMAND_MATCHED local broadcast
    -> Actor
    -> Action.close() on the previous action
    -> Action.run() on the new action
```

`Actor` belongs to `MainActivity` because the actions in this chapter modify that activity's view
hierarchy. Register it in `onCreate()` and close it in `onDestroy()` so it cannot retain a destroyed
activity.

## Step 1: Add Stable Matcher Constants

Do not route actions using the current generated ID, such as `cmd_0`. That ID changes when commands
are reordered. Give every actionable command a stable ID and publish the broadcast contract from
`Matcher`.

Add these constants near the top of `Matcher`, immediately below `TAG`:

```java
public static final String ACTION_COMMAND_MATCHED =
        "com.top.asrdemo.action.COMMAND_MATCHED";
public static final String EXTRA_COMMAND_ID = "command_id";
public static final String EXTRA_COMMAND_TEXT = "command_text";
public static final String EXTRA_SIMILARITY = "similarity";
public static final String EXTRA_ORIGINAL_TEXT = "original_text";

public static final String COMMAND_GREET = "greet";
```

Replace `loadCommands()` with the following implementation. The helper keeps the stable ID next to
the phrase that is embedded:

```java
private void loadCommands() {
    commandEmbeddings.clear();
    commandTexts.clear();

    Log.i(TAG, "Pre-computing command embeddings...");
    long start = System.currentTimeMillis();

    addCommand(COMMAND_GREET, "Greet");

    long elapsed = System.currentTimeMillis() - start;
    Log.i(TAG, String.format(
            "Command loading completed in %dms (%d commands)",
            elapsed,
            commandEmbeddings.size()));
}

private void addCommand(String commandId, String commandText) {
    float[] embedding = embedder.embed(commandText);
    if (embedding == null) {
        Log.e(TAG, "Failed to embed command: " + commandText);
        return;
    }

    commandEmbeddings.put(commandId, embedding);
    commandTexts.put(commandId, commandText);
    Log.d(TAG, String.format(
            "Loaded command: %s (id=%s, dim=%d)",
            commandText,
            commandId,
            embedding.length));
}
```

Replace `broadcastMatchResult()` so all senders and receivers share the same constant names:

```java
@SuppressWarnings("deprecation")
private void broadcastMatchResult(
        String commandId,
        float similarity,
        String originalText) {
    Intent intent = new Intent(ACTION_COMMAND_MATCHED);
    intent.putExtra(EXTRA_COMMAND_ID, commandId);
    intent.putExtra(EXTRA_SIMILARITY, similarity);
    intent.putExtra(EXTRA_ORIGINAL_TEXT, originalText);

    if (commandId != null) {
        intent.putExtra(EXTRA_COMMAND_TEXT, commandTexts.get(commandId));
    }

    LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent);
}
```

## Step 2: Create the Actions Package

Create this package and file structure:

```text
app/src/main/java/com/top/asrdemo/actions/
    Action.java
    Actor.java
    Greet.java
```

## Step 3: Define the Action Contract

Create `app/src/main/java/com/top/asrdemo/actions/Action.java`:

```java
package com.top.asrdemo.actions;

/** A command action with an explicit UI/resource lifecycle. */
public interface Action {
    public void run();

    public void close();
}
```

The contract is intentionally small:

- `run()` creates or starts everything owned by the action.
- `close()` removes UI and releases every resource created by `run()`.
- Both methods are called on the main thread by `Actor`.
- `close()` must be safe even if the action has already been closed.

## Step 4: Implement Greet

Create `app/src/main/java/com/top/asrdemo/actions/Greet.java`:

```java
package com.top.asrdemo.actions;

import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

import com.top.asrdemo.R;

/** Displays a greeting in the activity's upper section. */
public final class Greet implements Action {
    private final ViewGroup parent;
    private TextView greetingView;

    public Greet(ViewGroup parent) {
        this.parent = parent;
    }

    @Override
    public void run() {
        if (greetingView != null) {
            return;
        }

        TextView view = new TextView(parent.getContext());
        view.setText("Hello");
        view.setTextSize(32);
        view.setTextColor(Color.BLACK);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(16), dp(16), dp(16), dp(16));
        view.setBackgroundResource(R.drawable.glass_background);

        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        parent.addView(view, layoutParams);
        greetingView = view;
    }

    @Override
    public void close() {
        if (greetingView == null) {
            return;
        }

        parent.removeView(greetingView);
        greetingView = null;
    }

    private int dp(int value) {
        float density = parent.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
```

`Greet` receives the parent rather than looking up `MainActivity` globally. This keeps view ownership
explicit and makes the action easier to test. The existing `upper_section` is a `LinearLayout`, which
is also a `ViewGroup`, so no layout change is required.

## Step 5: Implement Actor

Create `app/src/main/java/com/top/asrdemo/actions/Actor.java`:

```java
package com.top.asrdemo.actions;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.ViewGroup;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.top.asrdemo.R;
import com.top.asrdemo.commands.Matcher;

/** Receives matched commands and owns the currently running action. */
public final class Actor extends BroadcastReceiver implements AutoCloseable {
    private static final String TAG = "Actor";

    private final LocalBroadcastManager broadcastManager;
    private final ViewGroup actionHost;

    private Action currentAction;
    private boolean registered;

    public Actor(Activity activity) {
        broadcastManager = LocalBroadcastManager.getInstance(activity);
        actionHost = activity.findViewById(R.id.upper_section);
    }

    @SuppressWarnings("deprecation")
    public void start() {
        if (registered) {
            return;
        }

        IntentFilter filter = new IntentFilter(Matcher.ACTION_COMMAND_MATCHED);
        broadcastManager.registerReceiver(this, filter);
        registered = true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Matcher.ACTION_COMMAND_MATCHED.equals(intent.getAction())) {
            return;
        }

        String commandId = intent.getStringExtra(Matcher.EXTRA_COMMAND_ID);
        if (commandId == null) {
            // A failed match does not replace the action already on screen.
            return;
        }

        Action nextAction = createAction(commandId);
        if (nextAction == null) {
            Log.w(TAG, "No action registered for command: " + commandId);
            return;
        }

        closeCurrentAction();
        currentAction = nextAction;

        try {
            currentAction.run();
            Log.i(TAG, "Started action for command: " + commandId);
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to run action for command: " + commandId, e);
            closeCurrentAction();
        }
    }

    private Action createAction(String commandId) {
        switch (commandId) {
            case Matcher.COMMAND_GREET:
                return new Greet(actionHost);
            default:
                return null;
        }
    }

    private void closeCurrentAction() {
        if (currentAction == null) {
            return;
        }

        try {
            currentAction.close();
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to close current action", e);
        } finally {
            currentAction = null;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void close() {
        if (registered) {
            broadcastManager.unregisterReceiver(this);
            registered = false;
        }
        closeCurrentAction();
    }
}
```

The replacement order is important. `Actor` creates the next action first, closes the current action,
then runs the new action. An unknown command ID leaves the current action untouched because there is
nothing valid to launch.

`LocalBroadcastManager` dispatches its receiver callbacks through the application's main looper, so
`Greet.run()` and `Greet.close()` can modify the view hierarchy directly.

## Step 6: Connect Actor to MainActivity

Add this import to `MainActivity.java`:

```java
import com.top.asrdemo.actions.Actor;
```

Add an `Actor` field next to the existing `Matcher` field:

```java
private Matcher matcher;
private Actor actor;
```

In `onCreate()`, create and start the actor after `setContentView()` and the existing view lookups.
Starting it before the matcher is also acceptable and ensures it is ready before any match broadcast:

```java
actor = new Actor(this);
actor.start();

matcher = Matcher.getInstance();
if (!matcher.isInitialized()) {
    boolean success = matcher.initialize(this);
    if (!success) {
        Toast.makeText(this, "Failed to init command Matcher", Toast.LENGTH_LONG).show();
    }
}
```

Close the actor in `onDestroy()`. Do this before releasing the matcher:

```java
@SuppressWarnings("deprecation")
@Override
protected void onDestroy() {
    LocalBroadcastManager.getInstance(this).unregisterReceiver(asrReceiver);
    LocalBroadcastManager.getInstance(this).unregisterReceiver(matcherReceiver);

    if (actor != null) {
        actor.close();
    }
    matcher.release();

    if (isListening) {
        stopAsrService();
    }

    super.onDestroy();
}
```

The existing `matcherReceiver` can remain in `MainActivity`; it displays diagnostic match text while
`Actor` performs the actual action. Update that receiver to use the constants from `Matcher`:

```java
if (Matcher.ACTION_COMMAND_MATCHED.equals(action)) {
    String commandId = intent.getStringExtra(Matcher.EXTRA_COMMAND_ID);
    String commandText = intent.getStringExtra(Matcher.EXTRA_COMMAND_TEXT);
    float similarity = intent.getFloatExtra(Matcher.EXTRA_SIMILARITY, 0.0f);
    String originalText = intent.getStringExtra(Matcher.EXTRA_ORIGINAL_TEXT);

    // Keep the existing matched/no-match display logic here.
}
```

Also update `registerMatcherReceiver()`:

```java
@SuppressWarnings("deprecation")
private void registerMatcherReceiver() {
    IntentFilter filter = new IntentFilter();
    filter.addAction(Matcher.ACTION_COMMAND_MATCHED);
    LocalBroadcastManager.getInstance(this).registerReceiver(matcherReceiver, filter);
}
```

## Step 7: Verify the Behavior

Build and install the debug application:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then verify these cases:

1. Say `Greet`, or a close semantic equivalent. `Matcher` should broadcast command ID `greet`, and a
   new `TextView` containing `Hello` should appear in `upper_section`.
2. Trigger `Greet` again. The old `TextView` should be removed before a new one is added; the layout
   must still contain only one greeting.
3. Say something that does not cross the match threshold. The current greeting should remain because
   a no-match result is not a new action.
4. Leave or recreate `MainActivity`. The greeting should be removed and the old `Actor` receiver must
   no longer receive broadcasts.

Useful log filter:

```bash
adb logcat -s Actor Matcher Embedder
```

## Adding the Next Action

For every new action:

1. Add a stable command ID and embedded phrase in `Matcher`.
2. Create a class in `com.top.asrdemo.actions` that implements `Action`.
3. Add one case to `Actor.createAction()`.
4. Make the action's `close()` method undo everything its `run()` method created.

This keeps matching independent from Android behavior: `Matcher` decides what command was heard,
while `Actor` decides which application action implements that command.
