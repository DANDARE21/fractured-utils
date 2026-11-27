package net.dandare21.fracturedutils.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.dandare21.fracturedutils.client.camera.SequencerViewportManager;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @ModifyArg(method = "getProjectionMatrix", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;setPerspective(FFFF)Lorg/joml/Matrix4f;", remap = false), index = 1)
    private float modifyProjectionMatrixAspectRatio(float originalAspectRatio) {
        if (SequencerViewportManager.isActive()) {
            return SequencerViewportManager.getAspectRatio();
        }
        return originalAspectRatio;
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void onRenderLevelHead(float partialTicks, long finishTimeNano, PoseStack poseStack, CallbackInfo ci) {
        if (SequencerViewportManager.isActive()) {
            SequencerViewportManager.setRenderingLevel(true);
            SequencerViewportManager.setupViewport();
        }
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void onRenderLevelReturn(float partialTicks, long finishTimeNano, PoseStack poseStack, CallbackInfo ci) {
        if (SequencerViewportManager.isActive()) {
            SequencerViewportManager.setRenderingLevel(false);
            SequencerViewportManager.restoreViewport();
        }
    }
}
