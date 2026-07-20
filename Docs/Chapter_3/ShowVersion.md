# Chapter 3: Show Version and System Information

## Goal

This chapter adds a `ShowVersion` action for phrases such as "Show the version". The action writes
one system-information message to the chatbox containing:

- device manufacturer and model name;
- Android version, API level, security patch, and firmware build;
- hardware serial number when the app is allowed to read it;
- SoC, CPU hardware, supported ABIs, and available processor count;
- total and currently available memory;
- total and currently available internal data storage; and
- display name, pixel resolution, density, refresh rate, and HDR capability.

Most of this information needs no permission. Hardware serial numbers are the exception: Android
10 and newer restrict them to privileged apps, device/profile owners, and a few other special
roles. The implementation catches this restriction and displays `Restricted by Android` instead
of crashing or substituting a different identifier.

The action stores the ID returned by `ChatboxManager.addSystemText()`. When `Actor` closes the
action because another command was matched, `ShowVersion.close()` removes only the message created
by this action.

## Step 1: Register the Command Phrases

Open `app/src/main/java/com/top/asrdemo/commands/Commands.java` and add the new command ID beside the
other `COMMAND_...` constants:

```java
public static final String COMMAND_SHOW_VERSION = "show_version";
```

Add several recognition examples beside the existing `TEXT_...` constants:

```java
// command ShowVersion
public static final String TEXT_SHOW_THE_VERSION = "Show the version";
public static final String TEXT_SHOW_SYSTEM_INFORMATION = "Show system information";
public static final String TEXT_WHAT_ANDROID_VERSION = "What Android version is this";
public static final String TEXT_SHOW_DEVICE_INFORMATION = "Show device information";
```

Finally, add this entry to the `static` initializer before `COMMAND_TEXTS` is made unmodifiable:

```java
commands.put(COMMAND_SHOW_VERSION, texts(
        TEXT_SHOW_THE_VERSION,
        TEXT_SHOW_SYSTEM_INFORMATION,
        TEXT_WHAT_ANDROID_VERSION,
        TEXT_SHOW_DEVICE_INFORMATION));
```

No `Matcher` change is required. `Matcher.loadCommands()` already reads every command and phrase
from `Commands.all()` and computes their embeddings during initialization.

## Step 2: Understand Serial-Number Access

`Build.getSerial()` returns the hardware serial only when Android authorizes the caller. A regular
application installed with ADB or from an app store cannot obtain it on current Android releases.
The ordinary `READ_PHONE_STATE` permission does not remove this Android 10+ restriction.

Therefore, the code in the next step always calls `Build.getSerial()` but catches
`SecurityException`. This produces one of these values:

```text
Serial number: ABC123456
Serial number: Restricted by Android
Serial number: Unknown
```

Do not use `Settings.Secure.ANDROID_ID` as a fallback and label it as the serial number. It is a
different, app-scoped identifier and can change after a factory reset or signing-key change.

If this project is shipped by the device manufacturer as a privileged system application, the OEM
can grant serial access. Add this declaration to `AndroidManifest.xml`:

```xml
<uses-permission
    android:name="android.permission.READ_PRIVILEGED_PHONE_STATE"
    tools:ignore="ProtectedPermissions" />
```

The OEM must also install the APK under a privileged system partition, sign it appropriately, and
allowlist `READ_PRIVILEGED_PHONE_STATE` for `com.top.asrdemo` in the device image. Merely adding the
manifest line to a normally installed APK does not grant the permission. Leave it out for regular
application builds.

## Step 3: Create ShowVersion

Create `app/src/main/java/com/top/asrdemo/actions/ShowVersion.java`:

