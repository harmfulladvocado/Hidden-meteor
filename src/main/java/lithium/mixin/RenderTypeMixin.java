/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package lithium.mixin;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderType.class)
public abstract class RenderTypeMixin {
    @Inject(method = "prepare", at = @At("RETURN"), cancellable = true)
    private void onPrepare(CallbackInfoReturnable<PreparedRenderType> cir) {
        PreparedRenderType prepared = cir.getReturnValue();
        if (!"lines".equals(prepared.name()) || prepared.pipeline() != RenderPipelines.LINES || prepared.oitPipelineSet() != null) return;

        cir.setReturnValue(new PreparedRenderType(
            prepared.name(),
            prepared.pipeline(),
            RenderPipelines.OIT_LINES_TRANSLUCENT,
            prepared.dynamicTransforms(),
            prepared.scissorState(),
            prepared.textures()
        ));
    }
}
