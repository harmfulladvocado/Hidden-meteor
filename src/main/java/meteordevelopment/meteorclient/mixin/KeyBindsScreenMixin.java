/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.Anti_detection.HideKeybinds;
import meteordevelopment.meteorclient.utils.misc.input.KeyBinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Arrays;

@Mixin(KeyBindsList.class)
public class KeyBindsScreenMixin {

    @Redirect(
        method = {"<init>", "refreshEntries"},
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/Options;keyMappings:[Lnet/minecraft/client/KeyMapping;"
        )
    )
    private KeyMapping[] filterMeteorKeyMappings(Options options) {
        if (!Modules.get().isActive(HideKeybinds.class)) return options.keyMappings;

        return Arrays.stream(options.keyMappings)
            .filter(km -> km != KeyBinds.OPEN_GUI && km != KeyBinds.OPEN_COMMANDS)
            .toArray(KeyMapping[]::new);
    }
}