```java
package com.top.asrdemo.actions;

import android.app.ActivityManager;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.view.Display;

import com.top.asrdemo.utils.ChatboxManager;

import java.util.Locale;

/** Displays application and device version information in the system chat. */
public final class ShowVersion implements Action {
    private static final String UNKNOWN = "Unknown";
    private static final String RESTRICTED = "Restricted by Android";

    private final Context context;
    private final ChatboxManager chatboxManager;

    private long messageId = ChatboxManager.NO_MESSAGE_ID;

    public ShowVersion(Context context, ChatboxManager chatboxManager) {
        this.context = context.getApplicationContext();
        this.chatboxManager = chatboxManager;
    }

    @Override
    public void run() {
        if (messageId != ChatboxManager.NO_MESSAGE_ID) {
            return;
        }

        messageId = chatboxManager.addSystemText(buildSystemInformation());
    }

    @Override
    public void close() {
        if (messageId == ChatboxManager.NO_MESSAGE_ID) {
            return;
        }

        chatboxManager.removeMessage(messageId);
        messageId = ChatboxManager.NO_MESSAGE_ID;
    }

    private String buildSystemInformation() {
        StringBuilder information = new StringBuilder();
        information.append("System information\n")
                .append("App: ").append(readAppVersion()).append('\n')
                .append("Manufacturer: ").append(orUnknown(Build.MANUFACTURER)).append('\n')
                .append("Model name: ").append(orUnknown(Build.MODEL)).append('\n')
                .append("Android version: ")
                .append(orUnknown(Build.VERSION.RELEASE))
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                .append("Security patch: ")
                .append(orUnknown(Build.VERSION.SECURITY_PATCH)).append('\n')
                .append("Firmware version: ").append(orUnknown(Build.DISPLAY)).append('\n')
                .append("Serial number: ").append(readSerialNumber()).append('\n')
                .append(readCpuInformation()).append('\n')
                .append("Memory: ").append(readMemoryInformation()).append('\n')
                .append("Internal storage: ").append(readStorageInformation()).append('\n')
                .append("LCD/display: ").append(readDisplayInformation());

        return information.toString();
    }

    @SuppressLint("HardwareIds")
    private static String readSerialNumber() {
        try {
            return orUnknown(Build.getSerial());
        } catch (SecurityException e) {
            return RESTRICTED;
        }
    }

    private static String readCpuInformation() {
        StringBuilder cpu = new StringBuilder("CPU information:");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cpu.append("\n  SoC: ")
                    .append(orUnknown(Build.SOC_MANUFACTURER))
                    .append(' ')
                    .append(orUnknown(Build.SOC_MODEL));
        }

        cpu.append("\n  Hardware: ").append(orUnknown(Build.HARDWARE))
                .append("\n  ABIs: ")
                .append(Build.SUPPORTED_ABIS.length == 0
                        ? UNKNOWN
                        : String.join(", ", Build.SUPPORTED_ABIS))
                .append("\n  Available cores: ")
                .append(Runtime.getRuntime().availableProcessors());
        return cpu.toString();
    }

    private String readAppVersion() {
        PackageManager packageManager = context.getPackageManager();
        String applicationName = packageManager
                .getApplicationLabel(context.getApplicationInfo())
                .toString();

        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(
                    context.getPackageName(),
                    0);
            String versionName = packageInfo.versionName != null
                    ? packageInfo.versionName
                    : UNKNOWN;
            return String.format(
                    Locale.US,
                    "%s %s (%d)",
                    applicationName,
                    versionName,
                    packageInfo.getLongVersionCode());
        } catch (PackageManager.NameNotFoundException e) {
            return applicationName + " " + UNKNOWN;
        }
    }

    private String readMemoryInformation() {
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return UNKNOWN;
        }

        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return String.format(
                Locale.US,
                "%.1f GiB total, %.1f GiB available",
                bytesToGiB(memoryInfo.totalMem),
                bytesToGiB(memoryInfo.availMem));
    }

    private static String readStorageInformation() {
        StatFs storage = new StatFs(Environment.getDataDirectory().getPath());
        return String.format(
                Locale.US,
                "%.1f GiB total, %.1f GiB available",
                bytesToGiB(storage.getTotalBytes()),
                bytesToGiB(storage.getAvailableBytes()));
    }

    private String readDisplayInformation() {
        DisplayManager displayManager =
                (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager == null) {
            return UNKNOWN;
        }

        Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) {
            return UNKNOWN;
        }

        Display.Mode mode = display.getMode();
        int densityDpi = context.getResources().getDisplayMetrics().densityDpi;
        return String.format(
                Locale.US,
                "%s, %d x %d px, %d dpi, %.1f Hz, %s",
                display.getName(),
                mode.getPhysicalWidth(),
                mode.getPhysicalHeight(),
                densityDpi,
                mode.getRefreshRate(),
                display.isHdr() ? "HDR" : "SDR");
    }

    private static double bytesToGiB(long bytes) {
        return bytes / (1024.0 * 1024.0 * 1024.0);
    }

    private static String orUnknown(String value) {
        if (value == null || value.trim().isEmpty()
                || Build.UNKNOWN.equalsIgnoreCase(value.trim())) {
            return UNKNOWN;
        }
        return value.trim();
    }
}
```

