package com.top.asrdemo.commands;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
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
    // command Greet
    public static final String TEXT_GREET = "Greet";
    public static final String TEXT_SAY_HELLO = "Say hello";
    public static final String TEXT_HELLO = "Hello";
    // command IncreaseBrightness
    public static final String TEXT_INCREASE_BRIGHTNESS = "Increase the brightness";
    public static final String TEXT_TURN_UP_BRIGHTNESS = "Turn up the brightness";
    public static final String TEXT_MAKE_SCREEN_BRIGHTER = "Make the screen brighter";
    // command DecreaseBrightness
    public static final String TEXT_DECREASE_BRIGHTNESS = "Decrease the brightness";
    public static final String TEXT_TURN_DOWN_BRIGHTNESS = "Turn down the brightness";
    public static final String TEXT_MAKE_SCREEN_DARKER = "Make the screen darker";
    // command IncreaseVolume
    public static final String TEXT_INCREASE_VOLUME = "Increase the volume";
    public static final String TEXT_TURN_UP_VOLUME = "Turn up the volume";
    public static final String TEXT_MAKE_IT_LOUDER = "Make it louder";
    // command DecreaseVolume
    public static final String TEXT_DECREASE_VOLUME = "Decrease the volume";
    public static final String TEXT_TURN_DOWN_VOLUME = "Turn down the volume";
    public static final String TEXT_MAKE_IT_QUIETER = "Make it quieter";

    private static final Map<String, List<String>> COMMAND_TEXTS;

    static {
        Map<String, List<String>> commands = new LinkedHashMap<>();

        commands.put(COMMAND_GREET, texts(
                TEXT_GREET, TEXT_SAY_HELLO, TEXT_HELLO
        ));

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