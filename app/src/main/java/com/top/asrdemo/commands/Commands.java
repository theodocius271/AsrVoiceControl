package com.top.asrdemo.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Commands {
    public static final String COMMAND_GREET = "greet";
    public static final String COMMAND_INCREASE_BRIGHTNESS = "increase_brightness";
    public static final String COMMAND_DECREASE_BRIGHTNESS = "decrease_brightness";
    public static final String COMMAND_INCREASE_VOLUME = "increase_volume";
    public static final String COMMAND_DECREASE_VOLUME = "decrease_volume";
    public static final String COMMAND_SHOW_VERSION = "show_version";

    // No matching
    public static final String NO_MATCHING_COMMAND = "抱歉，我不太理解，请尝试音量/亮度/设备信息相关问题";

    // command Greet
    public static final String TEXT_GREET = "Greet";
    public static final String TEXT_SAY_HELLO = "Say hello";
    public static final String TEXT_HELLO = "Hello";

    // command IncreaseBrightness
    public static final String TEXT_INCREASE_BRIGHTNESS = "Increase the brightness";
    public static final String TEXT_INCREASE_BRIGHTNESS_1 = "Can't see clearly, make it brighter";
    public static final String TEXT_INCREASE_BRIGHTNESS_2 = "Make the screen brighter";

    // command DecreaseBrightness
    public static final String TEXT_DECREASE_BRIGHTNESS = "Decrease the brightness";
    public static final String TEXT_DECREASE_BRIGHTNESS_1 = "亮度调暗";
    public static final String TEXT_DECREASE_BRIGHTNESS_2 = "Make the screen darker";
    // command IncreaseVolume
    public static final String TEXT_INCREASE_VOLUME = "Increase the volume";
    public static final String TEXT_TURN_UP_VOLUME = "声音大一点";
    public static final String TEXT_MAKE_IT_LOUDER = "Make it louder";
    // command DecreaseVolume
    public static final String TEXT_DECREASE_VOLUME = "Decrease the volume";
    public static final String TEXT_DECREASE_VOLUME_1 = "声音小一点";
    public static final String TEXT_DECREASE_VOLUME_2 = "Make it quieter";
    public static final String TEXT_DECREASE_VOLUME_3 = "把声音调小";

    // command ShowVersion
    public static final String TEXT_SHOW_VERSION = "Device information";
    public static final String TEXT_SHOW_VERSION_1 = "About device";
    public static final String TEXT_SHOW_VERSION_2 = "System information";
    public static final String TEXT_SHOW_VERSION_3 = "What is the settings of this device";

    private static final Map<String, List<String>> COMMAND_TEXTS;

    static {
        Map<String, List<String>> commands = new LinkedHashMap<>();

        commands.put(COMMAND_GREET, texts(
                TEXT_GREET, TEXT_SAY_HELLO, TEXT_HELLO
        ));

        commands.put(COMMAND_INCREASE_BRIGHTNESS, texts(
                TEXT_INCREASE_BRIGHTNESS,
                TEXT_INCREASE_BRIGHTNESS_1,
                TEXT_INCREASE_BRIGHTNESS_2));

        commands.put(COMMAND_DECREASE_BRIGHTNESS, texts(
                TEXT_DECREASE_BRIGHTNESS,
                TEXT_DECREASE_BRIGHTNESS_1,
                TEXT_DECREASE_BRIGHTNESS_2));

        commands.put(COMMAND_INCREASE_VOLUME, texts(
                TEXT_INCREASE_VOLUME,
                TEXT_TURN_UP_VOLUME,
                TEXT_MAKE_IT_LOUDER));

        commands.put(COMMAND_DECREASE_VOLUME, texts(
                TEXT_DECREASE_VOLUME,
                TEXT_DECREASE_VOLUME_1,
                TEXT_DECREASE_VOLUME_2,
                TEXT_DECREASE_VOLUME_3));

        commands.put(COMMAND_SHOW_VERSION, texts(
                TEXT_SHOW_VERSION,
                TEXT_SHOW_VERSION_1,
                TEXT_SHOW_VERSION_2,
                TEXT_SHOW_VERSION_3));

        COMMAND_TEXTS = Collections.unmodifiableMap(commands);
    }

    private Commands() {}

    public static Map<String, List<String>> all() {
        return COMMAND_TEXTS;
    }

    public static List<String> textsFor(String commandId) {
        List<String> res = COMMAND_TEXTS.get(commandId);
        return res != null ? res : Collections.emptyList();
    }

    private static List<String> texts(String... val) {
        return Collections.unmodifiableList(Arrays.asList(val));
    }
}