/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.Anti_detection;

import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

public class ResistTranslationDetection extends Module {

    public ResistTranslationDetection() {
        super(Categories.Anti_detection, "resist-translation-detection",
            "Prevents servers from detecting Meteor via the Sign Translation Vulnerability (MC-265322).");
        runInMainMenu = true;
        defaultActive = true;
    }

    public static boolean shouldBlock(String key) {
        if (key == null) return false;
        return key.startsWith("key.meteor-client.")
            || key.startsWith("key.category.meteor-client.")
            || key.startsWith("meteor-client.")
            || key.startsWith("gui.meteor-client.")
            || key.startsWith("meteor.")
            || key.startsWith("key.meteor.")
            || key.equals("meteor.menu.title")
            || key.equals("meteor.menu.credits");
    }
}
