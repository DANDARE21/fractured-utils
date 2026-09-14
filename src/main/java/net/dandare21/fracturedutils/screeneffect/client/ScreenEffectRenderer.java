package net.dandare21.fracturedutils.screeneffect.client;

import net.dandare21.fracturedutils.screeneffect.ScreenEffectInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * Client-side handler and renderer for a specific ScreenEffectInstance type.
 *
 * @param <T> ScreenEffectInstance subclass
 */
public interface ScreenEffectRenderer<T extends ScreenEffectInstance> {
    default void onStart(T instance) {}

    default void onTick(T instance, Minecraft mc) {}

    default void onComputeCameraAngles(T instance, ViewportEvent.ComputeCameraAngles event, float partialTicks) {}

    default void onComputeFov(T instance, ViewportEvent.ComputeFov event, float partialTicks) {}

    default void onRenderOverlay(T instance, GuiGraphics graphics, float partialTicks, int screenWidth, int screenHeight) {}

    default void onEnd(T instance) {}
}
