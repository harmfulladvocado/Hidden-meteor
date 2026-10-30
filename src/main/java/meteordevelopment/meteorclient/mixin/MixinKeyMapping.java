/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Anti_detection.ResistTranslationDetection;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public abstract class MixinKeyMapping {

    @Shadow public abstract String getName();

    @Inject(method = "getTranslatedKeyMessage", at = @At("HEAD"), cancellable = true)
    private void meteor$blockKeybindLeak(CallbackInfoReturnable<Component> cir) {
        if (Modules.get() == null) return;
        ResistTranslationDetection module = Modules.get().get(ResistTranslationDetection.class);
        if (module != null && module.isActive() && ResistTranslationDetection.shouldBlock(getName())) {
            cir.setReturnValue(Component.literal(getName()));
        }
    }
}
