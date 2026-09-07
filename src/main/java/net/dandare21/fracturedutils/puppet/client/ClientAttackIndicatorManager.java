package net.dandare21.fracturedutils.puppet.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.dandare21.fracturedutils.FracturedUtils;
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
 * Client-side manager and renderer for circular ground attack indicator decals.
 * Uses textures/misc/attack_indicator_circle.png to render animated ground telegraphs.
 */
@Mod.EventBusSubscriber(modid = FracturedUtils.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientAttackIndicatorManager {

    public static final ResourceLocation CIRCLE_TEXTURE =
            new ResourceLocation(FracturedUtils.MOD_ID, "textures/misc/attack_indicator_circle.png");

    public static class IndicatorData {
        public double x, y, z;
        public float radius;
        public final int maxTicks;
        public int remainingTicks;
        public int color;

        public IndicatorData(double x, double y, double z, float radius, int durationTicks, int color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.maxTicks = Math.max(1, durationTicks);
            this.remainingTicks = Math.max(1, durationTicks);
            this.color = color;
        }
    }

    private static final Map<Integer, IndicatorData> ACTIVE_INDICATORS = new ConcurrentHashMap<>();

    public static void handlePacket(int id, double x, double y, double z, float radius, int durationTicks, int color, boolean remove) {
        if (remove) {
            ACTIVE_INDICATORS.remove(id);
        } else {
            IndicatorData existing = ACTIVE_INDICATORS.get(id);
            if (existing != null) {
                existing.x = x;
                existing.y = y;
                existing.z = z;
                existing.radius = radius;
                if (durationTicks > 0) {
                    existing.remainingTicks = durationTicks;
                }
                if (color != 0) {
                    existing.color = color;
                }
            } else {
                ACTIVE_INDICATORS.put(id, new IndicatorData(x, y, z, radius, durationTicks, color));
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
        RenderSystem.setShaderTexture(0, CIRCLE_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        PoseStack.Pose pose = poseStack.last();

        for (IndicatorData data : ACTIVE_INDICATORS.values()) {
            renderCircleQuad(buffer, pose, data);
        }

        tesselator.end();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static void renderCircleQuad(BufferBuilder buffer, PoseStack.Pose pose, IndicatorData data) {
        double x1 = data.x - data.radius;
        double x2 = data.x + data.radius;
        double z1 = data.z - data.radius;
        double z2 = data.z + data.radius;
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
}
