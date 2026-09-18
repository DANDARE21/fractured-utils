package net.dandare21.fracturedutils.screeneffect.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectInstance;
import net.dandare21.fracturedutils.screeneffect.ScreenEffectType;
import net.dandare21.fracturedutils.screeneffect.effects.HueShiftEffect;
import net.dandare21.fracturedutils.screeneffect.effects.ImpactFrameEffect;
import net.dandare21.fracturedutils.screeneffect.effects.InvertColorsEffect;
import net.dandare21.fracturedutils.screeneffect.effects.ScreenShakeEffect;
import net.dandare21.fracturedutils.screeneffect.effects.StrobeEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client-side manager for active screen effects.
 * Receives packets, ticks effects, updates camera angles, and renders screen overlays.
 */
public class ClientScreenEffectHandler {
    private static final List<ScreenEffectInstance> ACTIVE_EFFECTS = new CopyOnWriteArrayList<>();
    private static final Map<ResourceLocation, ScreenEffectRenderer<?>> RENDERERS = new ConcurrentHashMap<>();

    private static final ResourceLocation INVERT_SHADER = new ResourceLocation("shaders/post/invert.json");
    private static boolean invertShaderLoaded = false;

    private static final ResourceLocation COLOR_CONVOLVE_SHADER = new ResourceLocation("shaders/post/color_convolve.json");
    private static boolean colorConvolveShaderLoaded = false;
    private static java.lang.reflect.Field passesField = null;
    private static boolean passesFieldChecked = false;

    private static final ResourceLocation SOBEL_SHADER = new ResourceLocation("shaders/post/sobel.json");
    private static boolean sobelShaderLoaded = false;

    private static final ResourceLocation IMPACT_DRAW_SHADER = new ResourceLocation(FracturedUtils.MOD_ID, "shaders/post/impact_draw.json");
    private static boolean impactDrawShaderLoaded = false;

    static {
        registerDefaultRenderers();
    }

    private static void registerDefaultRenderers() {
        // 1. Screen Shake Renderer
        registerRenderer(ScreenShakeEffect.TYPE, new ScreenEffectRenderer<ScreenShakeEffect.ScreenShakeInstance>() {
            @Override
            public void onComputeCameraAngles(ScreenShakeEffect.ScreenShakeInstance instance, ViewportEvent.ComputeCameraAngles event, float partialTicks) {
                float time = (System.currentTimeMillis() - instance.getStartTimeMs()) / 1000.0f;
                float decayFactor = instance.isDecay() ? instance.getRemainingProgress(System.currentTimeMillis()) : 1.0f;
                float amp = instance.getIntensity() * decayFactor;
                float f = instance.getFrequency();

                float yawOffset = (float) (Math.sin(time * f * 1.0) * 0.7 + Math.sin(time * f * 2.3) * 0.3) * amp * instance.getYawFactor();
                float pitchOffset = (float) (Math.cos(time * f * 1.3) * 0.7 + Math.cos(time * f * 2.7) * 0.3) * amp * instance.getPitchFactor();
                float rollOffset = (float) (Math.sin(time * f * 0.8 + 1.0) * 0.7 + Math.sin(time * f * 1.9) * 0.3) * amp * instance.getRollFactor();

                event.setYaw(event.getYaw() + yawOffset);
                event.setPitch(event.getPitch() + pitchOffset);
                event.setRoll(event.getRoll() + rollOffset);
            }
        });

        // 2. Invert Colors Renderer
        registerRenderer(InvertColorsEffect.TYPE, new ScreenEffectRenderer<InvertColorsEffect.InvertColorsInstance>() {
            @Override
            public void onStart(InvertColorsEffect.InvertColorsInstance instance) {
                if (!instance.isPulse()) {
                    loadInvertShader();
                }
            }

            @Override
            public void onTick(InvertColorsEffect.InvertColorsInstance instance, Minecraft mc) {
                if (instance.isPulse()) {
                    float time = (System.currentTimeMillis() - instance.getStartTimeMs()) / 1000.0f;
                    float cycle = (float) (0.5 * (1.0 + Math.sin(time * instance.getFrequency() * Math.PI * 2.0)));
                    if (cycle >= 0.5f) {
                        loadInvertShader();
                    } else {
                        shutdownInvertShader();
                    }
                }
            }

            @Override
            public void onRenderOverlay(InvertColorsEffect.InvertColorsInstance instance, GuiGraphics graphics, float partialTicks, int screenWidth, int screenHeight) {
                // If post shader failed to load or is not supported by user settings, fallback to GL blend inversion
                Minecraft mc = Minecraft.getInstance();
                if (mc.gameRenderer.currentEffect() == null) {
                    boolean shouldInvert;
                    if (instance.isPulse()) {
                        float time = (System.currentTimeMillis() - instance.getStartTimeMs()) / 1000.0f;
                        float cycle = (float) (0.5 * (1.0 + Math.sin(time * instance.getFrequency() * Math.PI * 2.0)));
                        shouldInvert = cycle >= 0.5f;
                    } else {
                        shouldInvert = true;
                    }

                    if (shouldInvert) {
                        RenderSystem.enableBlend();
                        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO);
                        graphics.fill(0, 0, screenWidth, screenHeight, 0xFFFFFFFF);
                        RenderSystem.defaultBlendFunc();
                    }
                }
            }

            @Override
            public void onEnd(InvertColorsEffect.InvertColorsInstance instance) {
                shutdownInvertShader();
            }
        });

