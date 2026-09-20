package net.dandare21.fracturedutils.bossbar.client;

import net.dandare21.fracturedutils.bossbar.BossHealthBar;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-side data model for a custom boss healthbar.
 * Contains animation lerping states, ghost damage trail data, and styling properties.
 */
public class ClientBossBarData {
    private final String id;
    private Component name;
    private float targetPercent = 1.0f;
    private float smoothPercent = 1.0f;
    private float ghostPercent = 1.0f;
    private int ghostDelayTicks = 0;

    private float currentHealth = 100.0f;
    private float maxHealth = 100.0f;

    private BossEvent.BossBarColor color = BossEvent.BossBarColor.RED;
    private BossEvent.BossBarOverlay overlay = BossEvent.BossBarOverlay.PROGRESS;
    private String customColorHex = "";

    private String styleId = "default";
    @Nullable
    private ResourceLocation customTexture = null;
    private int barWidth = 182;
    private int barHeight = 5;

    private BossHealthBar.TextDisplayMode textDisplayMode = BossHealthBar.TextDisplayMode.NONE;
    private boolean showGhostBar = true;
    private boolean visible = true;

    private final List<Float> phaseThresholds = new ArrayList<>();

    public ClientBossBarData(String id, Component name) {
        this.id = id;
        this.name = name;
    }

    public void tick() {
        // Smoothly interpolate primary health bar
        float diff = targetPercent - smoothPercent;
        if (Math.abs(diff) > 0.001f) {
            smoothPercent += diff * 0.25f;
        } else {
            smoothPercent = targetPercent;
        }

        // Ghost bar delay and drain
        if (ghostDelayTicks > 0) {
            ghostDelayTicks--;
        } else if (ghostPercent > smoothPercent) {
            ghostPercent += (smoothPercent - ghostPercent) * 0.1f;
            if (Math.abs(ghostPercent - smoothPercent) < 0.001f) {
                ghostPercent = smoothPercent;
            }
        } else {
            ghostPercent = smoothPercent;
        }
    }

    public void updateFrom(ClientBossBarData other) {
        this.name = other.name;
        this.currentHealth = other.currentHealth;
        this.maxHealth = other.maxHealth;

        if (other.targetPercent < this.targetPercent) {
            // Health decreased - trigger ghost bar delay
            this.ghostDelayTicks = 15; // 0.75s delay before ghost drain begins
        } else if (other.targetPercent > this.targetPercent) {
            // Health increased - snap ghost bar immediately
            this.ghostPercent = other.targetPercent;
        }

        this.targetPercent = other.targetPercent;
        this.color = other.color;
        this.overlay = other.overlay;
        this.customColorHex = other.customColorHex;
        this.styleId = other.styleId;
        this.customTexture = other.customTexture;
        this.barWidth = other.barWidth;
        this.barHeight = other.barHeight;
        this.textDisplayMode = other.textDisplayMode;
        this.showGhostBar = other.showGhostBar;
        this.visible = other.visible;

        this.phaseThresholds.clear();
        this.phaseThresholds.addAll(other.phaseThresholds);
    }

    public String getId() {
        return id;
    }

    public Component getName() {
        return name;
    }

    public void setName(Component name) {
        this.name = name;
    }

    public float getTargetPercent() {
        return targetPercent;
    }

    public void setTargetPercent(float targetPercent) {
        this.targetPercent = targetPercent;
    }

    public float getSmoothPercent() {
        return smoothPercent;
    }

    public void setSmoothPercent(float smoothPercent) {
        this.smoothPercent = smoothPercent;
    }

    public float getGhostPercent() {
        return ghostPercent;
    }

    public void setGhostPercent(float ghostPercent) {
        this.ghostPercent = ghostPercent;
    }

    public float getCurrentHealth() {
        return currentHealth;
    }

    public void setCurrentHealth(float currentHealth) {
        this.currentHealth = currentHealth;
    }

    public float getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(float maxHealth) {
        this.maxHealth = maxHealth;
    }

    public BossEvent.BossBarColor getColor() {
        return color;
    }

    public void setColor(BossEvent.BossBarColor color) {
        this.color = color;
    }

    public BossEvent.BossBarOverlay getOverlay() {
        return overlay;
    }

    public void setOverlay(BossEvent.BossBarOverlay overlay) {
        this.overlay = overlay;
    }

    public String getCustomColorHex() {
        return customColorHex;
    }

    public void setCustomColorHex(String customColorHex) {
        this.customColorHex = customColorHex;
    }

    public String getStyleId() {
        return styleId;
    }

    public void setStyleId(String styleId) {
        this.styleId = styleId;
    }

    @Nullable
    public ResourceLocation getCustomTexture() {
        return customTexture;
    }

    public void setCustomTexture(@Nullable ResourceLocation customTexture) {
        this.customTexture = customTexture;
    }

    public int getBarWidth() {
        return barWidth;
    }

    public void setBarWidth(int barWidth) {
        this.barWidth = barWidth;
    }

    public int getBarHeight() {
        return barHeight;
    }

    public void setBarHeight(int barHeight) {
        this.barHeight = barHeight;
    }

    public BossHealthBar.TextDisplayMode getTextDisplayMode() {
        return textDisplayMode;
    }

    public void setTextDisplayMode(BossHealthBar.TextDisplayMode textDisplayMode) {
        this.textDisplayMode = textDisplayMode;
    }

    public boolean isShowGhostBar() {
        return showGhostBar;
    }

    public void setShowGhostBar(boolean showGhostBar) {
        this.showGhostBar = showGhostBar;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public List<Float> getPhaseThresholds() {
        return phaseThresholds;
    }
}
