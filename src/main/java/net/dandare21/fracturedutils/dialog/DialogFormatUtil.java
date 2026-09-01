package net.dandare21.fracturedutils.dialog;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class DialogFormatUtil {

    public enum CustomEffect {
        NONE,
        SHAKE,
        WAVE,
        RAINBOW,
        GLITCH,
        PULSE
    }

    /**
     * Translates '&' color and custom formatting codes into '§' section symbols without regex allocations.
     */
    public static String translateCodes(String input) {
        if (input == null || input.isEmpty()) return "";
        int len = input.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (c == '&' && i + 1 < len) {
                char next = input.charAt(i + 1);
                if (next == '~' && i + 2 < len && isCustomEffectChar(input.charAt(i + 2))) {
                    sb.append('§').append('~').append(Character.toLowerCase(input.charAt(i + 2)));
                    i += 2;
                    continue;
                } else if (isFormatChar(next)) {
                    sb.append('§').append(Character.toLowerCase(next));
                    i += 1;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Formats a raw string into a Minecraft Component with styling applied.
     */
    public static Component formatText(String input) {
        return Component.literal(translateCodes(input));
    }

    /**
     * Combines speaker and text into a styled line component.
     */
    public static Component formatLine(String speaker, String text) {
        String translatedSpeaker = translateCodes(speaker);
        String translatedText = translateCodes(text);

        if (translatedSpeaker != null && !translatedSpeaker.trim().isEmpty()) {
            return Component.literal(translatedSpeaker + " §r" + translatedText);
        } else {
            return Component.literal(translatedText);
        }
    }

    /**
     * Counts visible characters, ignoring standard formatting (&0-&f, &k-&r) and custom effect codes (&~s, &~w, &~r, &~g, &~p, &~x).
     */
    public static int getVisibleCharCount(String input) {
        if (input == null || input.isEmpty()) return 0;
        int count = 0;
        int len = input.length();
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 2 < len && input.charAt(i + 1) == '~' && isCustomEffectChar(input.charAt(i + 2))) {
                i += 2; // Skip 3-char custom code (&~s)
            } else if ((c == '&' || c == '§') && i + 1 < len && isFormatChar(input.charAt(i + 1))) {
                i += 1; // Skip 2-char standard code (&a)
            } else {
                count++;
            }
        }
        return count;
    }

    /**
     * Reveals up to visibleCharCount characters of formatted text without breaking standard or custom effect codes.
     */
    public static String getRevealedText(String input, int visibleCharCount) {
        if (input == null || input.isEmpty() || visibleCharCount <= 0) return "";
        StringBuilder sb = new StringBuilder();
        int remainingChars = visibleCharCount;
        int len = input.length();

        for (int i = 0; i < len && remainingChars > 0; i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 2 < len && input.charAt(i + 1) == '~' && isCustomEffectChar(input.charAt(i + 2))) {
                sb.append(c).append(input.charAt(i + 1)).append(input.charAt(i + 2));
                i += 2;
            } else if ((c == '&' || c == '§') && i + 1 < len && isFormatChar(input.charAt(i + 1))) {
                sb.append(c).append(input.charAt(i + 1));
                i += 1;
            } else {
                sb.append(c);
                remainingChars--;
            }
        }

        return translateCodes(sb.toString());
    }

    private static boolean isFormatChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                || (c >= 'k' && c <= 'o') || (c >= 'K' && c <= 'O') || c == 'r' || c == 'R';
    }

    public static boolean isCustomEffectChar(char c) {
        char lc = Character.toLowerCase(c);
        return lc == 's' || lc == 'w' || lc == 'r' || lc == 'g' || lc == 'p' || lc == 'x';
    }

    public static int getMinecraftColor(char code, int defaultColor) {
        switch (Character.toLowerCase(code)) {
            case '0': return 0xFF000000;
            case '1': return 0xFF0000AA;
            case '2': return 0xFF00AA00;
            case '3': return 0xFF00AAAA;
            case '4': return 0xFFAA0000;
            case '5': return 0xFFAA00AA;
            case '6': return 0xFFFFAA00;
            case '7': return 0xFFAAAAAA;
            case '8': return 0xFF555555;
            case '9': return 0xFF5555FF;
            case 'a': return 0xFF55FF55;
            case 'b': return 0xFF55FFFF;
            case 'c': return 0xFFFF5555;
            case 'd': return 0xFFFF55FF;
            case 'e': return 0xFFFFFF55;
            case 'f': return 0xFFFFFFFF;
            default: return defaultColor;
        }
    }

    public static int getRainbowColor(int charIndex) {
        float hue = (float) (((System.currentTimeMillis() / 18.0) + charIndex * 16.0) % 360.0) / 360.0f;
        return 0xFF000000 | hsvToRgb(hue, 0.85f, 1.0f);
    }

    public static int getPulseColor(int baseColor, int charIndex) {
        double pulse = 0.5 + 0.5 * Math.sin((System.currentTimeMillis() / 160.0) + charIndex * 0.35);
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        int minR = (int) (r * 0.25);
        int minG = (int) (g * 0.25);
        int minB = (int) (b * 0.25);

        if (r < 30 && g < 30 && b < 30) {
            r = 0; g = 229; b = 255;
            minR = 0; minG = 50; minB = 80;
        }

        int finalR = (int) (minR + (r - minR) * pulse);
        int finalG = (int) (minG + (g - minG) * pulse);
        int finalB = (int) (minB + (b - minB) * pulse);

        return 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
    }

    private static int hsvToRgb(float h, float s, float v) {
        float r = 0, g = 0, b = 0;
        float i = (float) Math.floor(h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        switch ((int) i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            case 5 -> { r = v; g = p; b = q; }
        }
        return ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }

    private static float fastNoise(long time, int index) {
        long hash = time * 31L + index * 1013L;
        hash = (hash ^ (hash >>> 16)) * 0x45d9f3bL;
        hash = (hash ^ (hash >>> 16)) * 0x45d9f3bL;
        hash = hash ^ (hash >>> 16);
        return ((hash & 0xFFFF) / 65535.0f);
    }

    /**
     * Renders animated RPG text efficiently with run batching for non-effect text segments
     * and fast primitive math for animated per-character Shake, Wave, Rainbow, Glitch, and Pulse effects.
     */
    public static void renderAnimatedText(GuiGraphics graphics, Font font, String text, int startX, int startY, int maxLineWidth, int defaultColor) {
        if (text == null || text.isEmpty()) return;

        String formatted = translateCodes(text);
        int len = formatted.length();

        int cursorX = startX;
        int cursorY = startY;
        int activeColor = defaultColor;
        CustomEffect activeEffect = CustomEffect.NONE;

        int visibleCharIdx = 0;
        int lineMaxH = 12;

        long now = System.currentTimeMillis();

        StringBuilder batchSb = new StringBuilder();
        int batchStartX = cursorX;

        for (int i = 0; i < len; i++) {
            char c = formatted.charAt(i);

            // Check Custom Effect code: §~s, §~w, §~r, §~g, §~p, §~x
            if (c == '§' && i + 2 < len && formatted.charAt(i + 1) == '~' && isCustomEffectChar(formatted.charAt(i + 2))) {
                // Flush existing batch before changing effect
                if (batchSb.length() > 0) {
                    graphics.drawString(font, batchSb.toString(), batchStartX, cursorY, activeColor, false);
                    batchSb.setLength(0);
                }
                char codeChar = Character.toLowerCase(formatted.charAt(i + 2));
                switch (codeChar) {
                    case 's': activeEffect = CustomEffect.SHAKE; break;
                    case 'w': activeEffect = CustomEffect.WAVE; break;
                    case 'r': activeEffect = CustomEffect.RAINBOW; break;
                    case 'g': activeEffect = CustomEffect.GLITCH; break;
                    case 'p': activeEffect = CustomEffect.PULSE; break;
                    case 'x': activeEffect = CustomEffect.NONE; break;
                }
                i += 2;
                batchStartX = cursorX;
                continue;
            }

            // Check Standard Minecraft Format code: §0 - §f, §r
            if (c == '§' && i + 1 < len && isFormatChar(formatted.charAt(i + 1))) {
                // Flush existing batch before changing color
                if (batchSb.length() > 0) {
                    graphics.drawString(font, batchSb.toString(), batchStartX, cursorY, activeColor, false);
                    batchSb.setLength(0);
                }
                char codeChar = Character.toLowerCase(formatted.charAt(i + 1));
                if (codeChar == 'r') {
                    activeColor = defaultColor;
                    activeEffect = CustomEffect.NONE;
                } else if ((codeChar >= '0' && codeChar <= '9') || (codeChar >= 'a' && codeChar <= 'f')) {
                    activeColor = getMinecraftColor(codeChar, defaultColor);
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

            String charStr = String.valueOf(c);
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
            if (activeEffect == CustomEffect.NONE) {
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
                    drawColor = getRainbowColor(visibleCharIdx);
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
                    drawColor = getPulseColor(activeColor, visibleCharIdx);
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
