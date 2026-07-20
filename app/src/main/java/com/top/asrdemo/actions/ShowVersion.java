package com.top.asrdemo.actions;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
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

public final class ShowVersion implements Action {
    private static final String UNKNOWN = "Unknown";
    private static final String RESTICTED = "Restricted by Android";

    private final Context context;
    private final ChatboxManager chatboxManager;

    public ShowVersion(Context context, ChatboxManager chatboxManager) {
        this.context = context;
        this.chatboxManager = chatboxManager;

    }

    @Override
    public void run() {
        chatboxManager.addSystemText(buildSystemInformation());
    }

    @Override
    public void close() {}

    private String buildSystemInformation() {
        StringBuilder information = new StringBuilder();
        information.append("已为您打开设备信息\n")
                .append("App: ").append(readAppVersion()).append('\n')
                .append("Manufacturer: ").append(orUnknown(Build.MANUFACTURER)).append('\n')
                .append("Model name: ").append(orUnknown(Build.MODEL)).append('\n')
                .append("Android version: ").append(orUnknown(Build.VERSION.RELEASE))
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                .append(readCpuInformation()).append('\n')
                .append("Memory: ").append(readMemoryInformation()).append('\n')
                .append("Internal storage: ").append(readStorageInformation()).append('\n')
                .append("LCD: ").append(readDisplayInformation());
                // .append("Serial number: ").append(readSerialNumber()).append('\n')
        return information.toString();
    }


//    private static String readSerialNumber() {
//        try {
//            return orUnknown(Build.getSerial());
//        } catch (SecurityException e) {
//            return RESTICTED;
//        }
//    }

    private static String readCpuInformation() {
        StringBuilder cpu = new StringBuilder("CPU information: ");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cpu.append("\n  SoC: ")
                    .append(orUnknown(Build.SOC_MANUFACTURER))
                    .append(" ")
                    .append(orUnknown(Build.SOC_MODEL));
        }

        cpu.append("\n  Hardware: ").append(orUnknown(Build.HARDWARE))
                .append("\n  ABIs: ").append(
                        Build.SUPPORTED_ABIS.length == 0 ? UNKNOWN : String.join(", ", Build.SUPPORTED_ABIS)
                ).append("\n  Available cores: ").append(Runtime.getRuntime().availableProcessors());

        return cpu.toString();
    }

    private String readAppVersion() {
        PackageManager packageManager = context.getPackageManager();
        String applicationName = packageManager.getApplicationLabel(context.getApplicationInfo()).toString();

        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            String versionName = packageInfo.versionName != null ? packageInfo.versionName : UNKNOWN;
            return String.format(Locale.US, "%s %s (%d)", applicationName, versionName, packageInfo.getLongVersionCode());
        } catch (PackageManager.NameNotFoundException e) {
            return applicationName + " " + UNKNOWN;
        }
    }

    private String readMemoryInformation() {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return UNKNOWN;
        }

        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return String.format(Locale.US, "%.1f GiB total, %.1f GiB available",
                bytesToGiB(memoryInfo.totalMem), bytesToGiB((memoryInfo.availMem)));
    }

    private static String readStorageInformation() {
        StatFs storage = new StatFs(Environment.getDataDirectory().getPath());
        return String.format(
                Locale.US, "%.1f GiB total, %.1f GiB available",
                bytesToGiB(storage.getTotalBytes()), bytesToGiB(storage.getAvailableBytes())
        );
    }

    private String readDisplayInformation() {
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager == null) {
            return UNKNOWN;
        }

        Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) {
            return UNKNOWN;
        }

        Display.Mode mode = display.getMode();
        int densityDpi = context.getResources().getDisplayMetrics().densityDpi;
        return String.format(Locale.US, "%s, %d x %d px, %d dpi, %.1f Hz, %s",
                display.getName(), mode.getPhysicalWidth(), mode.getPhysicalHeight(), densityDpi, mode.getRefreshRate(),
                display.isHdr() ? "HDR" : "SDR");
    }

    private static double bytesToGiB (long bytes) {
        return bytes / (1073741824.0);
    }

    private static String orUnknown (String value) {
        if (value == null || value.trim().isEmpty() || Build.UNKNOWN.equalsIgnoreCase(value.trim())) {
            return UNKNOWN;
        }
        return value.trim();
    }
}
