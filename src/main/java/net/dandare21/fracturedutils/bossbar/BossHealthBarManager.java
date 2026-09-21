package net.dandare21.fracturedutils.bossbar;

import com.mojang.logging.LogUtils;
import net.dandare21.fracturedutils.bossbar.network.S2CSyncBossBarsPacket;
import net.dandare21.fracturedutils.network.ModMessages;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.dandare21.fracturedutils.threshold.HealthThreshold;
import net.dandare21.fracturedutils.threshold.HealthThresholdManager;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side manager for Boss Healthbars in Fractured Utils.
 * Handles entity tracking, multi-entity health summation, auto-hiding,
 * auto-deletion on entity death/despawn, and client synchronization.
 */
public class BossHealthBarManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BossHealthBarManager INSTANCE = new BossHealthBarManager();

    private final Map<String, BossHealthBar> bossBars = new LinkedHashMap<>();
    private final Map<UUID, List<String>> lastSyncedBarsPerPlayer = new ConcurrentHashMap<>();
    private boolean initializedFromSave = false;

    public static BossHealthBarManager getInstance() {
        return INSTANCE;
    }

    private BossHealthBarManager() {
    }

    public synchronized void onServerStarting(MinecraftServer server) {
        bossBars.clear();
        lastSyncedBarsPerPlayer.clear();
        try {
            BossHealthBarSavedData savedData = BossHealthBarSavedData.get(server);
            for (BossHealthBar bar : savedData.getBossBars()) {
                bossBars.put(bar.getId(), bar);
            }
            initializedFromSave = true;
            LOGGER.info("[BossHealthBarManager] Loaded {} boss healthbars from world storage.", bossBars.size());
        } catch (Exception e) {
            LOGGER.error("[BossHealthBarManager] Failed to load saved bossbars", e);
        }
    }

    public synchronized void onServerStopping(MinecraftServer server) {
        saveToWorld(server);
        bossBars.clear();
        lastSyncedBarsPerPlayer.clear();
        initializedFromSave = false;
    }

    public synchronized void saveToWorld(MinecraftServer server) {
        if (server == null || server.overworld() == null) return;
        try {
            BossHealthBarSavedData savedData = BossHealthBarSavedData.get(server);
            savedData.setBossBars(new ArrayList<>(bossBars.values()));
        } catch (Exception e) {
            LOGGER.error("[BossHealthBarManager] Failed to save bossbars to world storage", e);
        }
    }

    public synchronized BossHealthBar createBossBar(String id, Component name) {
        BossHealthBar bar = new BossHealthBar(id, name);
        bossBars.put(id, bar);
        return bar;
    }

    @Nullable
    public synchronized BossHealthBar getBossBar(String id) {
        return bossBars.get(id);
    }

    public synchronized Collection<BossHealthBar> getAllBossBars() {
        return Collections.unmodifiableCollection(new ArrayList<>(bossBars.values()));
    }

    public synchronized boolean removeBossBar(String id) {
        BossHealthBar removed = bossBars.remove(id);
        return removed != null;
    }

    public synchronized void linkEntity(String barId, LivingEntity entity) {
        BossHealthBar bar = bossBars.get(barId);
        if (bar != null && entity != null) {
            bar.linkEntity(entity.getUUID());
            bar.getCachedMaxHealth().put(entity.getUUID(), entity.getMaxHealth());
        }
    }

    public synchronized void unlinkEntity(String barId, UUID entityUuid) {
        BossHealthBar bar = bossBars.get(barId);
        if (bar != null) {
            bar.unlinkEntity(entityUuid);
        }
    }

    /**
     * Ticked once every server tick from ServerEventHandler.
     */
    public synchronized void tick(MinecraftServer server) {
        if (server == null) return;

        List<String> barsToDelete = new ArrayList<>();

        for (BossHealthBar bar : bossBars.values()) {
            // Tick modular features
            bar.getFeatures().values().forEach(feat -> feat.tick(bar, server));

            if (!bar.getLinkedEntityUuids().isEmpty()) {
                float sumCurrentHealth = 0.0f;
                float sumMaxHealth = 0.0f;
                boolean hasAnyAlive = false;
                List<Float> activeThresholds = new ArrayList<>();

                for (UUID entityUuid : bar.getLinkedEntityUuids()) {
                    LivingEntity living = findLivingEntity(server, entityUuid);
                    if (living != null && living.isAlive() && !living.isRemoved()) {
                        hasAnyAlive = true;
                        sumCurrentHealth += Math.max(0.0f, living.getHealth());
                        sumMaxHealth += living.getMaxHealth();
                        bar.getCachedMaxHealth().put(entityUuid, living.getMaxHealth());

                        // For a single linked entity, collect all active thresholds on this entity
                        if (bar.getLinkedEntityUuids().size() == 1) {
                            List<HealthThreshold> ths = HealthThresholdManager.getThresholds(living);
                            for (HealthThreshold th : ths) {
                                float minHp = th.getMinHealthResolved(living);
                                if (minHp > 0.0f && living.getMaxHealth() > 0.0f) {
                                    float frac = Math.max(0.005f, Math.min(0.995f, minHp / living.getMaxHealth()));
                                    if (!activeThresholds.contains(frac)) {
                                        activeThresholds.add(frac);
                                    }
                                }
                            }
                        }
                    } else {
                        // Entity is dead or removed/despawned
                        Float cachedMax = bar.getCachedMaxHealth().get(entityUuid);
                        if (cachedMax != null) {
                            sumMaxHealth += cachedMax;
                        }
                    }
                }

                // For multiple linked entities, calculate combined threshold from active barriers
                if (bar.getLinkedEntityUuids().size() > 1 && sumMaxHealth > 0.0f) {
                    float sumMinHp = 0.0f;
                    boolean anyThreshold = false;
                    for (UUID entityUuid : bar.getLinkedEntityUuids()) {
                        LivingEntity living = findLivingEntity(server, entityUuid);
                        if (living != null && living.isAlive() && !living.isRemoved()) {
                            Optional<HealthThreshold> activeOpt = HealthThresholdManager.getActiveThreshold(living);
                            if (activeOpt.isPresent()) {
                                anyThreshold = true;
                                sumMinHp += activeOpt.get().getMinHealthResolved(living);
                            }
                        }
                    }
                    if (anyThreshold && sumMinHp > 0.0f) {
                        float combinedFrac = Math.max(0.005f, Math.min(0.995f, sumMinHp / sumMaxHealth));
                        if (!activeThresholds.contains(combinedFrac)) {
                            activeThresholds.add(combinedFrac);
                        }
                    }
                }

                Collections.sort(activeThresholds);
                bar.setEntityThresholds(activeThresholds);

                bar.setCurrentHealth(sumCurrentHealth);
                bar.setMaxHealth(Math.max(1.0f, sumMaxHealth));

                // Auto-hide and auto-delete check:
                // "only hide and delete the healthbar if the entities linked to it are actually dead/despawned"
                if (!hasAnyAlive) {
                    if (bar.isAutoHideWhenZero()) {
                        bar.setVisible(false);
                    }
                    if (bar.isAutoDeleteWhenDead()) {
                        barsToDelete.add(bar.getId());
                    }
                } else if (sumCurrentHealth <= 0.0f) {
                    if (bar.isAutoHideWhenZero()) {
                        bar.setVisible(false);
                    }
                }
            }
        }

        // Delete bossbars whose entities are actually dead/despawned
        if (!barsToDelete.isEmpty()) {
            for (String barId : barsToDelete) {
                bossBars.remove(barId);
                LOGGER.info("[BossHealthBarManager] Auto-deleted boss healthbar '{}' as all linked entities are dead/despawned.", barId);
            }
            saveToWorld(server);
        }

        // Synchronize active bars to connected players
        syncToPlayers(server);
    }

    private void syncToPlayers(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();

        for (ServerPlayer player : players) {
            List<BossHealthBar> visibleBarsForPlayer = new ArrayList<>();

            for (BossHealthBar bar : bossBars.values()) {
                if (!bar.isVisible()) {
                    continue;
                }

                // Check player eligibility
                if (!bar.isAllPlayers() && !bar.getAssignedPlayers().contains(player.getUUID())) {
                    continue;
                }

                // Check range if specified
                if (bar.getRange() > 0.0) {
                    boolean inRange = false;
                    double rangeSqr = bar.getRange() * bar.getRange();

                    for (UUID uuid : bar.getLinkedEntityUuids()) {
                        LivingEntity living = findLivingEntity(server, uuid);
                        if (living != null && living.level() == player.level() && living.isAlive()) {
                            if (player.distanceToSqr(living) <= rangeSqr) {
                                inRange = true;
                                break;
                            }
                        }
                    }

                    if (!inRange) {
                        continue;
                    }
                }

                visibleBarsForPlayer.add(bar);
            }

            // Send packet to this player
            ModMessages.sendToPlayer(new S2CSyncBossBarsPacket(visibleBarsForPlayer), player);
        }
    }

    public synchronized void onPlayerJoin(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        // Sync immediately on join
        List<BossHealthBar> visibleBarsForPlayer = new ArrayList<>();
        for (BossHealthBar bar : bossBars.values()) {
            if (bar.isVisible()) {
                if (bar.isAllPlayers() || bar.getAssignedPlayers().contains(player.getUUID())) {
                    visibleBarsForPlayer.add(bar);
                }
            }
        }
        ModMessages.sendToPlayer(new S2CSyncBossBarsPacket(visibleBarsForPlayer), player);
    }

    @Nullable
    private LivingEntity findLivingEntity(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }
}
