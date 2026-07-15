# Chapter 2: Brightness Actions

## Goal

This guide adds two command actions:

- `IncreaseBrightness` changes the system brightness to its maximum value.
- `DecreaseBrightness` changes the system brightness to its minimum value.

Both actions change the global system setting, not only the brightness of `MainActivity`.

Android protects this setting with `android.permission.WRITE_SETTINGS`. This is a special app-access
permission: it cannot be requested with `requestPermissions()` or `EasyPermissions`. The application
must check `Settings.System.canWrite()` and, when necessary, open the system's **Modify system
settings** page for the user.

Because the settings page does not return a useful permission result, `MainActivity.onResume()` asks
`Actor` to retry the current action after the user returns.

## Step 1: Declare WRITE_SETTINGS

The manifest already declares the `tools` namespace. Add this permission next to the existing
permissions in `app/src/main/AndroidManifest.xml`:

```xml
<uses-permission
    android:name="android.permission.WRITE_SETTINGS"
    tools:ignore="ProtectedPermissions" />
```

Do not add `WRITE_SETTINGS` to `MainActivity.getRequiredPermissions()`. It is not a normal runtime
permission, and `EasyPermissions` cannot grant it.

## Step 2: Add a Shared Permission Helper

Both brightness actions require the same special-permission flow. Create
`app/src/main/java/com/top/asrdemo/actions/BrightnessPermission.java`:

```java
package com.top.asrdemo.actions;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

final class BrightnessPermission {
    private static final String TAG = "BrightnessPermission";

    private BrightnessPermission() {
    }

    static boolean canWrite(Activity activity) {
        return Settings.System.canWrite(activity);
    }

    static void requestIfNeeded(Activity activity) {
        if (canWrite(activity)) {
            return;
        }

        Toast.makeText(
                activity,
                "Allow Modify system settings to control brightness",
                Toast.LENGTH_LONG)
                .show();

        Intent appSettings = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        appSettings.setData(Uri.parse("package:" + activity.getPackageName()));

        try {
            activity.startActivity(appSettings);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "App-specific write settings page is unavailable", e);
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS));
        }
    }
}
```

The helper is package-private because only actions in `com.top.asrdemo.actions` need it. Each action
calls `requestIfNeeded()` from its constructor, satisfying the rule that permission is checked as
soon as the action is created.

## Step 3: Implement IncreaseBrightness

Replace the current `IncreaseBrightness.java` stub completely. In particular, remove
`EasyPermissions.PermissionCallbacks`; it is not part of this permission flow.

Use the following code:

```java
package com.top.asrdemo.actions;

import android.app.Activity;
import android.provider.Settings;
import android.util.Log;

public final class IncreaseBrightness implements Action {
    private static final String TAG = "IncreaseBrightness";
    private static final int MAX_BRIGHTNESS = 255;

    private final Activity activity;
    private boolean applied;

    public IncreaseBrightness(Activity activity) {
        this.activity = activity;
        BrightnessPermission.requestIfNeeded(activity);
    }

    @Override
    public void run() {
        if (applied) {
            return;
        }

        if (!BrightnessPermission.canWrite(activity)) {
            Log.i(TAG, "Waiting for WRITE_SETTINGS permission");
            return;
        }

        boolean modeUpdated = Settings.System.putInt(
                activity.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

        boolean brightnessUpdated = Settings.System.putInt(
                activity.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS,
                MAX_BRIGHTNESS);

        if (!modeUpdated || !brightnessUpdated) {
            throw new IllegalStateException("Failed to write maximum system brightness");
        }

        applied = true;
        Log.i(TAG, "System brightness set to maximum");
    }

    @Override
    public void close() {
        // Brightness is a persistent system setting. Closing this action does not restore it.
    }
}
```

The action first switches the device from automatic to manual brightness. Without this change,
adaptive brightness may immediately override the requested value.

## Step 4: Implement DecreaseBrightness

Create `app/src/main/java/com/top/asrdemo/actions/DecreaseBrightness.java`:

```java
package com.top.asrdemo.actions;

import android.app.Activity;
import android.provider.Settings;
import android.util.Log;

public final class DecreaseBrightness implements Action {
    private static final String TAG = "DecreaseBrightness";
    private static final int MIN_BRIGHTNESS = 0;

    private final Activity activity;
    private boolean applied;

    public DecreaseBrightness(Activity activity) {
        this.activity = activity;
        BrightnessPermission.requestIfNeeded(activity);
    }

    @Override
    public void run() {
        if (applied) {
            return;
        }

        if (!BrightnessPermission.canWrite(activity)) {
            Log.i(TAG, "Waiting for WRITE_SETTINGS permission");
            return;
        }

        boolean modeUpdated = Settings.System.putInt(
                activity.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

        boolean brightnessUpdated = Settings.System.putInt(
                activity.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS,
                MIN_BRIGHTNESS);

        if (!modeUpdated || !brightnessUpdated) {
            throw new IllegalStateException("Failed to write minimum system brightness");
        }

        applied = true;
        Log.i(TAG, "System brightness set to minimum");
    }

    @Override
    public void close() {
        // Brightness is a persistent system setting. Closing this action does not restore it.
    }
}
```

