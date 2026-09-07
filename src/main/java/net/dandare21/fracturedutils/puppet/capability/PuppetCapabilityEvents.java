package net.dandare21.fracturedutils.puppet.capability;

import net.dandare21.fracturedutils.FracturedUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Event subscriber handling capability attachment to all Mobs and autonomous server-side ticking.
 */
@Mod.EventBusSubscriber(modid = FracturedUtils.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PuppetCapabilityEvents {

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Mob mob) {
            PuppetCapabilityProvider provider = new PuppetCapabilityProvider(mob);
            event.addCapability(PuppetCapabilityProvider.IDENTIFIER, provider);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide) {
            mob.getCapability(PuppetCapabilityProvider.PUPPET_HANDLER).ifPresent(IPuppetHandler::tick);
        }
    }

    public static void onRegisterCapabilities(net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent event) {
        event.register(IPuppetHandler.class);
    }
}

