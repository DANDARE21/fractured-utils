package net.dandare21.fracturedutils.dialog.text;

import net.dandare21.fracturedutils.dialog.DialogFormatUtil;
import net.dandare21.fracturedutils.dialog.DialogFormatUtil.CustomEffect;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DialogTextParser {

    public static List<DialogTextToken.Token> parse(String rawText, int defaultColor) {
        List<DialogTextToken.Token> tokens = new ArrayList<>();
        if (rawText == null || rawText.isEmpty()) {
            return tokens;
        }

        int activeColor = defaultColor;
        CustomEffect activeEffect = CustomEffect.NONE;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strikethrough = false;
        boolean obfuscated = false;

        int len = rawText.length();
        for (int i = 0; i < len; i++) {
            char c = rawText.charAt(i);

            // 1. Check for BBCode / custom tag: {tag...}
            if (c == '{') {
                int closeIdx = rawText.indexOf('}', i);
                if (closeIdx != -1) {
                    String tagContent = rawText.substring(i + 1, closeIdx).trim();
                    boolean handled = handleTag(tagContent, tokens, defaultColor, activeEffectState -> {
                        // callback to modify active effect
                    });
                    if (handled) {
                        // Tag was processed, update state
                        String lower = tagContent.toLowerCase(Locale.ROOT);
                        if (lower.equals("shake")) activeEffect = CustomEffect.SHAKE;
                        else if (lower.equals("/shake") && activeEffect == CustomEffect.SHAKE) activeEffect = CustomEffect.NONE;
                        else if (lower.equals("wave")) activeEffect = CustomEffect.WAVE;
                        else if (lower.equals("/wave") && activeEffect == CustomEffect.WAVE) activeEffect = CustomEffect.NONE;
                        else if (lower.equals("rainbow")) activeEffect = CustomEffect.RAINBOW;
                        else if (lower.equals("/rainbow") && activeEffect == CustomEffect.RAINBOW) activeEffect = CustomEffect.NONE;
                        else if (lower.equals("glitch")) activeEffect = CustomEffect.GLITCH;
                        else if (lower.equals("/glitch") && activeEffect == CustomEffect.GLITCH) activeEffect = CustomEffect.NONE;
                        else if (lower.equals("pulse")) activeEffect = CustomEffect.PULSE;
                        else if (lower.equals("/pulse") && activeEffect == CustomEffect.PULSE) activeEffect = CustomEffect.NONE;
                        else if (lower.startsWith("color:")) {
                            activeColor = parseColor(lower.substring(6).trim(), defaultColor);
                        } else if (lower.equals("/color") || lower.equals("/c")) {
                            activeColor = defaultColor;
                        }

                        i = closeIdx;
                        continue;
                    }
                }
            }

            // 2. Check for Minecraft Section Symbol '§' or '&'
            if ((c == '§' || c == '&') && i + 1 < len) {
                char next = rawText.charAt(i + 1);
                // Custom effect code: §~s, §~w, §~r, §~g, §~p, §~x
                if (next == '~' && i + 2 < len && DialogFormatUtil.isCustomEffectChar(rawText.charAt(i + 2))) {
                    char code = Character.toLowerCase(rawText.charAt(i + 2));
                    switch (code) {
                        case 's' -> activeEffect = CustomEffect.SHAKE;
                        case 'w' -> activeEffect = CustomEffect.WAVE;
                        case 'r' -> activeEffect = CustomEffect.RAINBOW;
                        case 'g' -> activeEffect = CustomEffect.GLITCH;
                        case 'p' -> activeEffect = CustomEffect.PULSE;
                        case 'x' -> activeEffect = CustomEffect.NONE;
                    }
                    i += 2;
                    continue;
                }

                // Standard format code: §0-§f, §k-§o, §r
                char code = Character.toLowerCase(next);
                if (code == 'r') {
                    activeColor = defaultColor;
                    activeEffect = CustomEffect.NONE;
                    bold = false;
                    italic = false;
                    underline = false;
                    strikethrough = false;
                    obfuscated = false;
                    i++;
                    continue;
                } else if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    activeColor = DialogFormatUtil.getMinecraftColor(code, defaultColor);
                    i++;
                    continue;
                } else if (code == 'l') { bold = true; i++; continue; }
                else if (code == 'o') { italic = true; i++; continue; }
                else if (code == 'n') { underline = true; i++; continue; }
                else if (code == 'm') { strikethrough = true; i++; continue; }
                else if (code == 'k') { obfuscated = true; i++; continue; }
            }

            // 3. Regular display character
            tokens.add(new DialogTextToken.TextChar(c, activeColor, activeEffect, bold, italic, underline, strikethrough, obfuscated));

            // 4. Natural Punctuation Pacing
            if (c == ',' || c == ';') {
                tokens.add(new DialogTextToken.Pause(4));
            } else if (c == '.' || c == '!' || c == '?') {
                // Peek ahead to avoid pausing multiple times on clusters like '...' or '?!!'
                boolean hasMorePunctuation = false;
                for (int p = i + 1; p < len; p++) {
                    char nextChar = rawText.charAt(p);
                    if (nextChar == '.' || nextChar == '!' || nextChar == '?') {
                        hasMorePunctuation = true;
                        break;
                    } else if (!Character.isWhitespace(nextChar)) {
                        break;
                    }
                }
                if (!hasMorePunctuation) {
                    tokens.add(new DialogTextToken.Pause(10));
                }
            }
        }

        return tokens;
    }

    private static boolean handleTag(String tag, List<DialogTextToken.Token> tokens, int defaultColor, java.util.function.Consumer<CustomEffect> effectSetter) {
        String lower = tag.toLowerCase(Locale.ROOT);
        String[] parts = tag.split(":");
        String command = parts[0].trim().toLowerCase(Locale.ROOT);

        switch (command) {
            case "pause", "wait" -> {
                int ticks = 10;
                if (parts.length > 1) {
                    try {
                        ticks = Math.max(1, Integer.parseInt(parts[1].trim()));
                    } catch (NumberFormatException ignored) {}
                }
                tokens.add(new DialogTextToken.Pause(ticks));
                return true;
            }
            case "shake_screen", "screenshake", "quake" -> {
                float intensity = 2.0f;
                int duration = 20;
                if (parts.length > 1) {
                    try {
                        intensity = Float.parseFloat(parts[1].trim());
                    } catch (NumberFormatException ignored) {}
                }
                if (parts.length > 2) {
                    try {
                        duration = Math.max(1, Integer.parseInt(parts[2].trim()));
                    } catch (NumberFormatException ignored) {}
                }
                tokens.add(new DialogTextToken.ScreenShake(intensity, duration));
                return true;
            }
            case "sound" -> {
                if (parts.length > 1) {
                    String soundId = parts[1].trim();
                    float vol = parts.length > 2 ? parseFloat(parts[2], 1.0f) : 1.0f;
                    float pitch = parts.length > 3 ? parseFloat(parts[3], 1.0f) : 1.0f;
                    try {
                        ResourceLocation rl = soundId.contains(":") ? new ResourceLocation(soundId) : new ResourceLocation("fractured_utils", soundId);
                        tokens.add(new DialogTextToken.SoundTrigger(rl, vol, pitch));
                        return true;
                    } catch (Exception ignored) {}
                }
            }
            case "particle" -> {
                if (parts.length > 1) {
                    String particleName = parts[1].trim();
                    int count = parts.length > 2 ? parseInt(parts[2], 10) : 10;
                    tokens.add(new DialogTextToken.ParticleTrigger(particleName, count));
                    return true;
                }
            }
            case "event" -> {
                if (parts.length > 1) {
                    String eventName = parts[1].trim();
                    String payload = parts.length > 2 ? parts[2].trim() : "";
                    tokens.add(new DialogTextToken.EventTrigger(eventName, payload));
                    return true;
                }
            }
            case "shake", "/shake", "wave", "/wave", "rainbow", "/rainbow",
                 "glitch", "/glitch", "pulse", "/pulse", "color", "/color", "/c" -> {
                return true;
            }
        }

        return false;
    }

    private static int parseColor(String str, int defaultColor) {
        if (str == null || str.isBlank()) return defaultColor;
        if (str.startsWith("#")) {
            try {
                int rgb = Integer.parseInt(str.substring(1), 16);
                return 0xFF000000 | rgb;
            } catch (NumberFormatException ignored) {}
        }
        return switch (str) {
            case "black" -> 0xFF000000;
            case "dark_blue", "navy" -> 0xFF0000AA;
            case "dark_green", "green" -> 0xFF00AA00;
            case "dark_aqua", "teal" -> 0xFF00AAAA;
            case "dark_red" -> 0xFFAA0000;
            case "dark_purple", "purple" -> 0xFFAA00AA;
            case "gold", "orange" -> 0xFFFFAA00;
            case "gray", "grey" -> 0xFFAAAAAA;
            case "dark_gray", "dark_grey" -> 0xFF555555;
            case "blue" -> 0xFF5555FF;
            case "light_green", "lime" -> 0xFF55FF55;
            case "aqua", "cyan" -> 0xFF55FFFF;
            case "red" -> 0xFFFF5555;
            case "light_purple", "pink", "magenta" -> 0xFFFF55FF;
            case "yellow" -> 0xFFFFFF55;
            case "white" -> 0xFFFFFFFF;
            default -> defaultColor;
        };
    }

    private static float parseFloat(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    public static int countVisibleChars(List<DialogTextToken.Token> tokens) {
        if (tokens == null) return 0;
        int count = 0;
        for (DialogTextToken.Token t : tokens) {
            if (t instanceof DialogTextToken.TextChar) {
                count++;
            }
        }
        return count;
    }
}

