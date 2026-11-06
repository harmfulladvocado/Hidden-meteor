/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.Anti_detection;

import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.ResourcePackListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class HideResourcePacks extends Module {
    private final Setting<List<String>> packs = settings.getDefaultGroup().add(new ResourcePackListSetting.Builder()
        .name("packs")
        .description("Resource packs to hide from the selection lists.")
        .build()
    );

    private final Setting<Keybind> revealKey = settings.getDefaultGroup().add(new KeybindSetting.Builder()
        .name("reveal-key")
        .description("Hold to show hidden packs in the selection list.")
        .defaultValue(Keybind.none())
        .build()
    );

    public HideResourcePacks() {
        super(Categories.Anti_detection, "hide-resource-packs", "Hides selected resource packs from the pack selection list.");
        defaultActive = false;
    }

    public static Stream<PackSelectionModel.Entry> filter(Stream<PackSelectionModel.Entry> stream) {
        if (Modules.get() == null) return stream;

        HideResourcePacks module = Modules.get().get(HideResourcePacks.class);
        if (module == null || !module.isActive() || module.isRevealing()) return stream;

        return stream.filter(entry -> entry.isRequired() || !module.matches(entry.getId(), entry.getTitle().getString()));
    }

    public static boolean isRevealing() {
        if (Modules.get() == null) return false;

        HideResourcePacks module = Modules.get().get(HideResourcePacks.class);
        return module != null && module.isActive() && module.revealKey.get().isPressed();
    }

    private boolean matches(String id, String title) {
        for (String value : packs.get()) {
            if (matchesEntry(value, id, title)) return true;
        }

        return false;
    }

    private static boolean matchesEntry(String value, String id, String title) {
        if (value == null || id == null || title == null) return false;

        String needle = value.trim();
        if (needle.isEmpty()) return false;

        if (title.equalsIgnoreCase(needle) || id.equalsIgnoreCase(needle)) return true;

        int slash = id.lastIndexOf('/');
        String fileName = slash >= 0 ? id.substring(slash + 1) : id;

        if (fileName.equalsIgnoreCase(needle)) return true;

        return fileName.toLowerCase(Locale.ROOT).endsWith(".zip")
            && fileName.substring(0, fileName.length() - 4).equalsIgnoreCase(needle);
    }
}
