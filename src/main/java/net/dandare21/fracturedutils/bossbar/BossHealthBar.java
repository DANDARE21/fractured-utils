package net.dandare21.fracturedutils.bossbar;

import com.google.common.collect.Sets;
import net.dandare21.fracturedutils.bossbar.feature.BossBarFeatureRegistry;
import net.dandare21.fracturedutils.bossbar.feature.IBossBarFeature;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Represents a custom boss healthbar in Fractured Utils.
 * Supports linking single or multiple entities, summed health, custom styles and textures,
 * and modular feature attachments.
 */
public class BossHealthBar {

    public enum TextDisplayMode {
        NONE,
        VALUE,
        PERCENT,
        BOTH;

        public static TextDisplayMode fromString(String name) {
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                return NONE;
            }
        }
    }

    private final String id;
    private Component name;
    private final Set<UUID> linkedEntityUuids = Sets.newLinkedHashSet();
    private final Map<UUID, Float> cachedMaxHealth = new HashMap<>();

    private float currentHealth = 100.0f;
    private float maxHealth = 100.0f;
    private float percent = 1.0f;

    private BossEvent.BossBarColor color = BossEvent.BossBarColor.RED;
    private BossEvent.BossBarOverlay overlay = BossEvent.BossBarOverlay.PROGRESS;
    private String customColorHex = ""; // Optional custom hex color e.g. #FF0055

    private String styleId = "default"; // "default", "fractured", "custom_texture"
    @Nullable
    private ResourceLocation customTexture = null;
    private int barWidth = 182;
    private int barHeight = 5;

    private TextDisplayMode textDisplayMode = TextDisplayMode.NONE;
    private boolean showGhostBar = true;

    private boolean visible = false;
    private boolean autoHideWhenZero = true;
    private boolean autoDeleteWhenDead = true;

    private double range = 0.0; // 0.0 = global to all players
    private boolean allPlayers = true;
    private final Set<UUID> assignedPlayers = Sets.newHashSet();

    private final Map<String, IBossBarFeature> features = new LinkedHashMap<>();

    public BossHealthBar(String id, Component name) {
        this.id = id;
        this.name = name;
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

    public Set<UUID> getLinkedEntityUuids() {
        return linkedEntityUuids;
    }

    public void linkEntity(UUID entityUuid) {
        this.linkedEntityUuids.add(entityUuid);
    }

    public void unlinkEntity(UUID entityUuid) {
        this.linkedEntityUuids.remove(entityUuid);
        this.cachedMaxHealth.remove(entityUuid);
    }

    public void clearLinkedEntities() {
        this.linkedEntityUuids.clear();
        this.cachedMaxHealth.clear();
    }

    public Map<UUID, Float> getCachedMaxHealth() {
        return cachedMaxHealth;
    }

    public float getCurrentHealth() {
        return currentHealth;
    }

    public void setCurrentHealth(float currentHealth) {
        this.currentHealth = Math.max(0.0f, currentHealth);
        recomputePercent();
    }

    public float getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(float maxHealth) {
        this.maxHealth = Math.max(0.001f, maxHealth);
        recomputePercent();
    }

    public float getPercent() {
        return percent;
    }

    private void recomputePercent() {
        if (maxHealth <= 0.0001f) {
            this.percent = 0.0f;
        } else {
            this.percent = Math.max(0.0f, Math.min(1.0f, currentHealth / maxHealth));
        }
    }

    public BossEvent.BossBarColor getColor() {
        return color;
    }

    public void setColor(BossEvent.BossBarColor color) {
        this.color = color;
    }

    public String getCustomColorHex() {
        return customColorHex;
    }

    public void setCustomColorHex(String customColorHex) {
        this.customColorHex = customColorHex != null ? customColorHex : "";
    }

    public BossEvent.BossBarOverlay getOverlay() {
        return overlay;
    }

    public void setOverlay(BossEvent.BossBarOverlay overlay) {
        this.overlay = overlay;
    }

    public String getStyleId() {
        return styleId;
    }

    public void setStyleId(String styleId) {
        this.styleId = styleId != null ? styleId : "default";
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
        this.barWidth = Math.max(10, barWidth);
    }

    public int getBarHeight() {
        return barHeight;
    }

    public void setBarHeight(int barHeight) {
        this.barHeight = Math.max(1, barHeight);
    }

    public TextDisplayMode getTextDisplayMode() {
        return textDisplayMode;
    }

    public void setTextDisplayMode(TextDisplayMode textDisplayMode) {
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

    public boolean isAutoHideWhenZero() {
        return autoHideWhenZero;
    }

    public void setAutoHideWhenZero(boolean autoHideWhenZero) {
        this.autoHideWhenZero = autoHideWhenZero;
    }

    public boolean isAutoDeleteWhenDead() {
        return autoDeleteWhenDead;
    }

    public void setAutoDeleteWhenDead(boolean autoDeleteWhenDead) {
        this.autoDeleteWhenDead = autoDeleteWhenDead;
    }

    public double getRange() {
        return range;
    }

    public void setRange(double range) {
        this.range = Math.max(0.0, range);
    }

    public boolean isAllPlayers() {
        return allPlayers;
    }

    public void setAllPlayers(boolean allPlayers) {
        this.allPlayers = allPlayers;
    }

    public Set<UUID> getAssignedPlayers() {
        return assignedPlayers;
    }

    public void addAssignedPlayer(UUID playerUuid) {
        this.assignedPlayers.add(playerUuid);
    }

    public void removeAssignedPlayer(UUID playerUuid) {
        this.assignedPlayers.remove(playerUuid);
    }

    public void clearAssignedPlayers() {
        this.assignedPlayers.clear();
    }

    public Map<String, IBossBarFeature> getFeatures() {
        return features;
    }

    public void addFeature(IBossBarFeature feature) {
        this.features.put(feature.getFeatureId(), feature);
    }

    public void removeFeature(String featureId) {
        this.features.remove(featureId);
    }

    // --- Network Serialization ---

    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeUtf(this.id);
        buf.writeComponent(this.name);
        buf.writeFloat(this.percent);
        buf.writeFloat(this.currentHealth);
        buf.writeFloat(this.maxHealth);

        buf.writeEnum(this.color);
        buf.writeEnum(this.overlay);
        buf.writeUtf(this.customColorHex);

        buf.writeUtf(this.styleId);
        buf.writeBoolean(this.customTexture != null);
        if (this.customTexture != null) {
            buf.writeResourceLocation(this.customTexture);
        }
        buf.writeInt(this.barWidth);
        buf.writeInt(this.barHeight);

        buf.writeEnum(this.textDisplayMode);
        buf.writeBoolean(this.showGhostBar);
        buf.writeBoolean(this.visible);

        buf.writeInt(this.features.size());
        for (IBossBarFeature feature : this.features.values()) {
            buf.writeUtf(feature.getFeatureId());
            feature.toNetwork(buf);
        }
    }

    // --- NBT Serialization ---

    public CompoundTag saveNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", this.id);
        tag.putString("Name", Component.Serializer.toJson(this.name));

        ListTag entitiesTag = new ListTag();
        for (UUID uuid : this.linkedEntityUuids) {
            entitiesTag.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("LinkedEntities", entitiesTag);

        CompoundTag cachedMaxTag = new CompoundTag();
        for (Map.Entry<UUID, Float> entry : this.cachedMaxHealth.entrySet()) {
            cachedMaxTag.putFloat(entry.getKey().toString(), entry.getValue());
        }
        tag.put("CachedMaxHealth", cachedMaxTag);

        tag.putFloat("CurrentHealth", this.currentHealth);
        tag.putFloat("MaxHealth", this.maxHealth);
        tag.putString("Color", this.color.name());
        tag.putString("Overlay", this.overlay.name());
        tag.putString("CustomColorHex", this.customColorHex);

        tag.putString("StyleId", this.styleId);
        if (this.customTexture != null) {
            tag.putString("CustomTexture", this.customTexture.toString());
        }
        tag.putInt("BarWidth", this.barWidth);
        tag.putInt("BarHeight", this.barHeight);

        tag.putString("TextDisplayMode", this.textDisplayMode.name());
        tag.putBoolean("ShowGhostBar", this.showGhostBar);
        tag.putBoolean("Visible", this.visible);
        tag.putBoolean("AutoHideWhenZero", this.autoHideWhenZero);
        tag.putBoolean("AutoDeleteWhenDead", this.autoDeleteWhenDead);

        tag.putDouble("Range", this.range);
        tag.putBoolean("AllPlayers", this.allPlayers);

        ListTag playersTag = new ListTag();
        for (UUID uuid : this.assignedPlayers) {
            playersTag.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("AssignedPlayers", playersTag);

        CompoundTag featuresTag = new CompoundTag();
        for (Map.Entry<String, IBossBarFeature> entry : this.features.entrySet()) {
            featuresTag.put(entry.getKey(), entry.getValue().serializeNbt());
        }
        tag.put("Features", featuresTag);

        return tag;
    }

    public static BossHealthBar loadNbt(CompoundTag tag) {
        String id = tag.getString("Id");
        Component name;
        try {
            name = Component.Serializer.fromJson(tag.getString("Name"));
            if (name == null) name = Component.literal(id);
        } catch (Exception e) {
            name = Component.literal(id);
        }

        BossHealthBar bar = new BossHealthBar(id, name);

        if (tag.contains("LinkedEntities", Tag.TAG_LIST)) {
            ListTag list = tag.getList("LinkedEntities", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                try {
                    bar.linkedEntityUuids.add(UUID.fromString(list.getString(i)));
                } catch (Exception ignored) {}
            }
        }

        if (tag.contains("CachedMaxHealth", Tag.TAG_COMPOUND)) {
            CompoundTag cached = tag.getCompound("CachedMaxHealth");
            for (String key : cached.getAllKeys()) {
                try {
                    bar.cachedMaxHealth.put(UUID.fromString(key), cached.getFloat(key));
                } catch (Exception ignored) {}
            }
        }

        if (tag.contains("CurrentHealth")) bar.currentHealth = tag.getFloat("CurrentHealth");
        if (tag.contains("MaxHealth")) bar.maxHealth = tag.getFloat("MaxHealth");
        bar.recomputePercent();

        if (tag.contains("Color")) {
            try {
                bar.color = BossEvent.BossBarColor.valueOf(tag.getString("Color"));
            } catch (Exception ignored) {}
        }
        if (tag.contains("Overlay")) {
            try {
                bar.overlay = BossEvent.BossBarOverlay.valueOf(tag.getString("Overlay"));
            } catch (Exception ignored) {}
        }
        if (tag.contains("CustomColorHex")) bar.customColorHex = tag.getString("CustomColorHex");

        if (tag.contains("StyleId")) bar.styleId = tag.getString("StyleId");
        if (tag.contains("CustomTexture")) {
            String tex = tag.getString("CustomTexture");
            if (!tex.isEmpty()) {
                bar.customTexture = ResourceLocation.tryParse(tex);
            }
        }
        if (tag.contains("BarWidth")) bar.barWidth = tag.getInt("BarWidth");
        if (tag.contains("BarHeight")) bar.barHeight = tag.getInt("BarHeight");

        if (tag.contains("TextDisplayMode")) {
            bar.textDisplayMode = TextDisplayMode.fromString(tag.getString("TextDisplayMode"));
        }
        if (tag.contains("ShowGhostBar")) bar.showGhostBar = tag.getBoolean("ShowGhostBar");
        if (tag.contains("Visible")) bar.visible = tag.getBoolean("Visible");
        if (tag.contains("AutoHideWhenZero")) bar.autoHideWhenZero = tag.getBoolean("AutoHideWhenZero");
        if (tag.contains("AutoDeleteWhenDead")) bar.autoDeleteWhenDead = tag.getBoolean("AutoDeleteWhenDead");

        if (tag.contains("Range")) bar.range = tag.getDouble("Range");
        if (tag.contains("AllPlayers")) bar.allPlayers = tag.getBoolean("AllPlayers");

        if (tag.contains("AssignedPlayers", Tag.TAG_LIST)) {
            ListTag list = tag.getList("AssignedPlayers", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                try {
                    bar.assignedPlayers.add(UUID.fromString(list.getString(i)));
                } catch (Exception ignored) {}
            }
        }

        if (tag.contains("Features", Tag.TAG_COMPOUND)) {
            CompoundTag featuresTag = tag.getCompound("Features");
            for (String key : featuresTag.getAllKeys()) {
                IBossBarFeature feature = BossBarFeatureRegistry.create(key);
                if (feature != null) {
                    feature.deserializeNbt(featuresTag.getCompound(key));
                    bar.features.put(key, feature);
                }
            }
        }

        return bar;
    }
}
