/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.Anti_detection;

import com.mojang.blaze3d.platform.InputConstants;
import meteordevelopment.meteorclient.events.meteor.KeyInputEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.Screenshot;

import java.util.ArrayList;
import java.util.List;

public class EmptyScreenshots extends Module {

    private final SettingGroup sgGeneral    = settings.getDefaultGroup();
    private final SettingGroup sgExceptions = settings.createGroup("Exceptions");

    // General

    private final Setting<Boolean> disableModules = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-modules")
        .description("Temporarily disables all active modules before the screenshot is taken.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableHud = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-hud")
        .description("Temporarily hides the HUD before the screenshot is taken.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> customizePendingFrames = sgGeneral.add(new BoolSetting.Builder()
        .name("customize-pending-frames")
        .description("Allows you to manually set how many frames to wait before capturing. Useful for debugging if screenshots turn out empty.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> pendingFramesCount = sgGeneral.add(new IntSetting.Builder()
        .name("pending-frames")
        .description("Number of frames to wait before taking the screenshot. Increase this if screenshots are coming out empty.")
        .defaultValue(4)
        .min(1)
        .sliderMax(20)
        .visible(customizePendingFrames::get)
        .build()
    );

    // Exceptions

    private final Setting<List<Module>> moduleExceptions = sgExceptions.add(new ModuleListSetting.Builder()
        .name("module-exceptions")
        .description("Modules that will NOT be disabled or unbound when panic is triggered.")
        .defaultValue(getDefaultExceptions())
        .build()
    );

    private static List<Module> getDefaultExceptions() {
        List<Module> list = new ArrayList<>();
        Module freecam = Modules.get().get(meteordevelopment.meteorclient.systems.modules.render.Freecam.class);
        if (freecam != null) list.add(freecam);
        return list;
    }

    // State

    private final List<Module> savedModules = new ArrayList<>();
    private boolean hudWasActive = false;
    private int pendingFrames = -1;

    public EmptyScreenshots() {
        super(Categories.Anti_detection, "empty-screenshots",
            "Hides all modules and the HUD when a screenshot is taken, then restores everything.");
    }

    // Event

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onKeyInput(KeyInputEvent event) {
        if (event.action != KeyAction.Press) return;
        if (event.input.key() != InputConstants.KEY_F2) return;

        // Cancel the vanilla screenshot
        event.cancel();

        disableEverything();

        // Don't capture yet, wait for a clean frame to actually render first
        pendingFrames = customizePendingFrames.get() ? pendingFramesCount.get() : 5;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (pendingFrames < 0) return;

        pendingFrames--;
        if (pendingFrames > 0) return;

        pendingFrames = -1;
        takeScreenshot();
    }

    private void takeScreenshot() {
        try {
            Screenshot.grab(
                mc.gameDirectory,
                mc.gameRenderer.mainRenderTarget(),
                component -> mc.execute(() -> {
                    if (mc.player != null) mc.player.sendSystemMessage(component);
                    restoreEverything();
                })
            );
        } catch (Throwable t) {
            restoreEverything();
            throw t;
        }
    }

    private void disableEverything() {
        savedModules.clear();

        if (disableModules.get()) {
            for (Module module : Modules.get().getActive()) {
                if (module == this) continue;
                if (moduleExceptions.get().contains(module)) continue;
                savedModules.add(module);
                module.disable();
            }
        }

        if (disableHud.get()) {
            hudWasActive = Hud.get().active;
            Hud.get().active = false;
        }
    }

    private void restoreEverything() {
        for (Module module : savedModules) {
            module.enable();
        }
        savedModules.clear();

        if (disableHud.get() && hudWasActive) {
            Hud.get().active = true;
        }
    }
}
