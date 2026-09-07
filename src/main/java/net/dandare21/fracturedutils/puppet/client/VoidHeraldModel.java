package net.dandare21.fracturedutils.puppet.client;

import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.puppet.boss.VoidHeraldBoss;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib GeoModel for the Void Herald Boss.
 */
public class VoidHeraldModel extends GeoModel<VoidHeraldBoss> {
    private static final ResourceLocation MODEL = new ResourceLocation(FracturedUtils.MOD_ID, "geo/void_herald.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(FracturedUtils.MOD_ID, "textures/entity/void_herald.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(FracturedUtils.MOD_ID, "animations/void_herald.animation.json");

    @Override
    public ResourceLocation getModelResource(VoidHeraldBoss animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(VoidHeraldBoss animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(VoidHeraldBoss animatable) {
        return ANIMATION;
    }
}
