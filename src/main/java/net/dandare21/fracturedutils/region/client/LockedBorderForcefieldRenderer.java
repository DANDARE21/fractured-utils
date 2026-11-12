package net.dandare21.fracturedutils.region.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.region.data.Polygon2D;
import net.dandare21.fracturedutils.region.data.Region;
import net.dandare21.fracturedutils.region.data.Vec2d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Renders an animated, glowing worldborder-style forcefield along the perimeter of locked regions.
 * Dynamically fades in as the player approaches within proximity of any edge.
 * Uses analytical line-segment clipping so borders always render reliably near the player,
 * even when region vertices are located far away in unloaded chunks / outside simulation distance.
 */
@Mod.EventBusSubscriber(modid = FracturedUtils.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LockedBorderForcefieldRenderer {

    private static final ResourceLocation FORCEFIELD_TEXTURE = new ResourceLocation("minecraft", "textures/misc/forcefield.png");
    private static final double PROXIMITY_THRESHOLD = 20.0;
    private static final double CLIP_RADIUS = 28.0;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        Vec3 camPos = event.getCamera().getPosition();
        ClientRegionData data = ClientRegionData.getInstance();

        List<Region> regions = data.getAllRegions();
        boolean hasVisibleForcefield = false;

        for (Region r : regions) {
            if (data.isRegionLocked(r) && r.getPolygon() != null && r.getPolygon().vertexCount() >= 2) {
                Polygon2D.ClosestEdgeResult edge = r.getPolygon().findNearestEdge(camPos.x, camPos.z);
                if (edge.distanceSq() <= PROXIMITY_THRESHOLD * PROXIMITY_THRESHOLD) {
                    hasVisibleForcefield = true;
                    break;
                }
            }
        }

        if (!hasVisibleForcefield) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, FORCEFIELD_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE); // Additive glow
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        float gameTime = (float) (mc.level.getGameTime() % 24000) + event.getPartialTick();
        float scrollU = (gameTime * 0.012f) % 1.0f;

        float yBottom = (float) Math.max(camPos.y - 32.0, -64.0);
        float yTop = (float) Math.min(camPos.y + 32.0, 320.0);

        for (Region r : regions) {
            if (!data.isRegionLocked(r) || r.getPolygon() == null || r.getPolygon().vertexCount() < 2) {
                continue;
            }

            Polygon2D.ClosestEdgeResult edge = r.getPolygon().findNearestEdge(camPos.x, camPos.z);
            double distSq = edge.distanceSq();
            if (distSq > PROXIMITY_THRESHOLD * PROXIMITY_THRESHOLD) {
                continue;
            }

            int color = r.getColor() != 0 ? r.getColor() : (r.getType() != null ? r.getType().getDefaultColor() : 0xFF2255);
            int cr = (color >> 16) & 0xFF;
            int cg = (color >> 8) & 0xFF;
            int cb = color & 0xFF;

            List<Vec2d> vertices = r.getPolygon().getVertices();
            int n = vertices.size();

            for (int i = 0; i < n; i++) {
                Vec2d a = vertices.get(i);
                Vec2d b = vertices.get((i + 1) % n);

                double dx = b.x() - a.x();
                double dz = b.z() - a.z();
                double edgeLenSq = dx * dx + dz * dz;
                if (edgeLenSq < 1e-6) {
                    continue;
                }
                double edgeLen = Math.sqrt(edgeLenSq);

                double vx = a.x() - camPos.x;
                double vz = a.z() - camPos.z;

                // Analytical line-circle clipping against radius CLIP_RADIUS around camera
                double quadA = edgeLenSq;
                double quadB = 2.0 * (vx * dx + vz * dz);
                double quadC = (vx * vx + vz * vz) - (CLIP_RADIUS * CLIP_RADIUS);

                double discr = quadB * quadB - 4.0 * quadA * quadC;
                if (discr < 0.0) {
                    continue; // Entire line never intersects circle
                }

                double sqrtDiscr = Math.sqrt(discr);
                double t1 = (-quadB - sqrtDiscr) / (2.0 * quadA);
                double t2 = (-quadB + sqrtDiscr) / (2.0 * quadA);

                double tMin = Math.max(0.0, Math.min(t1, t2));
                double tMax = Math.min(1.0, Math.max(t1, t2));

                if (tMin >= tMax) {
                    continue; // Segment [a, b] is entirely outside the active radius
                }

                // Subdivide clipped segment into short steps for smooth distance fading & OpenGL stability
                double clippedLen = (tMax - tMin) * edgeLen;
                int steps = Math.max(1, (int) Math.ceil(clippedLen / 4.0));
                double dt = (tMax - tMin) / steps;

                for (int s = 0; s < steps; s++) {
                    double s0 = tMin + s * dt;
                    double s1 = tMin + (s + 1) * dt;

                    double p0x = a.x() + s0 * dx;
                    double p0z = a.z() + s0 * dz;
                    double p1x = a.x() + s1 * dx;
                    double p1z = a.z() + s1 * dz;

                    double dist0 = Math.hypot(p0x - camPos.x, p0z - camPos.z);
                    double dist1 = Math.hypot(p1x - camPos.x, p1z - camPos.z);

                    float fade0 = (float) Math.max(0.0, Math.min(1.0, 1.0 - (dist0 / PROXIMITY_THRESHOLD)));
                    float fade1 = (float) Math.max(0.0, Math.min(1.0, 1.0 - (dist1 / PROXIMITY_THRESHOLD)));

                    int alpha0 = (int) (fade0 * 200.0f);
                    int alpha1 = (int) (fade1 * 200.0f);

                    if (alpha0 <= 0 && alpha1 <= 0) {
                        continue;
                    }

                    float u0 = scrollU + (float) ((s0 * edgeLen) / 4.0);
                    float u1 = scrollU + (float) ((s1 * edgeLen) / 4.0);
                    float v0 = 0.0f;
                    float v1 = (yTop - yBottom) / 4.0f;

                    // Quad: P0(bottom) -> P1(bottom) -> P1(top) -> P0(top)
                    buffer.vertex(matrix, (float) p0x, yBottom, (float) p0z).uv(u0, v0).color(cr, cg, cb, alpha0).endVertex();
                    buffer.vertex(matrix, (float) p1x, yBottom, (float) p1z).uv(u1, v0).color(cr, cg, cb, alpha1).endVertex();
                    buffer.vertex(matrix, (float) p1x, yTop, (float) p1z).uv(u1, v1).color(cr, cg, cb, alpha1).endVertex();
                    buffer.vertex(matrix, (float) p0x, yTop, (float) p0z).uv(u0, v1).color(cr, cg, cb, alpha0).endVertex();
                }
            }
        }

        tesselator.end();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }
}
