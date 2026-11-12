package net.dandare21.fracturedutils.client.gui;

import net.dandare21.fracturedutils.client.camera.CustomCameraManager;
import net.dandare21.fracturedutils.dialog.DialogFormatUtil;
import net.dandare21.fracturedutils.dialog.style.DialogStyle;
import net.dandare21.fracturedutils.dialog.style.DialogStyleRegistry;
import net.dandare21.fracturedutils.dialog.style.NineSliceRenderer;
import net.dandare21.fracturedutils.dialog.text.DialogTextParser;
import net.dandare21.fracturedutils.dialog.text.DialogTextToken;
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.C2SDialogAdvancePacket;
import net.dandare21.fracturedutils.screeneffect.client.ClientScreenEffectHandler;
import net.dandare21.fracturedutils.screeneffect.effects.ScreenShakeEffect;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

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
    private static int pauseTicksRemaining = 0;
    private static int tokenIndex = 0;
    private static List<DialogTextToken.Token> parsedTokens = new ArrayList<>();

    // Styling & Theme
    private static DialogStyle activeStyle = DialogStyleRegistry.CYBERPUNK;

    // Multiplayer consensus player readiness state
    private static final List<UUID> readyPlayerUUIDs = new ArrayList<>();
    private static boolean dialogSetCamera = false;

    // Render Performance Caching
    private static int lastRenderGuiTicks = -1;
    private static float lastRenderPartialTick = -1.0f;
    private static int lastRevealedCharCount = -1;
    private static String lastActiveText = "";
    private static String cachedRevealedText = "";
    private static String lastSpeaker = "";
    private static int cachedSpeakerWidth = 0;

    public static void setActiveDialog(
            String speaker, String text, int delayTicks, int charSpeedTicks,
            String letterSound, float letterSoundPitchMin, float letterSoundPitchMax,
            boolean waitForInput, boolean useCamera, double cameraX, double cameraY, double cameraZ,
            float cameraYaw, float cameraPitch, double cameraFov, int targetEntityId,
            String anchor, double offsetX, double offsetY, double offsetZ, boolean lookAtTarget,
            int interpolationTicks, String easing, String styleId
    ) {
        activeSpeaker = speaker != null ? speaker : "";
        activeText = text != null ? text : "";
        DialogHudOverlay.delayTicks = Math.max(1, delayTicks);
        DialogHudOverlay.delayRemainingTicks = DialogHudOverlay.delayTicks;

        DialogHudOverlay.charSpeedTicks = Math.max(0, charSpeedTicks);
        DialogHudOverlay.letterSound = letterSound != null ? letterSound.trim() : "";
        DialogHudOverlay.letterSoundPitchMin = letterSoundPitchMin > 0 ? letterSoundPitchMin : 0.8f;
        DialogHudOverlay.letterSoundPitchMax = letterSoundPitchMax > 0 ? letterSoundPitchMax : 1.2f;
        DialogHudOverlay.waitForInput = waitForInput;

        activeStyle = DialogStyleRegistry.get(styleId);

        // Parse text into token stream with rich formatting, mid-text events and punctuation pacing
        parsedTokens = DialogTextParser.parse(activeText, activeStyle.defaultTextColor());
        totalCharCount = DialogTextParser.countVisibleChars(parsedTokens);
        tokenIndex = 0;
        pauseTicksRemaining = 0;
        charTickTimer = 0;

        if (DialogHudOverlay.charSpeedTicks == 0) {
            revealedCharCount = totalCharCount;
            // Execute all instant tokens up to end
            while (tokenIndex < parsedTokens.size()) {
                executeToken(parsedTokens.get(tokenIndex++));
            }
        } else {
            revealedCharCount = 0;
        }

        readyPlayerUUIDs.clear();
        active = true;

        if (useCamera) {
            dialogSetCamera = true;
            Minecraft mc = Minecraft.getInstance();
            Entity target = null;
            if (targetEntityId >= 0 && mc.level != null) {
                target = mc.level.getEntity(targetEntityId);
            }
            CustomCameraManager.setDialogCamera(
                    target, anchor, new Vec3(offsetX, offsetY, offsetZ), lookAtTarget,
                    cameraYaw, cameraPitch, cameraX, cameraY, cameraZ, cameraYaw, cameraPitch, cameraFov,
                    interpolationTicks, easing
            );
        } else {
            if (dialogSetCamera && CustomCameraManager.getActiveTrack() == null) {
                CustomCameraManager.clearCustomCamera();
            }
            dialogSetCamera = false;
        }
    }

    public static void setActiveDialog(String speaker, String text, int delayTicks, int charSpeedTicks, String letterSound, float letterSoundPitchMin, float letterSoundPitchMax, boolean waitForInput, boolean useCamera, double cameraX, double cameraY, double cameraZ, float cameraYaw, float cameraPitch, double cameraFov) {
        setActiveDialog(speaker, text, delayTicks, charSpeedTicks, letterSound, letterSoundPitchMin, letterSoundPitchMax, waitForInput, useCamera, cameraX, cameraY, cameraZ, cameraYaw, cameraPitch, cameraFov, -1, "eyes", 0, 0, 0, true, 20, "EASE_IN_OUT_CUBIC", "fractured_utils:cyberpunk");
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
        parsedTokens.clear();
        readyPlayerUUIDs.clear();
        lastRenderGuiTicks = -1;
        lastRenderPartialTick = -1.0f;
        lastRevealedCharCount = -1;
        lastActiveText = "";
        cachedRevealedText = "";
        lastSpeaker = "";
        cachedSpeakerWidth = 0;
        if (dialogSetCamera && CustomCameraManager.getActiveTrack() == null) {
            CustomCameraManager.clearCustomCamera();
        }
        dialogSetCamera = false;
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * Two-Stage Advance Handler:
     * Stage 1: If typewriter is printing or in pause, instantly fast-forward text to 100%.
     * Stage 2: If line is fully printed, send C2SDialogAdvancePacket to advance line.
     */
    public static boolean handleUserInput() {
        if (!active || !waitForInput) return false;

        // Stage 1: Fast-Forward Typewriter
        if (revealedCharCount < totalCharCount || pauseTicksRemaining > 0) {
            revealedCharCount = totalCharCount;
            pauseTicksRemaining = 0;
            // Execute all remaining inline triggers
            while (tokenIndex < parsedTokens.size()) {
                executeToken(parsedTokens.get(tokenIndex++));
            }
            return true;
        }

        // Stage 2: Advance Dialogue Line
        ModMessages.sendToServer(new C2SDialogAdvancePacket());
        return true;
    }

    public static void tick() {
        if (!active) return;

        if (revealedCharCount < totalCharCount) {
            if (charSpeedTicks == 0) {
                revealedCharCount = totalCharCount;
                while (tokenIndex < parsedTokens.size()) {
                    executeToken(parsedTokens.get(tokenIndex++));
                }
            } else {
                if (pauseTicksRemaining > 0) {
                    pauseTicksRemaining--;
                    return;
                }

                charTickTimer++;
                if (charTickTimer >= charSpeedTicks) {
                    charTickTimer = 0;

                    // Advance through tokens until next text character is revealed
                    while (tokenIndex < parsedTokens.size()) {
                        DialogTextToken.Token token = parsedTokens.get(tokenIndex++);
                        if (token instanceof DialogTextToken.TextChar tc) {
                            revealedCharCount++;
                            if (!Character.isWhitespace(tc.character())) {
                                playLetterSound();
                            }
                            break;
                        } else {
                            executeToken(token);
                            if (pauseTicksRemaining > 0) {
                                break;
                            }
                        }
                    }
                }
            }
        } else if (!waitForInput) {
            // Post-typewriter display delay for automated lines
            if (delayRemainingTicks > 0) {
                delayRemainingTicks--;
            } else {
                ModMessages.sendToServer(new C2SDialogAdvancePacket());
                clearActiveDialog();
            }
        }
    }

    private static void executeToken(DialogTextToken.Token token) {
        if (token instanceof DialogTextToken.Pause p) {
            pauseTicksRemaining = p.ticks();
        } else if (token instanceof DialogTextToken.ScreenShake s) {
            ClientScreenEffectHandler.playEffect(new ScreenShakeEffect.ScreenShakeInstance(s.durationTicks() * 50, s.intensity(), 20.0f, true));
        } else if (token instanceof DialogTextToken.SoundTrigger st) {
            try {
                SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(st.sound());
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(soundEvent, st.pitch(), st.volume()));
            } catch (Exception ignored) {}
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
            } catch (Exception ignored) {}
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

        // Frame Guard: Ensure we never render more than once per frame even if invoked repeatedly
        int currentGuiTicks = mc.gui.getGuiTicks();
        float currentPartialTick = mc.getFrameTime();
        if (currentGuiTicks == lastRenderGuiTicks && currentPartialTick == lastRenderPartialTick) {
            return;
        }
        lastRenderGuiTicks = currentGuiTicks;
        lastRenderPartialTick = currentPartialTick;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Dialog Box Dimensions
        int boxW = Math.min(340, screenWidth - 20);
        int boxH = 58;
        int boxX = (screenWidth - boxW) / 2;
        int boxY = Math.min(screenHeight - boxH - 12, (screenHeight / 2) + 40);

        // 1. Render Styled Dialog Frame using NineSliceRenderer
        NineSliceRenderer.renderFrame(guiGraphics, activeStyle, boxX, boxY, boxW, boxH);

        int contentX = boxX + activeStyle.paddingLeft();
        int currentY = boxY + activeStyle.paddingTop();
        int maxTextWidth = boxW - activeStyle.paddingLeft() - activeStyle.paddingRight();

        // 2. Render Speaker Badge Header
        if (!activeSpeaker.isEmpty()) {
            if (!activeSpeaker.equals(lastSpeaker)) {
                lastSpeaker = activeSpeaker;
                Component speakerComp = DialogFormatUtil.formatText(activeSpeaker);
                cachedSpeakerWidth = mc.font.width(speakerComp);
            }
            int speakerWidth = cachedSpeakerWidth;

            int badgeX = boxX + 12;
            int badgeY = boxY - 12;
            int badgeW = speakerWidth + 12;
            int badgeH = 14;

            guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, activeStyle.speakerBadgeBgColor());
            int badgeBorder = activeStyle.speakerBadgeBorderColor();
            guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, badgeBorder);
            guiGraphics.fill(badgeX, badgeY + 1, badgeX + 1, badgeY + badgeH, badgeBorder);
            guiGraphics.fill(badgeX + badgeW - 1, badgeY + 1, badgeX + badgeW, badgeY + badgeH, badgeBorder);

            DialogFormatUtil.renderAnimatedText(guiGraphics, mc.font, activeSpeaker, badgeX + 6, badgeY + 3, badgeW, activeStyle.speakerTextColor());
            currentY += 4;
        }

        // 3. Render Typewriter Revealed Dialog Text
        if (revealedCharCount != lastRevealedCharCount || !activeText.equals(lastActiveText)) {
            lastRevealedCharCount = revealedCharCount;
            lastActiveText = activeText;
            cachedRevealedText = DialogFormatUtil.getRevealedText(activeText, revealedCharCount);
        }
        DialogFormatUtil.renderAnimatedText(guiGraphics, mc.font, cachedRevealedText, contentX, currentY, maxTextWidth, activeStyle.defaultTextColor());

        // 4. Render Blinking Advance Prompt Indicator (▼) and Ready Player Heads
        if (revealedCharCount >= totalCharCount) {
            int promptX = boxX + boxW - 20;
            int promptY = boxY + boxH - 16;

            boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
            if (blink) {
                String promptText = waitForInput ? "▼" : "…";
                int promptColor = waitForInput ? activeStyle.borderColor() : 0xAA888888;
                guiGraphics.drawString(mc.font, promptText, promptX, promptY, promptColor);
            }

            // Render Player Face Icons next to the "next" icon for all players who pressed advance
            if (!readyPlayerUUIDs.isEmpty() && mc.getConnection() != null) {
                int headSize = 12;
                int headX = promptX - 16;
                int headY = boxY + boxH - 18;

                for (UUID uuid : readyPlayerUUIDs) {
                    PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
                    if (info != null) {
                        ResourceLocation skinLoc = info.getSkinLocation();

                        guiGraphics.fill(headX - 1, headY - 1, headX + headSize + 1, headY + headSize + 1, activeStyle.borderColor());
                        guiGraphics.fill(headX, headY, headX + headSize, headY + headSize, 0xFF000000);

                        PlayerFaceRenderer.draw(guiGraphics, skinLoc, headX, headY, headSize);
                        headX -= (headSize + 4);
                    }
                }
            }
        }
    }
}