Android's standard integer brightness range is `0` through `255`. Many devices clamp `0` to their
hardware-supported minimum, but this is device-specific. Test the minimum action on every target
device because it can make the display difficult to see.

The `close()` methods intentionally do not restore the old brightness. These are setting commands,
not temporary visual effects: saying "increase brightness" should remain effective after another
action starts or after the activity closes.

## Step 5: Register Both Commands in Matcher

Use stable IDs that are separate from the phrases embedded by the model. In `Matcher.java`, replace
the existing increase-brightness constant and add the decrease-brightness constant:

```java
public static final String COMMAND_INCREASE_BRIGHTNESS = "increase_brightness";
public static final String COMMAND_DECREASE_BRIGHTNESS = "decrease_brightness";
```

Add both phrases in `loadCommands()`:

```java
addCommand(COMMAND_GREET, "Greet");
addCommand(COMMAND_INCREASE_BRIGHTNESS, "Increase the brightness");
addCommand(COMMAND_DECREASE_BRIGHTNESS, "Decrease the brightness");
```

Only the IDs are used for action routing. The human-readable phrases remain available through
`EXTRA_COMMAND_TEXT` for logging and UI output.

## Step 6: Give Actor Access to the Activity

The brightness actions need an `Activity` to open the app-specific settings page. Update the fields
and constructor in `Actor.java`:

```java
private final Activity activity;
private final LocalBroadcastManager broadcastManager;
private final ViewGroup actionHost;

public Actor(Activity activity) {
    this.activity = activity;
    broadcastManager = LocalBroadcastManager.getInstance(activity);
    actionHost = activity.findViewById(R.id.upper_section);
}
```

Then extend `createAction()`:

```java
private Action createAction(String commandId) {
    switch (commandId) {
        case Matcher.COMMAND_GREET:
            return new Greet(actionHost);
        case Matcher.COMMAND_INCREASE_BRIGHTNESS:
            return new IncreaseBrightness(activity);
        case Matcher.COMMAND_DECREASE_BRIGHTNESS:
            return new DecreaseBrightness(activity);
        default:
            return null;
    }
}
```

Constructing either brightness action now checks permission immediately. If permission is missing,
the constructor opens the system settings screen. `Actor` still makes the newly created instance its
current action, and its first `run()` waits until permission has been granted.

## Step 7: Add Actor's Permission-Return Retry

Add this public method to `Actor.java`:

```java
public void retryCurrentAction() {
    if (currentAction == null) {
        return;
    }

    try {
        currentAction.run();
    } catch (RuntimeException e) {
        Log.e(TAG, "Failed to retry current action", e);
        closeCurrentAction();
    }
}
```

The `applied` flag in each brightness action makes retries idempotent. Calling this method after an
already successful action does nothing.

## Step 8: Retry from MainActivity.onResume

Add the following lifecycle method to `MainActivity.java`:

```java
@Override
protected void onResume() {
    super.onResume();

    if (actor != null) {
        actor.retryCurrentAction();
    }
}
```

The complete first-run permission sequence is now:

```text
Brightness command matched
    -> Actor constructs brightness action
    -> Constructor sees that Settings.System.canWrite() is false
    -> Android opens Modify system settings
    -> User enables permission and returns
    -> MainActivity.onResume()
    -> Actor.retryCurrentAction()
    -> Brightness action writes the system setting
```

If the user does not grant permission, `run()` logs that it is waiting and leaves brightness
unchanged. Matching either brightness command again opens the permission page again because a new
action instance is created.

## Step 9: Build and Verify

Build and install the application:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Verify the permission flow:

1. Revoke **Modify system settings** access for the application in Android settings.
2. Say "increase the brightness".
3. Confirm that the app-specific **Modify system settings** page opens.
4. Enable access and return to the application.
5. Confirm that brightness changes to maximum without repeating the command.

Verify both values with ADB:

```bash
adb shell settings get system screen_brightness_mode
adb shell settings get system screen_brightness
```

After increasing brightness, the expected values are mode `0` (manual) and brightness `255`. After
decreasing brightness, the expected values are mode `0` and brightness `0`, subject to device-specific
minimum clamping.

Useful logs:

```bash
adb logcat -s Actor Matcher BrightnessPermission IncreaseBrightness DecreaseBrightness
```

Finally, match `Greet` after either brightness command. `Actor` should close the brightness action
and launch `Greet`, while the selected system brightness remains unchanged.