        // 3. Strobe Renderer
        registerRenderer(StrobeEffect.TYPE, new ScreenEffectRenderer<StrobeEffect.StrobeInstance>() {
            @Override
            public void onRenderOverlay(StrobeEffect.StrobeInstance instance, GuiGraphics graphics, float partialTicks, int screenWidth, int screenHeight) {
                float time = (System.currentTimeMillis() - instance.getStartTimeMs()) / 1000.0f;
                float alpha;
                if (instance.isSmooth()) {
                    alpha = (float) (0.5 * (1.0 + Math.sin(time * instance.getFrequency() * Math.PI * 2.0))) * instance.getMaxAlpha();
                } else {
                    float cycle = (time * instance.getFrequency()) % 1.0f;
                    alpha = cycle < 0.5f ? instance.getMaxAlpha() : 0.0f;
                }

                if (alpha > 0.005f) {
                    int a = Math.min(255, Math.max(0, (int) (alpha * 255.0f)));
                    int colorRgb = instance.getColor() & 0x00FFFFFF;
                    int argb = (a << 24) | colorRgb;

                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    graphics.fill(0, 0, screenWidth, screenHeight, argb);
                    RenderSystem.disableBlend();
                }
            }
        });

        // 4. Hue Shift Renderer
        registerRenderer(HueShiftEffect.TYPE, new ScreenEffectRenderer<HueShiftEffect.HueShiftInstance>() {
            @Override
            public void onStart(HueShiftEffect.HueShiftInstance instance) {
                loadColorConvolveShader();
            }

            @Override
            public void onRenderOverlay(HueShiftEffect.HueShiftInstance instance, GuiGraphics graphics, float partialTicks, int screenWidth, int screenHeight) {
                loadColorConvolveShader();

                float angleDegrees;
                if (instance.isContinuous()) {
                    float time = (System.currentTimeMillis() - instance.getStartTimeMs()) / 1000.0f;
                    angleDegrees = (time * instance.getSpeed() * 360.0f) % 360.0f;
                } else {
                    angleDegrees = instance.getHueAngle() % 360.0f;
                }
                if (angleDegrees < 0) angleDegrees += 360.0f;

                double rad = Math.toRadians(angleDegrees);
                float cosA = (float) Math.cos(rad);
                float sinA = (float) Math.sin(rad);
                float sqrt3 = (float) Math.sqrt(3.0);

                // Rodrigues rotation formula around normalized white axis (1, 1, 1) / sqrt(3):
                float oneThird = 1.0f / 3.0f;
                float twoThirds = 2.0f / 3.0f;

                float r_r = oneThird + twoThirds * cosA;
                float r_g = oneThird * (1.0f - cosA) - (sqrt3 / 3.0f) * sinA;
                float r_b = oneThird * (1.0f - cosA) + (sqrt3 / 3.0f) * sinA;

                float g_r = oneThird * (1.0f - cosA) + (sqrt3 / 3.0f) * sinA;
                float g_g = oneThird + twoThirds * cosA;
                float g_b = oneThird * (1.0f - cosA) - (sqrt3 / 3.0f) * sinA;

                float b_r = oneThird * (1.0f - cosA) - (sqrt3 / 3.0f) * sinA;
                float b_g = oneThird * (1.0f - cosA) + (sqrt3 / 3.0f) * sinA;
                float b_b = oneThird + twoThirds * cosA;

                // Linearly interpolate with Identity matrix based on intensity (0.0 to 1.0)
                float k = Math.max(0.0f, Math.min(1.0f, instance.getIntensity()));
                r_r = (1.0f - k) + k * r_r;
                r_g = k * r_g;
                r_b = k * r_b;

                g_r = k * g_r;
                g_g = (1.0f - k) + k * g_g;
                g_b = k * g_b;

                b_r = k * b_r;
                b_g = k * b_g;
                b_b = (1.0f - k) + k * b_b;

                updateColorConvolveMatrix(r_r, r_g, r_b, g_r, g_g, g_b, b_r, b_g, b_b);
            }

            @Override
            public void onEnd(HueShiftEffect.HueShiftInstance instance) {
                shutdownColorConvolveShader();
            }
        });

