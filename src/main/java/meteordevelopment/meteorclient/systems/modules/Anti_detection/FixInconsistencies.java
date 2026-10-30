/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.Anti_detection;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FixInconsistencies extends Module {
    private static final Pattern MODS_PATTERN = Pattern.compile("(?i)(\\b|\\()(\\d+)(\\s+mods?\\b)");

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> spoofModAmount = sgGeneral.add(new BoolSetting.Builder()
        .name("spoof-mod-amount")
        .description("Decreases the mod count displayed on the title screen by 11 to make it the exact same as a Lithium instance.")
        .defaultValue(true)
        .build()
    );

    public FixInconsistencies() {
        super(Categories.Anti_detection, "fix-inconsistencies",
            "Fixes visual inconsistencies caused by client modifications.");
        runInMainMenu = true;
        defaultActive = true;
    }

    public static String modifyTitleText(String text) {
        if (text == null) return null;

        if (Modules.get() == null) return text;
        FixInconsistencies module = Modules.get().get(FixInconsistencies.class);
        if (module == null || !module.isActive() || !module.spoofModAmount.get()) {
            return text;
        }

        Matcher matcher = MODS_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                int count = Integer.parseInt(matcher.group(2));
                int newCount = Math.max(0, count - 11);
                String suffix = matcher.group(3);
                if (newCount == 1) {
                    if (suffix.contains("Mods")) {
                        suffix = suffix.replace("Mods", "Mod");
                    } else if (suffix.contains("mods")) {
                        suffix = suffix.replace("mods", "mod");
                    }
                } else {
                    if (suffix.endsWith("Mod")) {
                        suffix = suffix.substring(0, suffix.length() - 3) + "Mods";
                    } else if (suffix.endsWith("mod")) {
                        suffix = suffix.substring(0, suffix.length() - 3) + "mods";
                    }
                }
                return matcher.replaceFirst(matcher.group(1) + newCount + suffix);
            } catch (NumberFormatException ignored) {}
        }

        return text;
    }
}
