package net.dandare21.fracturedutils.npc.client;

import net.dandare21.fracturedutils.npc.NpcDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientNpcData {
    private static final Map<ResourceLocation, NpcDefinition> CLIENT_DEFINITIONS = new ConcurrentHashMap<>();

    public static void updateDefinitions(List<NpcDefinition> definitions) {
        CLIENT_DEFINITIONS.clear();
        if (definitions != null) {
            for (NpcDefinition def : definitions) {
                if (def != null && def.id() != null) {
                    CLIENT_DEFINITIONS.put(def.id(), def);
                }
            }
        }
    }

    public static NpcDefinition getDefinition(ResourceLocation id) {
        if (id == null) return null;
        return CLIENT_DEFINITIONS.get(id);
    }

    public static Collection<NpcDefinition> getAllDefinitions() {
        return Collections.unmodifiableCollection(CLIENT_DEFINITIONS.values());
    }

    public static List<ResourceLocation> getAllIds() {
        return new ArrayList<>(CLIENT_DEFINITIONS.keySet());
    }
}
