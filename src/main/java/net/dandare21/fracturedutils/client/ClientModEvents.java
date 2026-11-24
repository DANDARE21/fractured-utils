package net.dandare21.fracturedutils.client;

import net.dandare21.fracturedutils.FracturedUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = FracturedUtils.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.dandare21.fracturedutils.client.animation.PlayerAnimationManager.init();
            net.dandare21.fracturedutils.sound.DialogResourcePackGenerator.generateIfMissing();
            net.dandare21.fracturedutils.sound.event.ClientAudioPackManager.getInstance().init();
        });
    }

    @SubscribeEvent
    public static void registerEntityRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                net.dandare21.fracturedutils.puppet.registry.ModEntities.VOID_HERALD.get(),
                net.dandare21.fracturedutils.puppet.client.VoidHeraldRenderer::new
        );
        event.registerEntityRenderer(
                net.dandare21.fracturedutils.puppet.registry.ModEntities.CUSTOM_NPC.get(),
                net.dandare21.fracturedutils.npc.client.CustomNpcRenderer::new
        );
    }

    @SubscribeEvent
    public static void registerParticleProviders(net.minecraftforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(net.dandare21.fracturedutils.particle.ModParticles.MARKER_PARTICLE.get(), net.dandare21.fracturedutils.particle.MarkerParticle.Provider::new);
        event.registerSpriteSet(net.dandare21.fracturedutils.particle.ModParticles.DOWNED_MARKER.get(), net.dandare21.fracturedutils.particle.MarkerParticle.Provider::new);
    }
}

