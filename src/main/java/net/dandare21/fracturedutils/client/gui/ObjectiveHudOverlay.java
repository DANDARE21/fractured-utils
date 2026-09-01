package net.dandare21.fracturedutils.client.gui;

import net.dandare21.fracturedutils.client.ClientCutsceneHandler;
import net.dandare21.fracturedutils.client.ClientDownedData;
import net.dandare21.fracturedutils.client.ClientObjectiveData;
import net.dandare21.fracturedutils.dialog.DialogFormatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Locale;

public class ObjectiveHudOverlay {
    private static final int CYAN_MAIN = 0xFF00E5FF;
    private static final int AMBER_MAIN = 0xFFFFB700;

    public static void render(GuiGraphics guiGraphics) {
        if (!ClientObjectiveData.isActive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        // Hide overlay during active cutscenes, team wipe, or spectate screens
        if (ClientCutsceneHandler.getInstance().isCinematicPlaying() || ClientDownedData.isDowned() || mc.screen instanceof TeamWipeScreen || mc.screen instanceof DownedSpectateScreen) {
            return;
        }

        String name = ClientObjectiveData.getName();
        String description = ClientObjectiveData.getDescription();
        String waitText = ClientObjectiveData.getActiveWaitText();
        String waitType = ClientObjectiveData.getWaitType();

        if (name.isEmpty() && description.isEmpty()) {
            return;
        }

        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int boxX = 14;
        int boxY = 14;
        int boxW = Math.min(195, screenWidth - 28);
        int paddingLeft = 10;
        int paddingRight = 8;
        int maxTextWidth = boxW - paddingLeft - paddingRight;

        // Calculate dynamic content height
        int currentY = boxY + 4;

        // Category Tag: // OBJECTIVE
        String categoryTag = "// OBJECTIVE";
        currentY += 10;

        // Objective Title
        Component titleComp = DialogFormatUtil.formatText(name);
        List<FormattedCharSequence> titleLines = font.split(titleComp, maxTextWidth);
        int titleHeight = Math.max(1, titleLines.size()) * 11;
        currentY += titleHeight;

        // Objective Description
        List<FormattedCharSequence> descLines = null;
        if (!description.isEmpty()) {
            Component descComp = DialogFormatUtil.formatText(description);
            descLines = font.split(descComp, maxTextWidth);
            currentY += descLines.size() * 10 + 2;
        }

        // Active Wait Status Sub-row (formatted countdown timer / location distance)
        String waitDisplayStr = "";
        if (!waitText.isEmpty() || !waitType.isEmpty()) {
            if (waitType.equalsIgnoreCase("delay") && ClientObjectiveData.getTimerEndTime() > 0) {
                double remainingSecs = Math.max(0.0, (ClientObjectiveData.getTimerEndTime() - System.currentTimeMillis()) / 1000.0);
                if (remainingSecs >= 60.0) {
                    int mins = (int) (remainingSecs / 60);
                    double secs = remainingSecs % 60;
                    waitDisplayStr = String.format(Locale.ROOT, "⏱ %d:%04.1fs remaining", mins, secs);
                } else {
                    waitDisplayStr = String.format(Locale.ROOT, "⏱ %.1fs remaining", remainingSecs);
                }
            } else if (waitType.equalsIgnoreCase("marker") || waitType.equalsIgnoreCase("proximity")) {
                double dx = mc.player.getX() - ClientObjectiveData.getTargetX();
                double dy = mc.player.getY() - ClientObjectiveData.getTargetY();
                double dz = mc.player.getZ() - ClientObjectiveData.getTargetZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                waitDisplayStr = String.format(Locale.ROOT, "📍 %.1fm to location", dist);
            } else if (!waitText.isEmpty()) {
                waitDisplayStr = "⏳ " + waitText;
            }
        }

        List<FormattedCharSequence> waitLines = null;
        if (!waitDisplayStr.isEmpty()) {
            Component waitComp = Component.literal(waitDisplayStr);
            waitLines = font.split(waitComp, maxTextWidth);
            currentY += waitLines.size() * 10 + 4;
        }

        currentY += 4;
        int totalHeight = currentY - boxY;

        // Pulse animation effect when newly set
        long timeSinceSet = System.currentTimeMillis() - ClientObjectiveData.getObjectiveSetTime();
        boolean isPulse = timeSinceSet < 1500;
        int accentColor = isPulse && ((timeSinceSet / 150) % 2 == 0) ? 0xFFFFFFFF : CYAN_MAIN;

        // 1. Soft Translucent Modern Background (33% opacity dark tint)
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + totalHeight, 0x5503070A);

        // 2. Minimalist 3px Left Accent Bar
        guiGraphics.fill(boxX, boxY, boxX + 3, boxY + totalHeight, accentColor);
        guiGraphics.fill(boxX + 3, boxY, boxX + 4, boxY + totalHeight, 0x4400E5FF);

        // 3. Category Tag
        int drawY = boxY + 5;
        guiGraphics.drawString(font, categoryTag, boxX + paddingLeft, drawY, 0xAA00E5FF, true);
        drawY += 11;

        // 4. Objective Title
        for (FormattedCharSequence line : titleLines) {
            guiGraphics.drawString(font, line, boxX + paddingLeft, drawY, 0xFFFFFFFF, true);
            drawY += 11;
        }

        // 5. Objective Description
        if (descLines != null && !descLines.isEmpty()) {
            drawY += 1;
            for (FormattedCharSequence line : descLines) {
                guiGraphics.drawString(font, line, boxX + paddingLeft, drawY, 0xFFCCCCCC, true);
                drawY += 10;
            }
        }

        // 6. Active Wait Status (Countdown / Distance)
        if (waitLines != null && !waitLines.isEmpty()) {
            drawY += 3;
            for (FormattedCharSequence line : waitLines) {
                guiGraphics.drawString(font, line, boxX + paddingLeft, drawY, AMBER_MAIN, true);
                drawY += 10;
            }
        }
    }
}