The application context is sufficient because this action reads system services and package
metadata; it does not launch an activity or permission screen. Keeping it instead of the activity
also avoids adding another activity reference to the action.

`Build.SOC_MANUFACTURER` and `Build.SOC_MODEL` were added in Android 12 (API 31), so the action
checks the API level before reading them. On older supported devices, the message still includes
the hardware name, supported ABIs, and available processor count. These values are more reliable
than parsing `/proc/cpuinfo`, whose content varies by device and Android release.

`Environment.getDataDirectory()` measures the internal data volume used by applications. It is not
necessarily the sum of every physical flash partition or removable SD card. Likewise, public
Android APIs expose display resolution, density, refresh rate, and HDR capability, but not whether
the physical panel technology is LCD or OLED. The guide therefore labels this row `LCD/display`
without guessing the panel type.

## Step 4: Route the Command in Actor

Open `app/src/main/java/com/top/asrdemo/actions/Actor.java`. Add this case to
`createAction(String commandId)`:

```java
case Commands.COMMAND_SHOW_VERSION:
    return new ShowVersion(activity, chatboxManager);
```

The complete end of the switch should now resemble:

```java
case Commands.COMMAND_DECREASE_VOLUME:
    return new DecreaseVolume(activity);
case Commands.COMMAND_SHOW_VERSION:
    return new ShowVersion(activity, chatboxManager);
default:
    return null;
```

`ShowVersion` is in the same `actions` package as `Actor`, so no import is needed for the action
class.

## Step 5: Update the Unsupported-Command Hint

`MainActivity` currently shows a hard-coded list when no command matches. Update that text so the
new command is discoverable:

```java
chatboxManager.addSystemText(
        "No matching command. Supported:\n"
                + "Greet,\n"
                + "Increase / Decrease Brightness,\n"
                + "Increase / Decrease Volume,\n"
                + "Show Version");
```

This change affects only the displayed hint. The actual command definitions remain owned by
`Commands.java`.

## Step 6: Verify the Action

Build and install the debug application:

```bash
./gradlew :app:assembleDebug
```

Then verify these cases on a device:

1. Say "Show the version" and confirm that it matches `show_version`.
2. Confirm one system bubble displays the model name, Android and firmware versions, CPU details,
   memory, internal storage, and LCD/display information.
3. On a regular Android 10+ installation, confirm the serial row says `Restricted by Android` and
   the action does not crash.
4. On an OEM privileged build, confirm the serial row contains the real hardware serial.
5. Rotate through the alternate phrases and confirm they all route to the same action.
6. Background and resume the app while `ShowVersion` is current. `Actor.retryCurrentAction()` must
   not insert a duplicate message.
7. Match a different command and confirm the system-information bubble is removed when
   `ShowVersion.close()` runs.
8. Test on an Android 11 or older device and confirm the missing SoC line does not prevent the
   remaining information from appearing.

No manifest change is needed for normal application builds. Use the privileged permission only as
part of an OEM-controlled system image that grants it to this application.
