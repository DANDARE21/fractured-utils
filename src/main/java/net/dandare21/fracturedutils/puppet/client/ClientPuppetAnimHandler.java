package net.dandare21.fracturedutils.puppet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.animatable.GeoEntity;

/**
 * Client-side animation trigger dispatcher for GeckoLib-powered puppet entities.
 */
@OnlyIn(Dist.CLIENT)
public class ClientPuppetAnimHandler {

    public static void handleAnim(int entityId, String controllerName, String animName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity entity = mc.level.getEntity(entityId);
        if (entity instanceof GeoEntity geoEntity) {
            String controller = (controllerName == null || controllerName.isBlank()) ? null : controllerName;
            geoEntity.triggerAnim(controller, animName);
        }
    }
}
