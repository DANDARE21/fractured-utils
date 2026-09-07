package net.dandare21.fracturedutils.client;

import net.dandare21.fracturedutils.client.gui.MusicSequenceSelectScreen;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Map;

public class ClientMusicSequenceHandler {
    public static void openScreen(Map<String, String> sequenceFiles, List<String> availableTracks) {
        Minecraft.getInstance().setScreen(new MusicSequenceSelectScreen(sequenceFiles, availableTracks));
    }
}
