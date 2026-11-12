package net.dandare21.fracturedutils.region.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.dandare21.fracturedutils.region.data.Region;
import net.dandare21.fracturedutils.region.data.RegionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Renders tiered open-world entry banners and the persistent location watermark.
 */
public class RegionHudOverlay {

    private static Region activeBannerRegion = null;
    private static long bannerStartTime = 0;

    private static final long FADE_IN_MS = 800;
    private static final long HOLD_MS = 3000;
    private static final long FADE_OUT_MS = 1000;
    private static final long TOTAL_DURATION_MS = FADE_IN_MS + HOLD_MS + FADE_OUT_MS;

    public static void showBanner(Region region) {
        if (region == null) return;
        activeBannerRegion = region;
        bannerStartTime = System.currentTimeMillis();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            // Play atmospheric chime
            if (region.getType() == RegionType.MAP_ZONE) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.85f, 0.4f));
            } else {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 0.5f));
            }
        }
    }

    public static void render(GuiGraphics guiGraphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        renderPersistentLocation(guiGraphics, mc);
        renderActiveBanner(guiGraphics, mc);
    }

    private static void renderPersistentLocation(GuiGraphics guiGraphics, Minecraft mc) {
        ClientRegionData data = ClientRegionData.getInstance();
        Region current = data.getCurrentPrimaryRegion();
        if (current == null) {
            return;
        }

        Region mapZone = data.getCurrentMapZone();
        Region cityOrEvent = (current.getType() != RegionType.MAP_ZONE) ? current : null;

        String locationText;
        if (mapZone != null && cityOrEvent != null) {
            locationText = "§7" + mapZone.getName() + " §8» §f" + cityOrEvent.getName();
        } else {
            locationText = "§f" + current.getName();
        }

        Font font = mc.font;
        int textWidth = font.width(locationText);
        int x = 8;
        int y = mc.getWindow().getGuiScaledHeight() - 22;

        // Subtle dark backing pill
        guiGraphics.fill(x - 3, y - 2, x + textWidth + 5, y + font.lineHeight + 2, 0x77000000);
        guiGraphics.fill(x - 4, y - 2, x - 3, y + font.lineHeight + 2, 0xFF00D0FF); // Cyan accent bar
        guiGraphics.drawString(font, locationText, x, y, 0xFFFFFFFF, true);
    }

    private static void renderActiveBanner(GuiGraphics guiGraphics, Minecraft mc) {
        if (activeBannerRegion == null) return;

        long elapsed = System.currentTimeMillis() - bannerStartTime;
        if (elapsed >= TOTAL_DURATION_MS) {
            activeBannerRegion = null;
            return;
        }

        float alpha;
        if (elapsed < FADE_IN_MS) {
            alpha = (float) elapsed / FADE_IN_MS;
        } else if (elapsed < FADE_IN_MS + HOLD_MS) {
            alpha = 1.0f;
        } else {
            alpha = 1.0f - (float) (elapsed - (FADE_IN_MS + HOLD_MS)) / FADE_OUT_MS;
        }
        alpha = Math.max(0.0f, Math.min(1.0f, alpha));
        if (alpha <= 0.001f) return;

        int alphaBits = (int) (alpha * 255.0f);
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        Font font = mc.font;

        if (activeBannerRegion.getType() == RegionType.MAP_ZONE) {
            renderMacroBanner(guiGraphics, font, screenWidth, alphaBits, activeBannerRegion);
        } else {
            renderSubBanner(guiGraphics, font, screenWidth, alphaBits, activeBannerRegion);
        }
    }

    private static void renderMacroBanner(GuiGraphics guiGraphics, Font font, int screenWidth, int alphaBits, Region region) {
        int centerY = 50;

        // Background dark gradient band
        int bandColor = (Math.min(alphaBits, 180) << 24) | 0x0A0A0E;
        guiGraphics.fill(0, centerY - 18, screenWidth, centerY + 30, bandColor);

        // Gold ornamental border lines
        int goldBorder = (alphaBits << 24) | 0xE6C364;
        guiGraphics.fill(screenWidth / 2 - 120, centerY - 18, screenWidth / 2 + 120, centerY - 17, goldBorder);
        guiGraphics.fill(screenWidth / 2 - 120, centerY + 29, screenWidth / 2 + 120, centerY + 30, goldBorder);

        // Large title text
        String title = region.getName().toUpperCase();
        int titleColor = (alphaBits << 24) | 0xFFE082; // Warm amber gold

        var pose = guiGraphics.pose();
        pose.pushPose();
        float scale = 1.6f;
        pose.translate((screenWidth - font.width(title) * scale) / 2.0f, centerY - 8, 0);
        pose.scale(scale, scale, 1.0f);
        guiGraphics.drawString(font, title, 0, 0, titleColor, true);
        pose.popPose();

        // Subtitle or territory designation
        String sub = !region.getSubtitle().isEmpty() ? region.getSubtitle() : "TERRITORY UNLOCKED";
        int subColor = (alphaBits << 24) | 0xCCCCCC;
        guiGraphics.drawCenteredString(font, "—  " + sub + "  —", screenWidth / 2, centerY + 14, subColor);
    }

    private static void renderSubBanner(GuiGraphics guiGraphics, Font font, int screenWidth, int alphaBits, Region region) {
        int centerY = 65;

        // Background pill
        int pillW = 200;
        int pillH = 34;
        int pillX = (screenWidth - pillW) / 2;
        int pillY = centerY - 14;

        int bgColor = (Math.min(alphaBits, 190) << 24) | 0x111116;
        guiGraphics.fill(pillX, pillY, pillX + pillW, pillY + pillH, bgColor);

        // Accent indicator
        int accentColor = (alphaBits << 24) | (region.getColor() & 0x00FFFFFF);
        guiGraphics.fill(pillX, pillY, pillX + 3, pillY + pillH, accentColor);

        // District title
        String typeLabel = region.getType() == RegionType.CITY ? "SETTLEMENT" : "ZONE";
        int labelColor = (alphaBits << 24) | 0xAAAAAA;
        guiGraphics.drawCenteredString(font, typeLabel, screenWidth / 2, centerY - 9, labelColor);

        int titleColor = (alphaBits << 24) | 0xFFFFFF;
        guiGraphics.drawCenteredString(font, region.getName(), screenWidth / 2, centerY + 3, titleColor);
    }
}

