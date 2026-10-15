package net.dandare21.fracturedutils.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.dandare21.fracturedutils.client.camera.SequencerViewportManager;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow private float zoom;
    @Shadow private float zoomX;
    @Shadow private float zoomY;
    @Shadow public abstract float getDepthFar();

    @Inject(method = "getProjectionMatrix", at = @At("HEAD"), cancellable = true)
    private void onGetProjectionMatrix(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        if (SequencerViewportManager.isActive()) {
            Matrix4f matrix4f = new Matrix4f();
            if (this.zoom != 1.0F) {
                matrix4f.translate(this.zoomX, -this.zoomY, 0.0F);
                matrix4f.scale(this.zoom, this.zoom, 1.0F);
            }
            matrix4f.perspective((float) (fov * (Math.PI / 180.0)), SequencerViewportManager.getAspectRatio(), 0.05F, this.getDepthFar());
            cir.setReturnValue(matrix4f);
        }
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
