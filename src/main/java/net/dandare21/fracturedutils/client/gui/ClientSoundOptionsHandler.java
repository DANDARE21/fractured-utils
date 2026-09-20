package net.dandare21.fracturedutils.client.gui;

import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.sound.event.ClientAudioConfig;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;

@Mod.EventBusSubscriber(modid = FracturedUtils.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientSoundOptionsHandler {

    private static final OptionInstance<Double> EVENT_MUSIC_OPTION = new OptionInstance<>(
            "options.event_music_volume",
            val -> null,
            (caption, value) -> {
                int pct = (int) Math.round(value * 100.0);
                return Component.literal("Event Music: " + (pct == 0 ? "OFF" : pct + "%"));
            },
            OptionInstance.UnitDouble.INSTANCE,
            (double) ClientAudioConfig.getEventMusicVolume(),
            val -> ClientAudioConfig.setEventMusicVolume(val.floatValue())
    );

    private static final OptionInstance<Double> EVENT_AMBIENCE_OPTION = new OptionInstance<>(
            "options.event_ambience_volume",
            val -> null,
            (caption, value) -> {
                int pct = (int) Math.round(value * 100.0);
                return Component.literal("Event Ambience: " + (pct == 0 ? "OFF" : pct + "%"));
            },
            OptionInstance.UnitDouble.INSTANCE,
            (double) ClientAudioConfig.getEventAmbienceVolume(),
            val -> ClientAudioConfig.setEventAmbienceVolume(val.floatValue())
    );

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof SoundOptionsScreen screen) {
            try {
                for (Field field : SoundOptionsScreen.class.getDeclaredFields()) {
                    if (OptionsList.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        OptionsList optionsList = (OptionsList) field.get(screen);
                        if (optionsList != null) {
                            optionsList.addSmall(EVENT_MUSIC_OPTION, EVENT_AMBIENCE_OPTION);
                            FracturedUtils.LOGGER.info("[ClientSoundOptionsHandler] Added Event Music & Ambience volume sliders into SoundOptionsScreen OptionsList.");
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                FracturedUtils.LOGGER.warn("[ClientSoundOptionsHandler] Failed to inject sound options into SoundOptionsScreen OptionsList", e);
            }
        }
    }
}