        // 5. Impact Frame Renderer (Anime High-Contrast Manga Draw / Monochromatic Keyframe)
        registerRenderer(ImpactFrameEffect.TYPE, new ScreenEffectRenderer<ImpactFrameEffect.ImpactFrameInstance>() {
            @Override
            public void onStart(ImpactFrameEffect.ImpactFrameInstance instance) {
                String style = instance.getStyle();
                if ("DRAW".equalsIgnoreCase(style) || "MANGA_DRAW".equalsIgnoreCase(style) || style == null || style.isBlank()) {
                    loadImpactDrawShader();
                } else if ("MANGA_OUTLINE".equalsIgnoreCase(style) || "SOBEL".equalsIgnoreCase(style)) {
                    loadSobelShader();
                } else if (instance.isInvertWorld()) {
                    loadInvertShader();
                } else {
                    loadColorConvolveShader();
                }
            }

            @Override
            public void onRenderOverlay(ImpactFrameEffect.ImpactFrameInstance instance, GuiGraphics graphics, float partialTicks, int screenWidth, int screenHeight) {
                long elapsed = System.currentTimeMillis() - instance.getStartTimeMs();
                int interval = Math.max(20, instance.getFrameIntervalMs());
                int frameIndex = (int) (elapsed / interval);

                // Even frames = negative/inverted frame, Odd frames = positive frame
                boolean isNegativeFrame = (frameIndex % 2 == 0);
                String style = instance.getStyle();

                if ("DRAW".equalsIgnoreCase(style) || "MANGA_DRAW".equalsIgnoreCase(style) || style == null || style.isBlank()) {
                    loadImpactDrawShader();

                    int pColor = instance.getPrimaryColor();
                    int sColor = instance.getSecondaryColor();

                    if (instance.isInvertWorld() && isNegativeFrame) {
                        int tmp = pColor;
                        pColor = sColor;
                        sColor = tmp;
                    }

                    float pR = ((pColor >> 16) & 0xFF) / 255.0f;
                    float pG = ((pColor >> 8) & 0xFF) / 255.0f;
                    float pB = (pColor & 0xFF) / 255.0f;

                    float sR = ((sColor >> 16) & 0xFF) / 255.0f;
                    float sG = ((sColor >> 8) & 0xFF) / 255.0f;
                    float sB = (sColor & 0xFF) / 255.0f;

                    // ColorDark receives shadow/outline color, ColorLight receives highlight/paper color
                    updateImpactDrawUniforms(sR, sG, sB, pR, pG, pB);
                    return;
                }

                if ("MANGA_FULL".equalsIgnoreCase(style)) {
                    loadImpactDrawShader();

                    int pColor = instance.getPrimaryColor();
                    int sColor = instance.getSecondaryColor();

                    if (instance.isInvertWorld() && isNegativeFrame) {
                        int tmp = pColor;
                        pColor = sColor;
                        sColor = tmp;
                    }

                    float pR = ((pColor >> 16) & 0xFF) / 255.0f;
                    float pG = ((pColor >> 8) & 0xFF) / 255.0f;
                    float pB = (pColor & 0xFF) / 255.0f;

                    float sR = ((sColor >> 16) & 0xFF) / 255.0f;
                    float sG = ((sColor >> 8) & 0xFF) / 255.0f;
                    float sB = (sColor & 0xFF) / 255.0f;

                    updateImpactDrawUniforms(sR, sG, sB, pR, pG, pB);
                    MangaDrawImpactRenderer.render(graphics, screenWidth, screenHeight, instance);
                    return;
                }

                // Fallback / legacy modes
                if ("MANGA_OUTLINE".equalsIgnoreCase(style) || "SOBEL".equalsIgnoreCase(style)) {
                    loadSobelShader();
                } else if (instance.isInvertWorld()) {
                    if (isNegativeFrame) {
                        shutdownColorConvolveShader();
                        loadInvertShader();
                    } else {
                        shutdownInvertShader();
                        loadColorConvolveShader();
                        float c = 2.0f;
                        updateColorConvolveMatrix(0.299f * c, 0.587f * c, 0.114f * c,
                                                  0.299f * c, 0.587f * c, 0.114f * c,
                                                  0.299f * c, 0.587f * c, 0.114f * c);
                    }
                } else {
                    loadColorConvolveShader();
                    float c = 2.0f;
                    updateColorConvolveMatrix(0.299f * c, 0.587f * c, 0.114f * c,
                                              0.299f * c, 0.587f * c, 0.114f * c,
                                              0.299f * c, 0.587f * c, 0.114f * c);
                }

                long seed = instance.getStartTimeMs() ^ ((long) frameIndex * 99991L);
                int baseLineColor = isNegativeFrame ? 0xDDFFFFFF : 0xEE000000;
                int accentColor = instance.getPrimaryColor();

                renderAnimeSpeedLines(graphics, screenWidth, screenHeight, seed, baseLineColor, accentColor, 1.0f);

                if ("RADIAL_SHOCK".equalsIgnoreCase(style)) {
                    int barH = Math.min(screenHeight / 5, 28 + (frameIndex % 3) * 4);
                    graphics.fill(0, 0, screenWidth, barH, 0xFF000000);
                    graphics.fill(0, screenHeight - barH, screenWidth, screenHeight, 0xFF000000);
                }
            }

            @Override
            public void onEnd(ImpactFrameEffect.ImpactFrameInstance instance) {
                shutdownImpactDrawShader();
                shutdownInvertShader();
                shutdownColorConvolveShader();
                shutdownSobelShader();
            }
        });
    }

    private static void loadInvertShader() {
        if (!invertShaderLoaded) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameRenderer != null) {
                try {
                    mc.gameRenderer.loadEffect(INVERT_SHADER);
                    invertShaderLoaded = true;
                } catch (Exception e) {
                    FracturedUtils.LOGGER.warn("[ClientScreenEffectHandler] Could not load invert shader, using GL fallback", e);
                }
            }
        }
    }

    private static void shutdownInvertShader() {
        if (invertShaderLoaded) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameRenderer != null) {
                try {
                    mc.gameRenderer.shutdownEffect();
                } catch (Exception ignored) {}
            }
            invertShaderLoaded = false;
        }
    }

    private static void loadColorConvolveShader() {
        if (!colorConvolveShaderLoaded) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameRenderer != null) {
                try {
                    mc.gameRenderer.loadEffect(COLOR_CONVOLVE_SHADER);
                    colorConvolveShaderLoaded = true;
                } catch (Exception e) {
                    FracturedUtils.LOGGER.warn("[ClientScreenEffectHandler] Could not load color_convolve shader", e);
                }
            }
        }
    }

    private static void shutdownColorConvolveShader() {
        if (colorConvolveShaderLoaded) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameRenderer != null) {
                try {
                    mc.gameRenderer.shutdownEffect();
                } catch (Exception ignored) {}
            }
            colorConvolveShaderLoaded = false;
        }
    }

    private static void loadSobelShader() {
        if (!sobelShaderLoaded) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameRenderer != null) {
                try {
                    mc.gameRenderer.loadEffect(SOBEL_SHADER);
                    sobelShaderLoaded = true;
                } catch (Exception e) {
                    FracturedUtils.LOGGER.warn("[ClientScreenEffectHandler] Could not load sobel shader", e);
                }
            }
        }
    }

    private static void shutdownSobelShader() {
        if (sobelShaderLoaded) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameRenderer != null) {
                try {
                    mc.gameRenderer.shutdownEffect();
                } catch (Exception ignored) {}
            }
            sobelShaderLoaded = false;
        }
    }

    private static void loadImpactDrawShader() {
        if (!impactDrawShaderLoaded) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameRenderer != null) {
                try {
                    mc.gameRenderer.loadEffect(IMPACT_DRAW_SHADER);
                    impactDrawShaderLoaded = true;
                } catch (Exception e) {
                    FracturedUtils.LOGGER.warn("[ClientScreenEffectHandler] Could not load impact_draw shader, using fallback", e);
                }
            }
        }
    }

    private static void shutdownImpactDrawShader() {
        if (impactDrawShaderLoaded) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameRenderer != null) {
                try {
                    mc.gameRenderer.shutdownEffect();
                } catch (Exception ignored) {}
            }
            impactDrawShaderLoaded = false;
        }
    }

    private static void updateImpactDrawUniforms(float darkR, float darkG, float darkB, float lightR, float lightG, float lightB) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || mc.gameRenderer.currentEffect() == null) return;
        try {
            net.minecraft.client.renderer.PostChain chain = mc.gameRenderer.currentEffect();
            if (!passesFieldChecked) {
                passesFieldChecked = true;
                for (java.lang.reflect.Field f : net.minecraft.client.renderer.PostChain.class.getDeclaredFields()) {
                    if (java.util.List.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        passesField = f;
                        break;
                    }
                }
            }

            if (passesField != null) {
                java.util.List<?> list = (java.util.List<?>) passesField.get(chain);
                if (list != null) {
                    for (Object passObj : list) {
                        if (passObj instanceof net.minecraft.client.renderer.PostPass pass) {
                            net.minecraft.client.renderer.EffectInstance effect = pass.getEffect();
                            if (effect != null) {
                                com.mojang.blaze3d.shaders.Uniform uDark = effect.getUniform("ColorDark");
                                if (uDark != null) uDark.set(darkR, darkG, darkB, 1.0f);

                                com.mojang.blaze3d.shaders.Uniform uLight = effect.getUniform("ColorLight");
                                if (uLight != null) uLight.set(lightR, lightG, lightB, 1.0f);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Renders anime-style radial sakuga speed lines converging from screen edges toward the focal center.
     */
    private static void renderAnimeSpeedLines(GuiGraphics graphics, int width, int height, long seed, int baseColor, int accentColor, float intensity) {
        float cx = width / 2.0f;
        float cy = height / 2.0f;
        float maxR = (float) Math.hypot(cx, cy) * 1.05f;
        java.util.Random rand = new java.util.Random(seed);

        int numLines = 48 + rand.nextInt(24);
        float step = (float) (Math.PI * 2.0 / numLines);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f pose = graphics.pose().last().pose();

        for (int i = 0; i < numLines; i++) {
            // Jitter angle around uniform circle
            float angle = i * step + (rand.nextFloat() - 0.5f) * step * 0.7f;
            float halfWidth = 0.015f + rand.nextFloat() * 0.035f;

            // Spike points inward towards center (center ~30-45% radius stays clear for focal action)
            float innerR = maxR * (0.28f + rand.nextFloat() * 0.22f);
            float outerR = maxR * (0.95f + rand.nextFloat() * 0.15f);

            // 1 in 5 lines can use the accent tint
            int c = (rand.nextFloat() < 0.22f) ? accentColor : baseColor;
            int a = (c >> 24) & 0xFF;
            if (a == 0) a = 255;
            a = Math.min(255, (int) (a * intensity));
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;

            float cosTip = (float) Math.cos(angle);
            float sinTip = (float) Math.sin(angle);

            float cosA = (float) Math.cos(angle - halfWidth);
            float sinA = (float) Math.sin(angle - halfWidth);

            float cosB = (float) Math.cos(angle + halfWidth);
            float sinB = (float) Math.sin(angle + halfWidth);

            // Triangle: Base Left (outer), Base Right (outer), Tip (inner)
            buffer.vertex(pose, cx + cosA * outerR, cy + sinA * outerR, 0.0f).color(r, g, b, a).endVertex();
            buffer.vertex(pose, cx + cosB * outerR, cy + sinB * outerR, 0.0f).color(r, g, b, a).endVertex();
            buffer.vertex(pose, cx + cosTip * innerR, cy + sinTip * innerR, 0.0f).color(r, g, b, a).endVertex();
        }

        tesselator.end();
        RenderSystem.disableBlend();
    }

    private static void updateColorConvolveMatrix(float r_r, float r_g, float r_b,
                                                  float g_r, float g_g, float g_b,
                                                  float b_r, float b_g, float b_b) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || mc.gameRenderer.currentEffect() == null) return;
        try {
            net.minecraft.client.renderer.PostChain chain = mc.gameRenderer.currentEffect();
            if (!passesFieldChecked) {
                passesFieldChecked = true;
                for (java.lang.reflect.Field f : net.minecraft.client.renderer.PostChain.class.getDeclaredFields()) {
                    if (java.util.List.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        passesField = f;
                        break;
                    }
                }
            }

            if (passesField != null) {
                java.util.List<?> list = (java.util.List<?>) passesField.get(chain);
                if (list != null) {
                    for (Object passObj : list) {
                        if (passObj instanceof net.minecraft.client.renderer.PostPass pass) {
                            net.minecraft.client.renderer.EffectInstance effect = pass.getEffect();
                            if (effect != null) {
                                com.mojang.blaze3d.shaders.Uniform uRed = effect.getUniform("RedMatrix");
                                if (uRed != null) uRed.set(r_r, r_g, r_b);

                                com.mojang.blaze3d.shaders.Uniform uGreen = effect.getUniform("GreenMatrix");
                                if (uGreen != null) uGreen.set(g_r, g_g, g_b);

                                com.mojang.blaze3d.shaders.Uniform uBlue = effect.getUniform("BlueMatrix");
                                if (uBlue != null) uBlue.set(b_r, b_g, b_b);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Registers a custom renderer for a screen effect type.
     */
    public static <T extends ScreenEffectInstance> void registerRenderer(ScreenEffectType<T> type, ScreenEffectRenderer<T> renderer) {
        RENDERERS.put(type.getId(), renderer);
    }

    /**
     * Triggers a screen effect on the client.
     */
    public static void playEffect(ScreenEffectInstance effect) {
        if (effect == null) return;
        // If an effect of the exact same type is already running, end and replace it
        stopEffect(effect.getType().getId());

        ACTIVE_EFFECTS.add(effect);
        @SuppressWarnings("unchecked")
        ScreenEffectRenderer<ScreenEffectInstance> renderer = (ScreenEffectRenderer<ScreenEffectInstance>) RENDERERS.get(effect.getType().getId());
        if (renderer != null) {
            renderer.onStart(effect);
        }
        FracturedUtils.LOGGER.info("[ClientScreenEffectHandler] Started screen effect: {} (duration={}ms)",
                effect.getType().getId(), effect.getDurationMs());
    }

    /**
     * Stops any active screen effects of the given type.
     */
    public static void stopEffect(ResourceLocation typeId) {
        if (typeId == null) return;
        for (Iterator<ScreenEffectInstance> it = ACTIVE_EFFECTS.iterator(); it.hasNext(); ) {
            ScreenEffectInstance instance = it.next();
            if (instance.getType().getId().equals(typeId)) {
                endInstance(instance);
                ACTIVE_EFFECTS.remove(instance);
            }
        }
    }

    /**
     * Stops all active screen effects.
     */
    public static void clearAllEffects() {
        for (ScreenEffectInstance instance : ACTIVE_EFFECTS) {
            endInstance(instance);
        }
        ACTIVE_EFFECTS.clear();
        shutdownInvertShader();
        shutdownColorConvolveShader();
        shutdownSobelShader();
        shutdownImpactDrawShader();
    }

    private static void endInstance(ScreenEffectInstance instance) {
        @SuppressWarnings("unchecked")
        ScreenEffectRenderer<ScreenEffectInstance> renderer = (ScreenEffectRenderer<ScreenEffectInstance>) RENDERERS.get(instance.getType().getId());
        if (renderer != null) {
            renderer.onEnd(instance);
        }
    }

    /**
     * Called every client tick to update effects and clean up expired ones.
     */
    public static void clientTick() {
        if (ACTIVE_EFFECTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();

        for (Iterator<ScreenEffectInstance> it = ACTIVE_EFFECTS.iterator(); it.hasNext(); ) {
            ScreenEffectInstance instance = it.next();
            if (instance.isExpired()) {
                endInstance(instance);
                ACTIVE_EFFECTS.remove(instance);
                continue;
            }

            @SuppressWarnings("unchecked")
            ScreenEffectRenderer<ScreenEffectInstance> renderer = (ScreenEffectRenderer<ScreenEffectInstance>) RENDERERS.get(instance.getType().getId());
            if (renderer != null) {
                renderer.onTick(instance, mc);
            }
        }
    }

    /**
     * Called from {@link net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles}.
     */
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (ACTIVE_EFFECTS.isEmpty()) return;
        float partialTicks = (float) event.getPartialTick();

        for (ScreenEffectInstance instance : ACTIVE_EFFECTS) {
            @SuppressWarnings("unchecked")
            ScreenEffectRenderer<ScreenEffectInstance> renderer = (ScreenEffectRenderer<ScreenEffectInstance>) RENDERERS.get(instance.getType().getId());
            if (renderer != null) {
                renderer.onComputeCameraAngles(instance, event, partialTicks);
            }
        }
    }

    /**
     * Called from {@link net.minecraftforge.client.event.ViewportEvent.ComputeFov}.
     */
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (ACTIVE_EFFECTS.isEmpty()) return;
        float partialTicks = (float) event.getPartialTick();

        for (ScreenEffectInstance instance : ACTIVE_EFFECTS) {
            @SuppressWarnings("unchecked")
            ScreenEffectRenderer<ScreenEffectInstance> renderer = (ScreenEffectRenderer<ScreenEffectInstance>) RENDERERS.get(instance.getType().getId());
            if (renderer != null) {
                renderer.onComputeFov(instance, event, partialTicks);
            }
        }
    }

    /**
     * Renders screen effect overlays directly using the supplied GuiGraphics.
     * Invoked before hotbar/hearts/boss bars so effects layer underneath HUD components.
     */
    public static void renderScreenEffects(GuiGraphics graphics, float partialTicks) {
        if (ACTIVE_EFFECTS.isEmpty() || graphics == null) return;

        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        for (ScreenEffectInstance instance : ACTIVE_EFFECTS) {
            @SuppressWarnings("unchecked")
            ScreenEffectRenderer<ScreenEffectInstance> renderer = (ScreenEffectRenderer<ScreenEffectInstance>) RENDERERS.get(instance.getType().getId());
            if (renderer != null) {
                renderer.onRenderOverlay(instance, graphics, partialTicks, width, height);
            }
        }
    }

    /**
     * Called from {@link net.minecraftforge.client.event.RenderGuiOverlayEvent.Post}.
     */
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (ACTIVE_EFFECTS.isEmpty()) return;
        if (event.getOverlay() == null || !event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) return;

        renderScreenEffects(event.getGuiGraphics(), event.getPartialTick());
    }
}
