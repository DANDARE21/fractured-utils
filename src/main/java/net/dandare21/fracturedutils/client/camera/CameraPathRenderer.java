package net.dandare21.fracturedutils.client.camera;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.client.gui.MusicSequenceScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * Renders in-world 3D motion path splines, keyframe camera frustums, and look-at vectors
 * for the Music Sequencer, providing a Mine-imator / Blender-style interactive viewport.
 */
@Mod.EventBusSubscriber(modid = FracturedUtils.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CameraPathRenderer {

    private static boolean pathPreviewEnabled = true;

    public static boolean isPathPreviewEnabled() {
        return pathPreviewEnabled;
    }

    public static void setPathPreviewEnabled(boolean enabled) {
        pathPreviewEnabled = enabled;
    }

    public static void togglePathPreview() {
        pathPreviewEnabled = !pathPreviewEnabled;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        if (!pathPreviewEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        // Only visible while the music sequencer UI is active, never during in-game playback for players
        if (!(mc.screen instanceof MusicSequenceScreen seqScreen)) {
            return;
        }

        CameraSequenceTrack track = seqScreen.getActiveCameraTrack();
        double currentPlayheadMs = seqScreen.getPlayheadMs();

        if (track == null || track.isEmpty()) {
            return;
        }

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        Matrix4f matrix = poseStack.last().pose();

        for (CameraShot shot : track.getShots()) {
            renderShotTrajectory(shot, buffer, tesselator, matrix, mc);
            renderShotKeyframes(shot, buffer, tesselator, matrix, mc);
        }

        // Render current playhead camera position node
        if (currentPlayheadMs >= 0.0) {
            CameraTransform currentTr = track.evaluate(currentPlayheadMs, event.getPartialTick(), mc);
            if (currentTr != null) {
                renderCameraFrustum(currentTr.getPosition(), currentTr.getYaw(), currentTr.getPitch(), currentTr.getRoll(),
                        currentTr.getFov(), 1.0f, 0xFF, 0xFF, 0xFF, 0xFF, buffer, tesselator, matrix, true);
            }
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static void renderShotTrajectory(CameraShot shot, BufferBuilder buffer, Tesselator tesselator, Matrix4f matrix, Minecraft mc) {
        long start = shot.getStartMs();
        long end = shot.getEndMs();
        if (end <= start) return;

        // Sample points along spline every 30ms
        int step = 30;
        long duration = end - start;

        // Pass 1: Semi-transparent through blocks (depth test disabled)
        RenderSystem.depthMask(false);
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        for (long t = start; t <= end; t += step) {
            CameraTransform tr = shot.evaluate(t, 1.0f, mc);
            if (tr != null) {
                Vec3 p = tr.getPosition();
                float progress = duration > 0 ? (float) (t - start) / duration : 0.0f;
                // Gradient: Green (start) -> Cyan (mid) -> Magenta (end)
                float r = progress < 0.5f ? 0.0f : (progress - 0.5f) * 2.0f;
                float g = progress < 0.5f ? 1.0f : 1.0f - (progress - 0.5f) * 1.5f;
                float b = progress < 0.5f ? progress * 2.0f : 1.0f;
                buffer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, 0.45f).endVertex();
            }
        }
        tesselator.end();

        // Pass 2: Solid lines in direct line-of-sight (depth test enabled)
        RenderSystem.depthMask(true);
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        for (long t = start; t <= end; t += step) {
            CameraTransform tr = shot.evaluate(t, 1.0f, mc);
            if (tr != null) {
                Vec3 p = tr.getPosition();
                float progress = duration > 0 ? (float) (t - start) / duration : 0.0f;
                float r = progress < 0.5f ? 0.0f : (progress - 0.5f) * 2.0f;
                float g = progress < 0.5f ? 1.0f : 1.0f - (progress - 0.5f) * 1.5f;
                float b = progress < 0.5f ? progress * 2.0f : 1.0f;
                buffer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, 0.95f).endVertex();
            }
        }
        tesselator.end();
    }

    private static void renderShotKeyframes(CameraShot shot, BufferBuilder buffer, Tesselator tesselator, Matrix4f matrix, Minecraft mc) {
        for (CameraKeyframe kf : shot.getKeyframes()) {
            Vec3 pos = kf.getTransform().getPosition();
            float yaw = kf.getTransform().getYaw();
            float pitch = kf.getTransform().getPitch();
            float roll = kf.getTransform().getRoll();
            double fov = kf.getTransform().getFov();

            int r = 0x00;
            int g = 0xE5;
            int b = 0xFF;
            if (kf.isEnable()) {
                r = 0x00; g = 0xFF; b = 0x88;
            } else if (kf.isDisable()) {
                r = 0xFF; g = 0x33; b = 0x55;
            }

            // Draw frustum pyramid
            renderCameraFrustum(pos, yaw, pitch, roll, fov, 0.75f, r, g, b, 0xDD, buffer, tesselator, matrix, false);

            // Draw Decoupled Look-At ray if enabled
            if (kf.isLookAtEnabled()) {
                Vec3 lookTarget = CameraSequenceTrack.resolveLookAtPosition(kf, 1.0f, mc);
                if (lookTarget != null) {
                    renderLookAtLine(pos, lookTarget, buffer, tesselator, matrix);
                }
            }
        }
    }

    private static void renderCameraFrustum(Vec3 pos, float yaw, float pitch, float roll, double fov, float scale,
                                            int r, int g, int b, int a, BufferBuilder buffer, Tesselator tesselator,
                                            Matrix4f matrix, boolean highlight) {
        // Frustum geometry vectors relative to camera view
        float fovRad = (float) Math.toRadians(fov * 0.5);
        float aspect = 16.0f / 9.0f;
        float nearDist = 0.8f * scale;
        float hHeight = (float) Math.tan(fovRad) * nearDist;
        float hWidth = hHeight * aspect;

        // Frustum 4 corners in camera local space (Z forward)
        Vector3f c0 = new Vector3f(-hWidth, hHeight, nearDist);  // Top-Left
        Vector3f c1 = new Vector3f(hWidth, hHeight, nearDist);   // Top-Right
        Vector3f c2 = new Vector3f(hWidth, -hHeight, nearDist);  // Bottom-Right
        Vector3f c3 = new Vector3f(-hWidth, -hHeight, nearDist); // Bottom-Left
        Vector3f topNotch = new Vector3f(0.0f, hHeight + 0.15f * scale, nearDist); // Up notch

        // Rotate local points using yaw, pitch, roll
        org.joml.Quaternionf rot = CameraMath.eulerToQuaternion(yaw, pitch, roll);
        c0.rotate(rot);
        c1.rotate(rot);
        c2.rotate(rot);
        c3.rotate(rot);
        topNotch.rotate(rot);

        float px = (float) pos.x;
        float py = (float) pos.y;
        float pz = (float) pos.z;

        RenderSystem.depthMask(true);
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        // 4 rays from origin to corners
        addLine(buffer, matrix, px, py, pz, px + c0.x, py + c0.y, pz + c0.z, r, g, b, a);
        addLine(buffer, matrix, px, py, pz, px + c1.x, py + c1.y, pz + c1.z, r, g, b, a);
        addLine(buffer, matrix, px, py, pz, px + c2.x, py + c2.y, pz + c2.z, r, g, b, a);
        addLine(buffer, matrix, px, py, pz, px + c3.x, py + c3.y, pz + c3.z, r, g, b, a);

        // Outer rectangular frame
        addLine(buffer, matrix, px + c0.x, py + c0.y, pz + c0.z, px + c1.x, py + c1.y, pz + c1.z, r, g, b, a);
        addLine(buffer, matrix, px + c1.x, py + c1.y, pz + c1.z, px + c2.x, py + c2.y, pz + c2.z, r, g, b, a);
        addLine(buffer, matrix, px + c2.x, py + c2.y, pz + c2.z, px + c3.x, py + c3.y, pz + c3.z, r, g, b, a);
        addLine(buffer, matrix, px + c3.x, py + c3.y, pz + c3.z, px + c0.x, py + c0.y, pz + c0.z, r, g, b, a);

        // Up arrow notch on top edge to show orientation / roll
        addLine(buffer, matrix, px + c0.x, py + c0.y, pz + c0.z, px + topNotch.x, py + topNotch.y, pz + topNotch.z, 0xFF, 0xE0, 0x66, a);
        addLine(buffer, matrix, px + c1.x, py + c1.y, pz + c1.z, px + topNotch.x, py + topNotch.y, pz + topNotch.z, 0xFF, 0xE0, 0x66, a);

        // Small center box at keyframe root
        float s = (highlight ? 0.08f : 0.05f) * scale;
        addLine(buffer, matrix, px - s, py - s, pz - s, px + s, py - s, pz - s, r, g, b, a);
        addLine(buffer, matrix, px + s, py - s, pz - s, px + s, py + s, pz - s, r, g, b, a);
        addLine(buffer, matrix, px + s, py + s, pz - s, px - s, py + s, pz - s, r, g, b, a);
        addLine(buffer, matrix, px - s, py + s, pz - s, px - s, py - s, pz - s, r, g, b, a);

        addLine(buffer, matrix, px - s, py - s, pz + s, px + s, py - s, pz + s, r, g, b, a);
        addLine(buffer, matrix, px + s, py - s, pz + s, px + s, py + s, pz + s, r, g, b, a);
        addLine(buffer, matrix, px + s, py + s, pz + s, px - s, py + s, pz + s, r, g, b, a);
        addLine(buffer, matrix, px - s, py + s, pz + s, px - s, py - s, pz + s, r, g, b, a);

        addLine(buffer, matrix, px - s, py - s, pz - s, px - s, py - s, pz + s, r, g, b, a);
        addLine(buffer, matrix, px + s, py - s, pz - s, px + s, py - s, pz + s, r, g, b, a);
        addLine(buffer, matrix, px + s, py + s, pz - s, px + s, py + s, pz + s, r, g, b, a);
        addLine(buffer, matrix, px - s, py + s, pz - s, px - s, py + s, pz + s, r, g, b, a);

        tesselator.end();
    }

    private static void renderLookAtLine(Vec3 from, Vec3 to, BufferBuilder buffer, Tesselator tesselator, Matrix4f matrix) {
        RenderSystem.depthMask(false);
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        addLine(buffer, matrix, (float) from.x, (float) from.y, (float) from.z, (float) to.x, (float) to.y, (float) to.z,
                0xFF, 0xBB, 0x33, 0x88);
        tesselator.end();
        RenderSystem.depthMask(true);
    }

    private static void addLine(BufferBuilder buffer, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b, int a) {
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).endVertex();
    }
}
