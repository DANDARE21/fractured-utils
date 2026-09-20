package net.dandare21.fracturedutils.client;

import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.client.gui.WaitingRoomScreen;
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.network.packet.JoinWaitingRoomC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FracturedUtils.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientEventHandler {

    public static int holdTicks = 0;
    public static int ticksSinceLastSound = 0;
    public static final int MAX_HOLD_TICKS = 30; // 1.5 Seconds hold time
    public static float smoothHoldProgress = 0.0f;
    private static float smoothReviveProgress = 0.0f;

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (ClientCutsceneHandler.getInstance().isCinematicPlaying()) {
            event.setSound(null);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof net.dandare21.fracturedutils.client.gui.TeamWipeScreen) {
            if (event.getSound() != null && event.getSound().getSource() != net.minecraft.sounds.SoundSource.MASTER) {
                event.setSound(null);
            }
        }
    }

    private static final net.minecraft.resources.ResourceLocation ICONS_FONT = new net.minecraft.resources.ResourceLocation(FracturedUtils.MOD_ID, "icons");

    @SubscribeEvent
    public static void onRenderNameTag(net.minecraftforge.client.event.RenderNameTagEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            if (ClientDownedData.isPlayerDowned(player.getUUID())) {
                event.setResult(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
                Component original = event.getContent();
                Component downedIcon = Component.literal("\uE001")
                        .withStyle(style -> style.withFont(ICONS_FONT).withColor(0xFFFFFFFF));
                Component space = Component.literal(" ");
                event.setContent(Component.empty().append(downedIcon).append(space).append(original.copy().withStyle(net.minecraft.ChatFormatting.WHITE)));
            }
        }
    }

    @SubscribeEvent
    public static void onClientChat(ClientChatReceivedEvent event) {
        ClientWaitingRoomData.addChatMessage(event.getMessage());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

        net.dandare21.fracturedutils.client.gui.DialogHudOverlay.tick();
        net.dandare21.fracturedutils.client.camera.ClientCameraHandler.clientTick();
        net.dandare21.fracturedutils.screeneffect.client.ClientScreenEffectHandler.clientTick();
        net.dandare21.fracturedutils.bossbar.client.ClientBossBarManager.getInstance().clientTick();
        ClientCutsceneHandler.getInstance().onClientTick();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            holdTicks = 0;
            ticksSinceLastSound = 0;
            return;
        }

        if (ClientDownedData.isDowned()) {
            if (!(mc.screen instanceof net.dandare21.fracturedutils.client.gui.DownedSpectateScreen) && !(mc.screen instanceof net.dandare21.fracturedutils.client.gui.TeamWipeScreen)) {
                mc.setScreen(new net.dandare21.fracturedutils.client.gui.DownedSpectateScreen());
            }
        }

        boolean isOp = mc.player.hasPermissions(2);

        if (ClientWaitingRoomData.isActive() && mc.screen == null) {
            if (isOp) {
                if (ModKeyBindings.WAITING_ROOM_KEY.consumeClick()) {
                    holdTicks = 0;
                    ticksSinceLastSound = 0;
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.4f, 0.6f));
                    ModMessages.sendToServer(new JoinWaitingRoomC2SPacket());
                    mc.setScreen(new WaitingRoomScreen());
                }
            } else if (ModKeyBindings.WAITING_ROOM_KEY.isDown()) {
                holdTicks++;
                ticksSinceLastSound++;

                float progress = (float) holdTicks / MAX_HOLD_TICKS;
                int targetInterval = Math.max(1, Math.round(1.0f + (progress * 4.0f)));

                if (ticksSinceLastSound >= targetInterval) {
                    ticksSinceLastSound = 0;
                    float pitch = 0.8f + (progress * 0.8f);
                    mc.getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch, 0.3f));
                }

                if (holdTicks >= MAX_HOLD_TICKS) {
                    holdTicks = 0;
                    ticksSinceLastSound = 0;
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.4f, 0.6f));
                    ModMessages.sendToServer(new JoinWaitingRoomC2SPacket());
                    mc.setScreen(new WaitingRoomScreen());
                }
            } else {
                holdTicks = 0;
                ticksSinceLastSound = 0;
            }
        } else {
            holdTicks = 0;
            ticksSinceLastSound = 0;
        }
    }

    @SubscribeEvent
    public static void onRenderGuiOverlayPre(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            net.dandare21.fracturedutils.screeneffect.client.ClientScreenEffectHandler.renderScreenEffects(event.getGuiGraphics(), event.getPartialTick());
        }
        Minecraft mc = Minecraft.getInstance();
        if (ClientDownedData.isDowned() || mc.screen instanceof net.dandare21.fracturedutils.client.gui.DownedSpectateScreen || mc.screen instanceof net.dandare21.fracturedutils.client.gui.TeamWipeScreen) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(net.minecraftforge.client.event.RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (ClientDownedData.isDowned() || net.dandare21.fracturedutils.client.camera.CustomCameraManager.isActive() || mc.screen instanceof net.dandare21.fracturedutils.client.gui.DownedSpectateScreen || mc.screen instanceof net.dandare21.fracturedutils.client.gui.TeamWipeScreen) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMovementInput(net.minecraftforge.client.event.MovementInputUpdateEvent event) {
        if (net.dandare21.fracturedutils.client.camera.CustomCameraManager.isActive()) {
            event.getInput().forwardImpulse = 0.0F;
            event.getInput().leftImpulse = 0.0F;
            event.getInput().up = false;
            event.getInput().down = false;
            event.getInput().left = false;
            event.getInput().right = false;
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        ClientCutsceneHandler.getInstance().renderOverlay(event);

        if (event.getOverlay().id().equals(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id())) {
            net.dandare21.fracturedutils.bossbar.client.ClientBossBarManager.getInstance().render(event.getGuiGraphics(), event.getPartialTick());
        }

        if (event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            Minecraft mc = Minecraft.getInstance();
            GuiGraphics guiGraphics = event.getGuiGraphics();

            // Render Objective HUD Overlay in Top-Left
            net.dandare21.fracturedutils.client.gui.ObjectiveHudOverlay.render(guiGraphics);

            // Render OP Active Sequence Monitor HUD Overlay
            ClientOpMonitorData.renderHudOverlay(guiGraphics);

            // Smoothly interpolate revive progress for UI
            float targetProg = ClientDownedData.getReviveProgress();
            if (Math.abs(smoothReviveProgress - targetProg) > 0.0001f) {
                smoothReviveProgress += (targetProg - smoothReviveProgress) * 0.2f;
                if (targetProg == 0.0f && smoothReviveProgress < 0.005f) {
                    smoothReviveProgress = 0.0f;
                }
            }

            // Render Downed & Revive HUD Overlay
            if (ClientDownedData.isDowned() && mc.player != null) {
                int screenWidth = mc.getWindow().getGuiScaledWidth();
                int screenHeight = mc.getWindow().getGuiScaledHeight();

                boolean beingRevived = smoothReviveProgress > 0.001f;
                int boxW = 240;
                int boxH = beingRevived ? 62 : 45;
                int boxX = (screenWidth - boxW) / 2;
                int boxY = screenHeight - (beingRevived ? 95 : 80);

                // 1. Dark Base Panel Fill
                guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xEE140505);

                // 2. Cyberpunk Borders & Content
                guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + 1, 0xFFFF2222);
                guiGraphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0xFFFF2222);
                guiGraphics.fill(boxX, boxY, boxX + 1, boxY + boxH, 0xFFFF2222);
                guiGraphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, 0xFFFF2222);

                guiGraphics.drawCenteredString(mc.font, "CRITICAL: YOU ARE DOWNED!", screenWidth / 2, boxY + 8, 0xFFFF3333);
                guiGraphics.drawCenteredString(mc.font, "Wait for a teammate to stand nearby & hold RIGHT CLICK to revive", screenWidth / 2, boxY + 22, 0xFFCCCCCC);

                if (beingRevived) {
                    String statusText = String.format(java.util.Locale.US, "BEING REVIVED  //  %d%%", (int)(smoothReviveProgress * 100));
                    guiGraphics.drawCenteredString(mc.font, Component.literal(statusText).withStyle(net.minecraft.ChatFormatting.BOLD), screenWidth / 2, boxY + 36, 0xFF00FF55);

                    int trackX = boxX + 10;
                    int trackY = boxY + boxH - 10;
                    int trackW = boxW - 20;
                    int trackH = 5;

                    guiGraphics.fill(trackX - 1, trackY - 1, trackX + trackW + 1, trackY + trackH + 1, 0x6600FF55);
                    guiGraphics.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0xFF02160C);
                    int fillW = (int) (trackW * smoothReviveProgress);
                    if (fillW > 0) {
                        guiGraphics.fill(trackX, trackY, trackX + fillW, trackY + trackH, 0xFF00FF55);
                        guiGraphics.fill(trackX + Math.max(0, fillW - 3), trackY, trackX + fillW, trackY + trackH, 0xFFFFFFFF);
                    }
                }
            } else if (ClientDownedData.isRevivingOther() && mc.player != null && smoothReviveProgress > 0.001f) {
                int screenWidth = mc.getWindow().getGuiScaledWidth();
                int screenHeight = mc.getWindow().getGuiScaledHeight();

                int boxW = 220;
                int boxH = 44;
                int boxX = (screenWidth - boxW) / 2;
                int boxY = screenHeight - 82;

                // 1. Dark Base Panel Fill
                guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xEE051A0B);

                // 2. Cyberpunk Borders & Corner Cutouts
                guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + 1, 0xFF00FF66);
                guiGraphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0xFF00FF66);
                guiGraphics.fill(boxX, boxY, boxX + 1, boxY + boxH, 0xFF00FF66);
                guiGraphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, 0xFF00FF66);

                guiGraphics.fill(boxX + 2, boxY + 2, boxX + 6, boxY + 3, 0xFF00FF66);
                guiGraphics.fill(boxX + 2, boxY + 2, boxX + 3, boxY + 6, 0xFF00FF66);
                guiGraphics.fill(boxX + boxW - 6, boxY + 2, boxX + boxW - 2, boxY + 3, 0xFF00FF66);
                guiGraphics.fill(boxX + boxW - 3, boxY + 2, boxX + boxW - 2, boxY + 6, 0xFF00FF66);

                String text = String.format(java.util.Locale.US, "REVIVING TEAMMATE  //  %d%%", (int)(smoothReviveProgress * 100));
                guiGraphics.drawCenteredString(mc.font, Component.literal(text).withStyle(net.minecraft.ChatFormatting.BOLD), screenWidth / 2, boxY + 8, 0xFF00FF66);

                int trackX = boxX + 10;
                int trackY = boxY + boxH - 14;
                int trackW = boxW - 20;
                int trackH = 6;

                guiGraphics.fill(trackX - 1, trackY - 1, trackX + trackW + 1, trackY + trackH + 1, 0x6600FF66);
                guiGraphics.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0xFF02160C);
                int fillW = (int) (trackW * smoothReviveProgress);
                if (fillW > 0) {
                    guiGraphics.fill(trackX, trackY, trackX + fillW, trackY + trackH, 0xFF00FF66);
                    guiGraphics.fill(trackX + Math.max(0, fillW - 3), trackY, trackX + fillW, trackY + trackH, 0xFFFFFFFF);
                }
            }

            // Render Waiting Room HUD Overlay
            if (ClientWaitingRoomData.isActive() && !(mc.screen instanceof WaitingRoomScreen)) {
                int screenWidth = mc.getWindow().getGuiScaledWidth();

                int totalConnected = 0;
                if (mc.getConnection() != null && mc.getConnection().getOnlinePlayers() != null) {
                    totalConnected = mc.getConnection().getOnlinePlayers().size();
                }

                boolean isOp = mc.player != null && mc.player.hasPermissions(2);
                boolean isCountdown = ClientWaitingRoomData.isCountingDown();
                boolean isEveryoneReady = ClientWaitingRoomData.isEveryoneReady(totalConnected);

                int borderColor = isCountdown ? 0xFFFF3355 : (isEveryoneReady ? 0xFF00FF55 : 0xFF00E5FF);
                int innerColor = isCountdown ? 0x44FF3355 : (isEveryoneReady ? 0x4400FF55 : 0x4400E5FF);
                int titleColor = isCountdown ? 0xFFFF3355 : (isEveryoneReady ? 0xFF00FF55 : 0xFF00E5FF);
                net.minecraft.ChatFormatting titleStyle = isCountdown ? net.minecraft.ChatFormatting.RED
                        : (isEveryoneReady ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.AQUA);
                net.minecraft.ChatFormatting statsStyle = isCountdown ? net.minecraft.ChatFormatting.RED
                        : (isEveryoneReady ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.YELLOW);
                net.minecraft.ChatFormatting promptStyle = isCountdown ? net.minecraft.ChatFormatting.RED
                        : (isEveryoneReady ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.AQUA);

                String titleStr = ClientWaitingRoomData.getRoomTitle().toUpperCase();
                Component titleText = Component.literal(titleStr).withStyle(net.minecraft.ChatFormatting.BOLD, titleStyle);

                int joinedCount = ClientWaitingRoomData.getPlayerUUIDs().size();

                long remaining = isCountdown ? ClientWaitingRoomData.getCountdownRemainingSeconds() : 0;
                String timeStr = isCountdown
                        ? Component
                                .translatable("gui.fracturedutils.waiting_room.starting_in", remaining / 60, remaining % 60)
                                .getString()
                        : String.format("%02d:%02d", ClientWaitingRoomData.getElapsedSeconds() / 60,
                                ClientWaitingRoomData.getElapsedSeconds() % 60);

                Component statsText = Component
                        .translatable("gui.fracturedutils.waiting_room.hud_players", joinedCount, totalConnected)
                        .append(Component.literal("   |   " + timeStr))
                        .withStyle(statsStyle, net.minecraft.ChatFormatting.BOLD);

                String keyName = ModKeyBindings.WAITING_ROOM_KEY.getTranslatedKeyMessage().getString().toUpperCase();
                Component promptPrefix = Component.translatable(
                        isOp ? "gui.fracturedutils.waiting_room.press" : "gui.fracturedutils.waiting_room.hold");
                Component enterPrompt = Component.translatable("gui.fracturedutils.waiting_room.enter_prompt");

                Component promptText = Component.literal("[").withStyle(promptStyle, net.minecraft.ChatFormatting.BOLD)
                        .append(promptPrefix.getString() + " ")
                        .append(Component.literal(keyName).withStyle(net.minecraft.ChatFormatting.WHITE,
                                net.minecraft.ChatFormatting.BOLD))
                        .append(enterPrompt)
                        .withStyle(promptStyle, net.minecraft.ChatFormatting.BOLD);

                int w1 = mc.font.width(titleText);
                int w2 = mc.font.width(statsText);
                int w3 = mc.font.width(promptText);
                int maxW = Math.max(w1, Math.max(w2, w3));

                int cardW = maxW + 32;
                int cardH = 48;
                int x = (screenWidth - cardW) / 2;
                int y = 10;

                int fillColor = 0xEE08121B;

                guiGraphics.fill(x, y, x + cardW, y + cardH, fillColor);
                guiGraphics.fill(x, y, x + cardW, y + 1, borderColor);
                guiGraphics.fill(x, y + cardH - 1, x + cardW, y + cardH, borderColor);
                guiGraphics.fill(x, y, x + 1, y + cardH, borderColor);
                guiGraphics.fill(x + cardW - 1, y, x + cardW, y + cardH, borderColor);

                guiGraphics.fill(x + 2, y + 2, x + cardW - 2, y + 3, innerColor);
                guiGraphics.fill(x + 2, y + cardH - 3, x + cardW - 2, y + cardH - 2, innerColor);

                guiGraphics.fill(x, y, x + 5, y + 2, borderColor);
                guiGraphics.fill(x, y, x + 2, y + 5, borderColor);
                guiGraphics.fill(x + cardW - 5, y, x + cardW, y + 2, borderColor);
                guiGraphics.fill(x + cardW - 2, y, x + cardW, y + 5, borderColor);
                guiGraphics.fill(x, y + cardH - 2, x + 5, y + cardH, borderColor);
                guiGraphics.fill(x, y + cardH - 5, x + 2, y + cardH, borderColor);
                guiGraphics.fill(x + cardW - 5, y + cardH - 2, x + cardW, y + cardH, borderColor);
                guiGraphics.fill(x + cardW - 2, y + cardH - 5, x + cardW, y + cardH, borderColor);

                guiGraphics.drawCenteredString(mc.font, titleText, x + (cardW / 2), y + 6, titleColor);
                guiGraphics.drawCenteredString(mc.font, statsText, x + (cardW / 2), y + 19, 0xFFFFFFFF);
                guiGraphics.drawCenteredString(mc.font, promptText, x + (cardW / 2), y + 32, 0xFFFFFFFF);

                float partialTick = event.getPartialTick();
                if (holdTicks > 0) {
                    float rawProgress = Math.min(1.0f,
                            (holdTicks + (ModKeyBindings.WAITING_ROOM_KEY.isDown() ? partialTick : -partialTick))
                                    / MAX_HOLD_TICKS);
                    smoothHoldProgress = smoothHoldProgress + (rawProgress - smoothHoldProgress) * 0.4f;
                } else {
                    smoothHoldProgress = smoothHoldProgress * 0.6f;
                }

                int barX = x + 4;
                int barY = y + cardH - 3;
                int barWidth = cardW - 8;
                int barHeight = 2;

                guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0x55050B10);

                int filledWidth = (int) (barWidth * Math.max(0.0f, Math.min(1.0f, smoothHoldProgress)));
                if (filledWidth > 0) {
                    int progressColor = smoothHoldProgress >= 0.95f ? 0xFF00FF55 : borderColor;
                    guiGraphics.fill(barX, barY, barX + filledWidth, barY + barHeight, progressColor);
                }
            }
        }
        net.dandare21.fracturedutils.client.gui.DialogHudOverlay.render(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onInteraction(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        if (ClientDownedData.isDowned()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDialogKeyInput(net.minecraftforge.client.event.InputEvent.Key event) {
        if (net.dandare21.fracturedutils.client.gui.DialogHudOverlay.isActive() && event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            int key = event.getKey();
            int scanCode = event.getScanCode();

            Minecraft mc = Minecraft.getInstance();
            if (mc.options != null && mc.options.keyInventory != null) {
                if (mc.options.keyInventory.matches(key, scanCode)) {
                    mc.options.keyInventory.setDown(false);
                    while (mc.options.keyInventory.consumeClick()) {}
                }
            }

            if (ModKeyBindings.DIALOG_ADVANCE_KEY.matches(key, scanCode)) {
                net.dandare21.fracturedutils.client.gui.DialogHudOverlay.handleUserInput();
                if (mc.options != null) {
                    if (mc.options.keyInventory != null && mc.options.keyInventory.matches(key, scanCode)) {
                        while (mc.options.keyInventory.consumeClick()) {}
                    }
                    if (mc.options.keyJump != null && mc.options.keyJump.matches(key, scanCode)) {
                        while (mc.options.keyJump.consumeClick()) {}
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        if (net.dandare21.fracturedutils.client.camera.CustomCameraManager.isActive()) {
            net.dandare21.fracturedutils.client.camera.CameraUtils.applyCameraOverride(event.getCamera(), event);
        }
        net.dandare21.fracturedutils.screeneffect.client.ClientScreenEffectHandler.onComputeCameraAngles(event);
    }

    @SubscribeEvent
    public static void onComputeFov(net.minecraftforge.client.event.ViewportEvent.ComputeFov event) {
        if (net.dandare21.fracturedutils.client.camera.CustomCameraManager.isFovActive()) {
            event.setFOV(net.dandare21.fracturedutils.client.camera.CustomCameraManager.getCustomFov());
        }
        net.dandare21.fracturedutils.screeneffect.client.ClientScreenEffectHandler.onComputeFov(event);
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
        if (event.getEntity() != null && ClientDownedData.isPlayerDowned(event.getEntity().getUUID())) {
            if (!net.dandare21.fracturedutils.client.animation.PlayerAnimationManager.isAnimationPlaying(event.getEntity())) {
                net.dandare21.fracturedutils.client.animation.PlayerAnimationManager.playAnimation(event.getEntity(), "startDown", true);
            }
        }
    }

    @SubscribeEvent
    public static void onClientLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        net.dandare21.fracturedutils.client.gui.DialogHudOverlay.clearActiveDialog();
        net.dandare21.fracturedutils.client.camera.ClientCameraHandler.clearCameraOverride();
        net.dandare21.fracturedutils.screeneffect.client.ClientScreenEffectHandler.clearAllEffects();
        net.dandare21.fracturedutils.bossbar.client.ClientBossBarManager.getInstance().clear();
    }

    @SubscribeEvent
    public static void onClientLoggingIn(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        net.dandare21.fracturedutils.client.gui.DialogHudOverlay.clearActiveDialog();
    }

    @Mod.EventBusSubscriber(modid = FracturedUtils.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusClientEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(ModKeyBindings.WAITING_ROOM_KEY);
            event.register(ModKeyBindings.SKIP_CUTSCENE_KEY);
            event.register(ModKeyBindings.OPERATOR_RESUME_KEY);
            event.register(ModKeyBindings.DIALOG_ADVANCE_KEY);
        }
    }
}
