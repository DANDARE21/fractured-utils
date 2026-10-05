package net.dandare21.fracturedutils.client;

import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.client.camera.CustomCameraManager;
import net.dandare21.fracturedutils.client.gui.DialogHudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FracturedUtils.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientDialogInputLock {
    private static Float savedPlayerYaw = null;
    private static Float savedPlayerPitch = null;

    public static boolean shouldLockInput() {
        return CustomCameraManager.isCameraActive();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (DialogHudOverlay.isActive() && mc.options != null && mc.options.keyInventory != null) {
            mc.options.keyInventory.setDown(false);
            while (mc.options.keyInventory.consumeClick()) {}
        }

        if (shouldLockInput()) {
            // Save original player view rotation BEFORE custom camera takes over
            if (savedPlayerYaw == null) {
                savedPlayerYaw = mc.player.getYRot();
                savedPlayerPitch = mc.player.getXRot();
            }

            // 1. Clear attack and use keypresses every tick so player cannot punch or interact when custom camera is active
            if (mc.options != null) {
                if (mc.options.keyAttack != null) {
                    mc.options.keyAttack.setDown(false);
                    while (mc.options.keyAttack.consumeClick()) {}
                }
                if (mc.options.keyUse != null) {
                    mc.options.keyUse.setDown(false);
                    while (mc.options.keyUse.consumeClick()) {}
                }
                if (mc.options.keyPickItem != null) {
                    mc.options.keyPickItem.setDown(false);
                    while (mc.options.keyPickItem.consumeClick()) {}
                }
                if (mc.options.keyDrop != null) {
                    mc.options.keyDrop.setDown(false);
                    while (mc.options.keyDrop.consumeClick()) {}
                }
            }

            // 2. Keep player entity's rotation anchored to its original pre-camera view orientation
            float lockYaw = savedPlayerYaw;
            float lockPitch = savedPlayerPitch;

            mc.player.setYRot(lockYaw);
            mc.player.setXRot(lockPitch);
            mc.player.yRotO = lockYaw;
            mc.player.xRotO = lockPitch;
            mc.player.yHeadRot = lockYaw;
            mc.player.yHeadRotO = lockYaw;
            mc.player.yBodyRot = lockYaw;
            mc.player.yBodyRotO = lockYaw;
        } else {
            // Restore saved original player view rotation when custom camera finishes
            if (savedPlayerYaw != null) {
                mc.player.setYRot(savedPlayerYaw);
                mc.player.setXRot(savedPlayerPitch);
                mc.player.yRotO = savedPlayerYaw;
                mc.player.xRotO = savedPlayerPitch;
                mc.player.yHeadRot = savedPlayerYaw;
                mc.player.yHeadRotO = savedPlayerYaw;
                mc.player.yBodyRot = savedPlayerYaw;
                mc.player.yBodyRotO = savedPlayerYaw;
                savedPlayerYaw = null;
                savedPlayerPitch = null;
            }
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (shouldLockInput()) {
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
    public static void onRenderHand(RenderHandEvent event) {
        if (shouldLockInput()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (shouldLockInput()) {
            event.setCanceled(true);
        }
    }
}
