/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Anti_detection.HideResourcePacks;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PackSelectionScreen.class)
public abstract class PackSelectionScreenMixin {
    @Shadow
    private void populateLists(PackSelectionModel.EntryBase entryBase) {}

    @Unique
    private boolean meteor$wasRevealing;

    @Inject(method = "tick", at = @At("TAIL"))
    private void meteor$refreshRevealState(CallbackInfo ci) {
        boolean revealing = HideResourcePacks.isRevealing();
        if (revealing == meteor$wasRevealing) return;

        meteor$wasRevealing = revealing;
        populateLists(null);
    }
}
