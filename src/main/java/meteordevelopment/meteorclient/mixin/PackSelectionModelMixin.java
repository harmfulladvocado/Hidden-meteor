/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Anti_detection.HideResourcePacks;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

@Mixin(PackSelectionModel.class)
public abstract class PackSelectionModelMixin {
    @Inject(method = "getSelected", at = @At("RETURN"), cancellable = true)
    private void meteor$filterSelected(CallbackInfoReturnable<Stream<PackSelectionModel.Entry>> cir) {
        cir.setReturnValue(HideResourcePacks.filter(cir.getReturnValue()));
    }

    @Inject(method = "getUnselected", at = @At("RETURN"), cancellable = true)
    private void meteor$filterUnselected(CallbackInfoReturnable<Stream<PackSelectionModel.Entry>> cir) {
        cir.setReturnValue(HideResourcePacks.filter(cir.getReturnValue()));
    }
}
