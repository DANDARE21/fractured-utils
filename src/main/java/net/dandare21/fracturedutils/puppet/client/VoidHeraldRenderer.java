package net.dandare21.fracturedutils.puppet.client;

import net.dandare21.fracturedutils.puppet.boss.VoidHeraldBoss;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * GeckoLib GeoEntityRenderer for the Void Herald Boss.
 */
public class VoidHeraldRenderer extends GeoEntityRenderer<VoidHeraldBoss> {
    public VoidHeraldRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new VoidHeraldModel());
        this.shadowRadius = 0.8F;
    }
}
