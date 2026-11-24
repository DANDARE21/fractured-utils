package net.dandare21.fracturedutils.npc.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.dandare21.fracturedutils.npc.NpcDefinition;
import net.dandare21.fracturedutils.npc.NpcModelType;
import net.dandare21.fracturedutils.npc.entity.CustomNpcEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.UUID;

public class CustomNpcRenderer extends GeoEntityRenderer<CustomNpcEntity> {
    private final PlayerModel<CustomNpcEntity> defaultPlayerModel;
    private final PlayerModel<CustomNpcEntity> slimPlayerModel;

    public CustomNpcRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new DynamicNpcGeoModel());
        this.shadowRadius = 0.5F;
        this.defaultPlayerModel = new PlayerModel<>(renderManager.bakeLayer(ModelLayers.PLAYER), false);
        this.slimPlayerModel = new PlayerModel<>(renderManager.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public boolean shouldShowName(CustomNpcEntity entity) {
        if (!entity.isShowNameTag()) {
            return false;
        }
        return super.shouldShowName(entity);
    }

    @Override
    public void render(CustomNpcEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (entity.getModelType() == NpcModelType.PLAYER) {
            renderPlayerFallback(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        // Validate GeckoLib model existence before letting GeckoLib attempt to load it
        DynamicNpcGeoModel geoModel = (DynamicNpcGeoModel) this.getGeoModel();
        ResourceLocation modelLoc = geoModel.getModelResource(entity);
        if (!DynamicNpcGeoModel.doesResourceExist(modelLoc)) {
            // Safe fallback: render player model so game never crashes
            renderPlayerFallback(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        NpcDefinition def = entity.getDefinition();
        if (def != null) {
            this.shadowRadius = def.shadowRadius();
        }

        try {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        } catch (Throwable t) {
            // Prevent client crash on any unexpected GeckoLib loading/baking/animating exception
            renderPlayerFallback(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        }
    }

    private void renderPlayerFallback(CustomNpcEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        NpcDefinition def = entity.getDefinition();
        boolean isSlim = def != null && "slim".equalsIgnoreCase(def.skinModel());
        PlayerModel<CustomNpcEntity> model = isSlim ? this.slimPlayerModel : this.defaultPlayerModel;

        ResourceLocation skinTexture = resolveSkin(def, entity.getUUID());

        poseStack.pushPose();

        // Standard living entity rendering transforms
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
        float netHeadYaw = headYaw - bodyYaw;
        float headPitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        // Lip-sync talking head wobble/mouth movement
        if (entity.isSpeaking()) {
            float speakCycle = (entity.tickCount + partialTick) * 0.8F;
            headPitch += Mth.sin(speakCycle) * 3.5F;
        }

        // Apply scale/hitbox height
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0D, -1.501D, 0.0D);

        float limbSwing = 0.0F;
        float limbSwingAmount = 0.0F;
        if (entity.isAlive()) {
            limbSwingAmount = entity.walkAnimation.speed(partialTick);
            limbSwing = entity.walkAnimation.position(partialTick);
            if (limbSwingAmount > 1.0F) {
                limbSwingAmount = 1.0F;
            }
        }

        model.young = entity.isBaby();
        model.riding = entity.isPassenger();
        model.crouching = entity.isCrouching();

        model.setupAnim(entity, limbSwing, limbSwingAmount, entity.tickCount + partialTick, netHeadYaw, headPitch);

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(skinTexture));
        model.renderToBuffer(poseStack, vertexConsumer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

        poseStack.popPose();
    }

    private ResourceLocation resolveSkin(NpcDefinition def, UUID fallbackUuid) {
        if (def == null || def.skinValue() == null || def.skinValue().isBlank()) {
            return DefaultPlayerSkin.getDefaultSkin(fallbackUuid != null ? fallbackUuid : UUID.randomUUID());
        }

        String skinVal = def.skinValue().trim();

        // 1. If it has a namespace / texture path format (e.g. fractured_utils:textures/...)
        if (skinVal.contains(":") || skinVal.endsWith(".png")) {
            try {
                ResourceLocation loc = new ResourceLocation(skinVal);
                if (DynamicNpcGeoModel.isValidTexture(loc) && DynamicNpcGeoModel.doesResourceExist(loc)) {
                    return loc;
                }
            } catch (Exception ignored) {}
        }

        // 2. If it is a UUID string
        try {
            UUID uuid = UUID.fromString(skinVal);
            return DefaultPlayerSkin.getDefaultSkin(uuid);
        } catch (IllegalArgumentException ignored) {}

        // 3. If it is a player username
        try {
            GameProfile profile = new GameProfile(null, skinVal);
            var skinManager = Minecraft.getInstance().getSkinManager();
            return skinManager.getInsecureSkinLocation(profile);
        } catch (Exception ignored) {}

        return DefaultPlayerSkin.getDefaultSkin(fallbackUuid != null ? fallbackUuid : UUID.randomUUID());
    }
}
