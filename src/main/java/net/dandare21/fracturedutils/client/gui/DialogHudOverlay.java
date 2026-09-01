package net.dandare21.fracturedutils.client.gui;

import net.dandare21.fracturedutils.dialog.DialogFormatUtil;
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.C2SDialogAdvancePacket;
import net.dandare21.fracturedutils.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DialogHudOverlay {
    private static String activeSpeaker = "";
    private static String activeText = "";
    private static int delayTicks = 40;
    private static int delayRemainingTicks = 40;
    private static boolean active = false;

    // Typewriter state
    private static int charSpeedTicks = 1;
    private static String letterSound = "";
    private static float letterSoundPitchMin = 0.8f;
    private static float letterSoundPitchMax = 1.2f;
    private static boolean waitForInput = true;

    private static int revealedCharCount = 0;
    private static int totalCharCount = 0;
    private static int charTickTimer = 0;

    // Multiplayer consensus player readiness state
    private static final List<UUID> readyPlayerUUIDs = new ArrayList<>();

    public static void setActiveDialog(String speaker, String text, int delayTicks, int charSpeedTicks, String letterSound, float letterSoundPitchMin, float letterSoundPitchMax, boolean waitForInput, boolean useCamera, double cameraX, double cameraY, double cameraZ, float cameraYaw, float cameraPitch, double cameraFov) {
        activeSpeaker = speaker != null ? speaker : "";
        activeText = text != null ? text : "";
        DialogHudOverlay.delayTicks = Math.max(1, delayTicks);
        DialogHudOverlay.delayRemainingTicks = DialogHudOverlay.delayTicks;

        DialogHudOverlay.charSpeedTicks = Math.max(0, charSpeedTicks);
        DialogHudOverlay.letterSound = letterSound != null ? letterSound.trim() : "";
        DialogHudOverlay.letterSoundPitchMin = letterSoundPitchMin > 0 ? letterSoundPitchMin : 0.8f;
        DialogHudOverlay.letterSoundPitchMax = letterSoundPitchMax > 0 ? letterSoundPitchMax : 1.2f;
        DialogHudOverlay.waitForInput = waitForInput;

        totalCharCount = DialogFormatUtil.getVisibleCharCount(activeText);
        if (DialogHudOverlay.charSpeedTicks == 0) {
            revealedCharCount = totalCharCount;
        } else {
            revealedCharCount = 0;
        }
        charTickTimer = 0;
        readyPlayerUUIDs.clear();
        active = true;

        if (useCamera) {
            net.dandare21.fracturedutils.client.camera.CustomCameraManager.setCustomCamera(cameraX, cameraY, cameraZ, cameraYaw, cameraPitch, true);
            net.dandare21.fracturedutils.client.camera.CustomCameraManager.setCustomFov(cameraFov);
        } else {
            net.dandare21.fracturedutils.client.camera.CustomCameraManager.clearCustomCamera();
        }
    }

    public static void updateReadyPlayers(List<UUID> readyUUIDs) {
        readyPlayerUUIDs.clear();
        if (readyUUIDs != null) {
            readyPlayerUUIDs.addAll(readyUUIDs);
        }
    }

    public static void clearActiveDialog() {
        active = false;
        activeSpeaker = "";
        activeText = "";
        readyPlayerUUIDs.clear();
        net.dandare21.fracturedutils.client.camera.CustomCameraManager.clearCustomCamera();
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean handleUserInput() {
        if (!active || !waitForInput) return false;

        // 1. First Press: If text is still revealing, reveal all text immediately!
        if (revealedCharCount < totalCharCount) {
            revealedCharCount = totalCharCount;
            return true;
        }

        // 2. Second Press: If text is fully revealed, send C2SDialogAdvancePacket to server!
        ModMessages.sendToServer(new C2SDialogAdvancePacket());
        return true;
    }

    public static void tick() {
        if (!active) return;

        if (revealedCharCount < totalCharCount) {
            if (charSpeedTicks == 0) {
                revealedCharCount = totalCharCount;
            } else {
                charTickTimer++;
                if (charTickTimer >= charSpeedTicks) {
                    charTickTimer = 0;
                    revealedCharCount++;

                    // Play sound blip on revealed character if non-whitespace
                    String currentRevealedText = DialogFormatUtil.getRevealedText(activeText, revealedCharCount);
                    if (!currentRevealedText.isEmpty()) {
                        char lastChar = currentRevealedText.charAt(currentRevealedText.length() - 1);
                        if (!Character.isWhitespace(lastChar)) {
                            playLetterSound();
                        }
                    }
                }
            }
        } else if (!waitForInput) {
            // Delay happens AFTER typewriter text has finished displaying!
            if (delayRemainingTicks > 0) {
                delayRemainingTicks--;
            } else {
                active = false;
            }
        }
    }

    private static void playLetterSound() {
        if (letterSound != null && !letterSound.isEmpty()) {
            try {
                float pitch = letterSoundPitchMin + (float) Math.random() * (Math.max(letterSoundPitchMin, letterSoundPitchMax) - Math.min(letterSoundPitchMin, letterSoundPitchMax));
                pitch = Math.max(0.1f, Math.min(2.0f, pitch));
                SoundEvent soundEvent = ModSounds.resolveSound(letterSound);
                if (soundEvent != null) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(soundEvent, pitch));
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static void render(GuiGraphics guiGraphics) {
        if (!active || activeText.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Sleek RPG Dialog Box Dimensions (Anchored to screen center)
        int boxW = Math.min(330, screenWidth - 20);
        int boxH = 58;
        int boxX = (screenWidth - boxW) / 2;
        int boxY = Math.min(screenHeight - boxH - 10, (screenHeight / 2) + 40);

        int alphaBits = 0xF5000000;

        // 1. Dark RPG Frame Background Fill
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, alphaBits | 0x05090C);

        // 2. Double Cyberpunk Border
        int borderColor = alphaBits | 0x00E5FF;
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + 1, borderColor);
        guiGraphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, borderColor);
        guiGraphics.fill(boxX, boxY, boxX + 1, boxY + boxH, borderColor);
        guiGraphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, borderColor);

        // Inner Border
        guiGraphics.fill(boxX + 2, boxY + 2, boxX + boxW - 2, boxY + 3, alphaBits | 0x4400E5FF);
        guiGraphics.fill(boxX + 2, boxY + boxH - 3, boxX + boxW - 2, boxY + boxH - 2, alphaBits | 0x4400E5FF);
        guiGraphics.fill(boxX + 2, boxY + 2, boxX + 3, boxY + boxH - 2, alphaBits | 0x4400E5FF);
        guiGraphics.fill(boxX + boxW - 3, boxY + 2, boxX + boxW - 2, boxY + boxH - 2, alphaBits | 0x4400E5FF);

        // Cyberpunk Corner Accent Notches
        guiGraphics.fill(boxX + 3, boxY + 3, boxX + 8, boxY + 5, borderColor);
        guiGraphics.fill(boxX + 3, boxY + 3, boxX + 5, boxY + 8, borderColor);

        guiGraphics.fill(boxX + boxW - 8, boxY + 3, boxX + boxW - 3, boxY + 5, borderColor);
        guiGraphics.fill(boxX + boxW - 5, boxY + 3, boxX + boxW - 3, boxY + 8, borderColor);

        guiGraphics.fill(boxX + 3, boxY + boxH - 5, boxX + 8, boxY + boxH - 3, borderColor);
        guiGraphics.fill(boxX + 3, boxY + boxH - 8, boxX + 5, boxY + boxH - 3, borderColor);

        guiGraphics.fill(boxX + boxW - 8, boxY + boxH - 5, boxX + boxW - 3, boxY + boxH - 3, borderColor);
        guiGraphics.fill(boxX + boxW - 5, boxY + boxH - 8, boxX + boxW - 3, boxY + boxH - 3, borderColor);

        int contentX = boxX + 16;
        int currentY = boxY + 8;
        int maxTextWidth = boxW - 32;

        // 3. Render Speaker Tag / Header Badge
        if (!activeSpeaker.isEmpty()) {
            Component speakerComp = DialogFormatUtil.formatText(activeSpeaker);
            int speakerWidth = mc.font.width(speakerComp);

            int badgeX = boxX + 12;
            int badgeY = boxY - 12;
            int badgeW = speakerWidth + 12;
            int badgeH = 14;

            guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, alphaBits | 0x0A1622);
            guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, borderColor);
            guiGraphics.fill(badgeX, badgeY, badgeX + 1, badgeY + badgeH, borderColor);
            guiGraphics.fill(badgeX + badgeW - 1, badgeY, badgeX + badgeW, badgeY + badgeH, borderColor);

            DialogFormatUtil.renderAnimatedText(guiGraphics, mc.font, activeSpeaker, badgeX + 6, badgeY + 3, badgeW, 0xFFFFFFFF);
            currentY += 4;
        }

        // 4. Render Typewriter Revealed Dialog Text with Custom Animated Effects
        String currentRevealedText = DialogFormatUtil.getRevealedText(activeText, revealedCharCount);
        DialogFormatUtil.renderAnimatedText(guiGraphics, mc.font, currentRevealedText, contentX, currentY, maxTextWidth, 0xFFFFFFFF);

        // 5. Render Blinking RPG Next Prompt Indicator (▼) and Ready Player Heads
        if (revealedCharCount >= totalCharCount) {
            int promptX = boxX + boxW - 20;
            int promptY = boxY + boxH - 16;

            boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
            if (blink) {
                String promptText = waitForInput ? "▼" : "…";
                int promptColor = waitForInput ? 0xFF00E5FF : 0xAA888888;
                guiGraphics.drawString(mc.font, promptText, promptX, promptY, promptColor);
            }

            // Render Player Face Icons next to the "next" icon for all players who pressed advance!
            if (!readyPlayerUUIDs.isEmpty() && mc.getConnection() != null) {
                int headSize = 12;
                int headX = promptX - 16;
                int headY = boxY + boxH - 18;

                for (UUID uuid : readyPlayerUUIDs) {
                    PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
                    if (info != null) {
                        ResourceLocation skinLoc = info.getSkinLocation();

                        // Cyan Border Frame
                        guiGraphics.fill(headX - 1, headY - 1, headX + headSize + 1, headY + headSize + 1, 0xFF00E5FF);
                        guiGraphics.fill(headX, headY, headX + headSize, headY + headSize, 0xFF000000);

                        // Draw Player Skin Face
                        PlayerFaceRenderer.draw(guiGraphics, skinLoc, headX, headY, headSize);

                        headX -= (headSize + 4);
                    }
                }
            }
        }
    }
}
