package net.dandare21.fracturedutils.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.dandare21.fracturedutils.client.camera.SequencerViewportManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTarget.class)
public abstract class RenderTargetMixin {

    @Inject(method = "_bindWrite", at = @At("TAIL"))
    private void onBindWriteTail(boolean setViewport, CallbackInfo ci) {
        if (setViewport && SequencerViewportManager.isActive() && SequencerViewportManager.isRenderingLevel()) {
            SequencerViewportManager.setupViewport();
        }
    }
}
