/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Anti_detection.ResistTranslationDetection;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

@Mixin(TranslatableContents.class)
public class TranslatableContentsMixin {

    @Shadow @Final private String key;

    @Inject(method = "visit(Lnet/minecraft/network/chat/FormattedText$StyledContentConsumer;Lnet/minecraft/network/chat/Style;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <T> void meteor$blockTranslateStyled(FormattedText.StyledContentConsumer<T> consumer, Style style, CallbackInfoReturnable<Optional<T>> cir) {
        if (ResistTranslationDetection.shouldBlock(key)) {
            cir.setReturnValue(consumer.accept(style, key));
        }
    }

    @Inject(method = "visit(Lnet/minecraft/network/chat/FormattedText$ContentConsumer;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <T> void meteor$blockTranslatePlain(FormattedText.ContentConsumer<T> consumer, CallbackInfoReturnable<Optional<T>> cir) {
        if (ResistTranslationDetection.shouldBlock(key)) {
            cir.setReturnValue(consumer.accept(key));
        }
    }
}
