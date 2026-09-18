package net.dandare21.fracturedutils.puppet.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.network.packet.ClientboundAttackIndicatorPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side manager and renderer for circular and linear ground attack indicator decals.
 * Uses textures/misc/attack_indicator_circle.png and textures/misc/attack_indicator_line.png to render animated telegraphs.
 */
@Mod.EventBusSubscriber(modid = FracturedUtils.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientAttackIndicatorManager {

    public static final ResourceLocation CIRCLE_TEXTURE =
            new ResourceLocation(FracturedUtils.MOD_ID, "textures/misc/attack_indicator_circle.png");

    public static final ResourceLocation LINE_TEXTURE =
            new ResourceLocation(FracturedUtils.MOD_ID, "textures/misc/attack_indicator_line.png");

    public static class IndicatorData {
        public ClientboundAttackIndicatorPacket.IndicatorShape shape;
        public double x, y, z;
        public double endX, endY, endZ;
        public float widthOrRadius;
        public final int maxTicks;
        public int remainingTicks;
        public int color;

        public IndicatorData(ClientboundAttackIndicatorPacket.IndicatorShape shape,
                             double x, double y, double z,
                             double endX, double endY, double endZ,
                             float widthOrRadius, int durationTicks, int color) {
            this.shape = shape != null ? shape : ClientboundAttackIndicatorPacket.IndicatorShape.CIRCLE;
            this.x = x;
            this.y = y;
            this.z = z;
            this.endX = endX;
            this.endY = endY;
            this.endZ = endZ;
            this.widthOrRadius = widthOrRadius;
            this.maxTicks = Math.max(1, durationTicks);
            this.remainingTicks = Math.max(1, durationTicks);
            this.color = color;
        }

        public IndicatorData(double x, double y, double z, float radius, int durationTicks, int color) {
            this(ClientboundAttackIndicatorPacket.IndicatorShape.CIRCLE, x, y, z, x, y, z, radius, durationTicks, color);
        }
    }

    private static final Map<Integer, IndicatorData> ACTIVE_INDICATORS = new ConcurrentHashMap<>();

    public static void handlePacket(int id, double x, double y, double z, float radius, int durationTicks, int color, boolean remove) {
        handlePacket(ClientboundAttackIndicatorPacket.IndicatorShape.CIRCLE, id, x, y, z, x, y, z, radius, durationTicks, color, remove);
    }

    public static void handlePacket(ClientboundAttackIndicatorPacket.IndicatorShape shape,
                                    int id, double x, double y, double z,
                                    double endX, double endY, double endZ,
                                    float widthOrRadius, int durationTicks, int color, boolean remove) {
        if (remove) {
            ACTIVE_INDICATORS.remove(id);
        } else {
            IndicatorData existing = ACTIVE_INDICATORS.get(id);
            if (existing != null) {
                existing.shape = shape != null ? shape : existing.shape;
                existing.x = x;
                existing.y = y;
                existing.z = z;
                existing.endX = endX;
                existing.endY = endY;
                existing.endZ = endZ;
                existing.widthOrRadius = widthOrRadius;
                if (durationTicks > 0) {
                    existing.remainingTicks = durationTicks;
                }
                if (color != 0) {
                    existing.color = color;
                }
            } else {
                ACTIVE_INDICATORS.put(id, new IndicatorData(shape, x, y, z, endX, endY, endZ, widthOrRadius, durationTicks, color));
            }
        }
    }

    public static void clearAll() {
        ACTIVE_INDICATORS.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        ACTIVE_INDICATORS.entrySet().removeIf(entry -> {
            entry.getValue().remainingTicks--;
            return entry.getValue().remainingTicks <= 0;
        });
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (ACTIVE_INDICATORS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        PoseStack.Pose pose = poseStack.last();

        boolean hasCircles = false;
        boolean hasLines = false;
        for (IndicatorData data : ACTIVE_INDICATORS.values()) {
            if (data.shape == ClientboundAttackIndicatorPacket.IndicatorShape.CIRCLE) {
                hasCircles = true;
            } else if (data.shape == ClientboundAttackIndicatorPacket.IndicatorShape.LINE) {
                hasLines = true;
            }
            if (hasCircles && hasLines) break;
        }

        if (hasCircles) {
            RenderSystem.setShaderTexture(0, CIRCLE_TEXTURE);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (IndicatorData data : ACTIVE_INDICATORS.values()) {
                if (data.shape == ClientboundAttackIndicatorPacket.IndicatorShape.CIRCLE) {
                    renderCircleQuad(buffer, pose, data);
                }
            }
            tesselator.end();
        }

        if (hasLines) {
            RenderSystem.setShaderTexture(0, LINE_TEXTURE);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (IndicatorData data : ACTIVE_INDICATORS.values()) {
                if (data.shape == ClientboundAttackIndicatorPacket.IndicatorShape.LINE) {
                    renderLineQuad(buffer, pose, data);
                }
            }
            tesselator.end();
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static void renderCircleQuad(BufferBuilder buffer, PoseStack.Pose pose, IndicatorData data) {
        double x1 = data.x - data.widthOrRadius;
        double x2 = data.x + data.widthOrRadius;
        double z1 = data.z - data.widthOrRadius;
        double z2 = data.z + data.widthOrRadius;
        float y0 = (float) (data.y + 0.04);

        int c = data.color != 0 ? data.color : 0xD4B026FF;
        int a = (c >> 24) & 0xFF;
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;

        float progress = Math.max(0.0F, Math.min(1.0F, 1.0F - ((float) data.remainingTicks / data.maxTicks)));
        float pulseSpeed = 0.25F + (progress * 0.45F);
        float pulse = 0.80F + 0.20F * (float) Math.sin((data.maxTicks - data.remainingTicks) * pulseSpeed * 2.0);
        int finalAlpha = Math.min(255, (int) (a * pulse));

        buffer.vertex(pose.pose(), (float) x1, y0, (float) z1).uv(0.0F, 0.0F).color(r, g, b, finalAlpha).endVertex();
        buffer.vertex(pose.pose(), (float) x1, y0, (float) z2).uv(0.0F, 1.0F).color(r, g, b, finalAlpha).endVertex();
        buffer.vertex(pose.pose(), (float) x2, y0, (float) z2).uv(1.0F, 1.0F).color(r, g, b, finalAlpha).endVertex();
        buffer.vertex(pose.pose(), (float) x2, y0, (float) z1).uv(1.0F, 0.0F).color(r, g, b, finalAlpha).endVertex();
    }

    private static void renderLineQuad(BufferBuilder buffer, PoseStack.Pose pose, IndicatorData data) {
        double dx = data.endX - data.x;
        double dz = data.endZ - data.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        if (distXZ < 0.0001) {
            return;
        }

        // Unit forward vector in XZ plane
        double fx = dx / distXZ;
        double fz = dz / distXZ;

        // Perpendicular unit vector (to the right of forward vector)
        double rx = -fz;
        double rz = fx;

        double halfWidth = Math.max(0.05, data.widthOrRadius / 2.0);

        // 4 corners of the line decal on the ground
        double x1 = data.x - rx * halfWidth;
        double z1 = data.z - rz * halfWidth;
        float y1 = (float) (data.y + 0.04);

        double x2 = data.endX - rx * halfWidth;
        double z2 = data.endZ - rz * halfWidth;
        float y2 = (float) (data.endY + 0.04);

        double x3 = data.endX + rx * halfWidth;
        double z3 = data.endZ + rz * halfWidth;
        float y3 = (float) (data.endY + 0.04);

        double x4 = data.x + rx * halfWidth;
        double z4 = data.z + rz * halfWidth;
        float y4 = (float) (data.y + 0.04);

        int c = data.color != 0 ? data.color : 0xD4B026FF;
        int a = (c >> 24) & 0xFF;
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;

        float progress = Math.max(0.0F, Math.min(1.0F, 1.0F - ((float) data.remainingTicks / data.maxTicks)));
        float pulseSpeed = 0.25F + (progress * 0.45F);
        float pulse = 0.80F + 0.20F * (float) Math.sin((data.maxTicks - data.remainingTicks) * pulseSpeed * 2.0);
        int finalAlpha = Math.min(255, (int) (a * pulse));

        // Quad vertices (Start Left, End Left, End Right, Start Right)
        // Texture: U=0 left rail, U=1 right rail; V=0 start, V=1 end
        buffer.vertex(pose.pose(), (float) x1, y1, (float) z1).uv(0.0F, 0.0F).color(r, g, b, finalAlpha).endVertex();
        buffer.vertex(pose.pose(), (float) x2, y2, (float) z2).uv(0.0F, 1.0F).color(r, g, b, finalAlpha).endVertex();
        buffer.vertex(pose.pose(), (float) x3, y3, (float) z3).uv(1.0F, 1.0F).color(r, g, b, finalAlpha).endVertex();
        buffer.vertex(pose.pose(), (float) x4, y4, (float) z4).uv(1.0F, 0.0F).color(r, g, b, finalAlpha).endVertex();
    }
}
