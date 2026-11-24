package net.dandare21.fracturedutils.npc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.network.ModMessages;
import net.dandare21.fracturedutils.npc.network.S2CSyncNpcDefinitionsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NpcDefinitionManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final NpcDefinitionManager INSTANCE = new NpcDefinitionManager();

    private final Map<ResourceLocation, NpcDefinition> definitions = new ConcurrentHashMap<>();
    private MinecraftServer currentServer = null;

    public static NpcDefinitionManager getInstance() {
        return INSTANCE;
    }

    public NpcDefinitionManager() {
        super(GSON, "npcs");
    }

    public void setServer(MinecraftServer server) {
        this.currentServer = server;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, NpcDefinition> loaded = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement json = entry.getValue();

            NpcDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(err -> FracturedUtils.LOGGER.error("[NPC] Error parsing NPC definition '{}': {}", id, err))
                    .ifPresent(def -> loaded.put(id, def));
        }

        definitions.clear();
        definitions.putAll(loaded);
        FracturedUtils.LOGGER.info("[NPC] Loaded {} NPC definitions from DataPacks", definitions.size());

        if (currentServer != null) {
            syncToAll(currentServer);
        }
    }

    public void registerOrUpdateDefinition(NpcDefinition def) {
        if (def == null || def.id() == null) return;
        definitions.put(def.id(), def);
        if (currentServer != null) {
            syncToAll(currentServer);
        }
    }

    public NpcDefinition getDefinition(ResourceLocation id) {
        if (id == null) return null;
        return definitions.get(id);
    }

    public Collection<NpcDefinition> getAllDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public Set<ResourceLocation> getAllIds() {
        return Collections.unmodifiableSet(definitions.keySet());
    }

    public void syncToPlayer(ServerPlayer player) {
        if (player == null) return;
        ModMessages.sendToPlayer(new S2CSyncNpcDefinitionsPacket(new ArrayList<>(definitions.values())), player);
    }

    public void syncToAll(MinecraftServer server) {
        if (server == null) return;
        S2CSyncNpcDefinitionsPacket packet = new S2CSyncNpcDefinitionsPacket(new ArrayList<>(definitions.values()));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ModMessages.sendToPlayer(packet, player);
        }
    }
}
