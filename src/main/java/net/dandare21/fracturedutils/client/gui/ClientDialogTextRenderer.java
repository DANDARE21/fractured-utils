package net.dandare21.fracturedutils.client.gui;

import net.dandare21.fracturedutils.dialog.DialogFormatUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ClientDialogTextRenderer {

    private static final String[] ASCII_STRINGS = new String[128];
    static {
        for (int i = 0; i < 128; i++) {
            ASCII_STRINGS[i] = String.valueOf((char) i);
        }
    }

    private static String getCharString(char c) {
        return c < 128 ? ASCII_STRINGS[c] : String.valueOf(c);
    }

    private static float fastNoise(long time, int index) {
        long hash = time * 31L + index * 1013L;
        hash = (hash ^ (hash >>> 16)) * 0x45d9f3bL;
        hash = (hash ^ (hash >>> 16)) * 0x45d9f3bL;
        hash = hash ^ (hash >>> 16);
        return ((hash & 0xFFFF) / 65535.0f);
    }

    public static void renderAnimatedText(GuiGraphics graphics, Font font, String text, int startX, int startY, int maxLineWidth, int defaultColor) {
        if (text == null || text.isEmpty()) return;

        String formatted = DialogFormatUtil.translateCodes(text);
        if (formatted.isEmpty()) return;

        // FAST PATH: If there are no custom animated effects (wave, shake, rainbow, glitch, pulse),
        // use Minecraft's native font line-splitter which batches rendering and avoids per-character overhead.
        if (!formatted.contains("§~")) {
            List<FormattedCharSequence> wrappedLines = font.split(Component.literal(formatted), maxLineWidth);
            int lineY = startY;
            for (FormattedCharSequence line : wrappedLines) {
                graphics.drawString(font, line, startX, lineY, defaultColor, false);
                lineY += 12;
            }
            return;
        }

        int len = formatted.length();

        int cursorX = startX;
        int cursorY = startY;
        int activeColor = defaultColor;
        DialogFormatUtil.CustomEffect activeEffect = DialogFormatUtil.CustomEffect.NONE;

        int visibleCharIdx = 0;
        int lineMaxH = 12;

        long now = System.currentTimeMillis();

        StringBuilder batchSb = new StringBuilder();
        int batchStartX = cursorX;

        for (int i = 0; i < len; i++) {
            char c = formatted.charAt(i);

            // Check Custom Effect code: §~s, §~w, §~r, §~g, §~p, §~x
            if (c == '§' && i + 2 < len && formatted.charAt(i + 1) == '~' && DialogFormatUtil.isCustomEffectChar(formatted.charAt(i + 2))) {
                // Flush existing batch before changing effect
                if (batchSb.length() > 0) {
                    graphics.drawString(font, batchSb.toString(), batchStartX, cursorY, activeColor, false);
                    batchSb.setLength(0);
                }
                char codeChar = Character.toLowerCase(formatted.charAt(i + 2));
                switch (codeChar) {
                    case 's': activeEffect = DialogFormatUtil.CustomEffect.SHAKE; break;
                    case 'w': activeEffect = DialogFormatUtil.CustomEffect.WAVE; break;
                    case 'r': activeEffect = DialogFormatUtil.CustomEffect.RAINBOW; break;
                    case 'g': activeEffect = DialogFormatUtil.CustomEffect.GLITCH; break;
                    case 'p': activeEffect = DialogFormatUtil.CustomEffect.PULSE; break;
                    case 'x': activeEffect = DialogFormatUtil.CustomEffect.NONE; break;
                }
                i += 2;
                batchStartX = cursorX;
                continue;
            }

            // Check Standard Minecraft Format code: §0 - §f, §r
            if (c == '§' && i + 1 < len && DialogFormatUtil.isFormatChar(formatted.charAt(i + 1))) {
                // Flush existing batch before changing color
                if (batchSb.length() > 0) {
                    graphics.drawString(font, batchSb.toString(), batchStartX, cursorY, activeColor, false);
                    batchSb.setLength(0);
                }
                char codeChar = Character.toLowerCase(formatted.charAt(i + 1));
                if (codeChar == 'r') {
                    activeColor = defaultColor;
                    activeEffect = DialogFormatUtil.CustomEffect.NONE;
                } else if ((codeChar >= '0' && codeChar <= '9') || (codeChar >= 'a' && codeChar <= 'f')) {
                    activeColor = DialogFormatUtil.getMinecraftColor(codeChar, defaultColor);
                }
                i += 1;
                batchStartX = cursorX;
                continue;
            }

            // Newline character
            if (c == '\n') {
                if (batchSb.length() > 0) {
                    graphics.drawString(font, batchSb.toString(), batchStartX, cursorY, activeColor, false);
                    batchSb.setLength(0);
                }
                cursorX = startX;
                cursorY += lineMaxH;
                batchStartX = cursorX;
                continue;
            }

            String charStr = getCharString(c);
            int charW = font.width(charStr);

            // Word Wrapping
            if ((c == ' ' && cursorX + charW > startX + maxLineWidth) || (cursorX + charW > startX + maxLineWidth && cursorX > startX)) {
                if (batchSb.length() > 0) {
                    graphics.drawString(font, batchSb.toString(), batchStartX, cursorY, activeColor, false);
                    batchSb.setLength(0);
                }
                cursorX = startX;
                cursorY += lineMaxH;
                batchStartX = cursorX;
                if (c == ' ') continue;
            }

            // If active effect is NONE, accumulate character into run batch for single drawString call!
            if (activeEffect == DialogFormatUtil.CustomEffect.NONE) {
                if (batchSb.length() == 0) {
                    batchStartX = cursorX;
                }
                batchSb.append(c);
                cursorX += charW;
                visibleCharIdx++;
                continue;
            }

            // If active effect is active (SHAKE, WAVE, RAINBOW, GLITCH, PULSE), render individual animated character
            if (batchSb.length() > 0) {
                graphics.drawString(font, batchSb.toString(), batchStartX, cursorY, activeColor, false);
                batchSb.setLength(0);
            }

            double drawX = cursorX;
            double drawY = cursorY;
            int drawColor = activeColor;

            switch (activeEffect) {
                case SHAKE:
                    drawX += (fastNoise(now, visibleCharIdx * 2) - 0.5f) * 2.2f;
                    drawY += (fastNoise(now, visibleCharIdx * 2 + 1) - 0.5f) * 2.2f;
                    break;
                case WAVE:
                    drawY += Math.sin((now / 130.0) + visibleCharIdx * 0.45) * 2.5;
                    break;
                case RAINBOW:
                    drawColor = DialogFormatUtil.getRainbowColor(visibleCharIdx);
                    break;
                case GLITCH:
                    if (fastNoise(now / 100L, visibleCharIdx) < 0.14f) {
                        drawX += (fastNoise(now, visibleCharIdx + 99) - 0.5f) * 4.0f;
                    }
                    if (fastNoise(now / 120L, visibleCharIdx + 42) < 0.10f) {
                        drawColor = 0xFF00FFCC;
                    }
                    break;
                case PULSE:
                    drawColor = DialogFormatUtil.getPulseColor(activeColor, visibleCharIdx);
                    break;
                default:
                    break;
            }

            graphics.drawString(font, charStr, (int) Math.round(drawX), (int) Math.round(drawY), drawColor, false);
            cursorX += charW;
            visibleCharIdx++;
        }

        // Flush any remaining batched text run at end of string
        if (batchSb.length() > 0) {
            graphics.drawString(font, batchSb.toString(), batchStartX, cursorY, activeColor, false);
        }
    }
}

