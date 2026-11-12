package net.dandare21.fracturedutils.client;

import net.dandare21.fracturedutils.client.gui.DialogHudOverlay;
import net.dandare21.fracturedutils.client.gui.DialogScreen;
import net.minecraft.client.Minecraft;

import java.util.Map;

public class ClientDialogHandler {
    public static void openScreen(Map<String, String> sequenceFiles) {
        Minecraft.getInstance().setScreen(new DialogScreen(sequenceFiles));
    }

    public static void displayDialog(
            String speaker, String text, int delayTicks, int charSpeedTicks,
            String letterSound, float letterSoundPitchMin, float letterSoundPitchMax,
            boolean waitForInput, boolean useCamera, double cameraX, double cameraY, double cameraZ,
            float cameraYaw, float cameraPitch, double cameraFov
    ) {
        displayDialog(speaker, text, delayTicks, charSpeedTicks, letterSound, letterSoundPitchMin, letterSoundPitchMax,
                waitForInput, useCamera, cameraX, cameraY, cameraZ, cameraYaw, cameraPitch, cameraFov,
                -1, "eyes", 0.0, 0.0, 0.0, true, 20, "EASE_IN_OUT_CUBIC", "fractured_utils:cyberpunk");
    }

    public static void displayDialog(
            String speaker, String text, int delayTicks, int charSpeedTicks,
            String letterSound, float letterSoundPitchMin, float letterSoundPitchMax,
            boolean waitForInput, boolean useCamera, double cameraX, double cameraY, double cameraZ,
            float cameraYaw, float cameraPitch, double cameraFov, int targetEntityId,
            String anchor, double offsetX, double offsetY, double offsetZ, boolean lookAtTarget,
            int interpolationTicks, String easing, String styleId
    ) {
        DialogHudOverlay.setActiveDialog(
                speaker, text, delayTicks, charSpeedTicks, letterSound, letterSoundPitchMin, letterSoundPitchMax,
                waitForInput, useCamera, cameraX, cameraY, cameraZ, cameraYaw, cameraPitch, cameraFov,
                targetEntityId, anchor, offsetX, offsetY, offsetZ, lookAtTarget, interpolationTicks, easing, styleId
        );
    }
}
