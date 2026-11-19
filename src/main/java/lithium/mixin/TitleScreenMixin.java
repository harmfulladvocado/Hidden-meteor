/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package lithium.mixin;

import meteordevelopment.meteorclient.systems.modules.Anti_detection.FixInconsistencies;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    public TitleScreenMixin(Component title) {
        super(title);
    }

    @ModifyArg(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"
        ),
        index = 1
    )
    private String modifyTitleText(String text) {
        return FixInconsistencies.modifyTitleText(text);
    }
}
