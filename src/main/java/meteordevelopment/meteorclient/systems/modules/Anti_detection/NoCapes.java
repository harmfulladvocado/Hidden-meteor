/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.Anti_detection;

import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.network.Capes;

public class NoCapes extends Module {
    public NoCapes() {
        super(Categories.Anti_detection, "no-capes",
            "Disables Meteor capes and their network requests.");
        runInMainMenu = true;
        defaultActive = true;
    }

    public static boolean isEnabled() {
        if (Modules.get() == null) return false;

        NoCapes module = Modules.get().get(NoCapes.class);
        return module != null && module.isActive();
    }

    @Override
    public void onActivate() {
        Capes.clear();
    }

    @Override
    public void onDeactivate() {
        Capes.init();
    }
}
