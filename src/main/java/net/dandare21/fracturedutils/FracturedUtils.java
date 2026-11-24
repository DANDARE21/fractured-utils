package net.dandare21.fracturedutils;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(FracturedUtils.MOD_ID)
public class FracturedUtils {
    public static final String MOD_ID = "fractured_utils";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FracturedUtils(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        net.dandare21.fracturedutils.particle.ModParticles.register(modEventBus);
        net.dandare21.fracturedutils.sound.ModSounds.register(modEventBus);
        net.dandare21.fracturedutils.puppet.registry.ModEntities.register(modEventBus);
        net.dandare21.fracturedutils.puppet.registry.ModItems.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::entityAttributeSetup);
        modEventBus.addListener(
                net.dandare21.fracturedutils.puppet.capability.PuppetCapabilityEvents::onRegisterCapabilities);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            net.dandare21.fracturedutils.network.ModMessages.register();
            net.dandare21.fracturedutils.config.ServerConfig.load();
        });
    }

    private void entityAttributeSetup(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(net.dandare21.fracturedutils.puppet.registry.ModEntities.VOID_HERALD.get(),
                net.dandare21.fracturedutils.puppet.boss.VoidHeraldBoss.createAttributes().build());
        event.put(net.dandare21.fracturedutils.puppet.registry.ModEntities.CUSTOM_NPC.get(),
                net.dandare21.fracturedutils.npc.entity.CustomNpcEntity.createAttributes().build());
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.SPAWN_EGGS) {
            event.accept(net.dandare21.fracturedutils.puppet.registry.ModItems.VOID_HERALD_SPAWN_EGG);
        }
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(net.dandare21.fracturedutils.puppet.registry.ModItems.NPC_WAND);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        net.dandare21.fracturedutils.sound.event.EventAudioManager.getInstance().onServerStarting(event.getServer());
        net.dandare21.fracturedutils.bossbar.BossHealthBarManager.getInstance().onServerStarting(event.getServer());
        net.dandare21.fracturedutils.region.RegionManager.getInstance().onServerStarting(event.getServer());
        net.dandare21.fracturedutils.npc.NpcDefinitionManager.getInstance().setServer(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        net.dandare21.fracturedutils.sound.event.EventAudioManager.getInstance().onServerStopping(event.getServer());
        net.dandare21.fracturedutils.bossbar.BossHealthBarManager.getInstance().onServerStopping(event.getServer());
        net.dandare21.fracturedutils.region.RegionManager.getInstance().onServerStopping(event.getServer());
    }

}
