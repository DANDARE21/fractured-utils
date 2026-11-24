package net.dandare21.fracturedutils.npc.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.npc.NpcDefinition;
import net.dandare21.fracturedutils.npc.NpcDefinitionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;

public class GeneratedPackManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static boolean saveNpcTemplate(MinecraftServer server, NpcDefinition definition) {
        if (server == null || definition == null || definition.id() == null) {
            return false;
        }

        try {
            Path datapackDir = server.getWorldPath(LevelResource.DATAPACK_DIR);
            File generatedPack = datapackDir.resolve("generated_npcs").toFile();
            if (!generatedPack.exists()) {
                generatedPack.mkdirs();
            }

            // Ensure pack.mcmeta exists
            File mcmeta = new File(generatedPack, "pack.mcmeta");
            if (!mcmeta.exists()) {
                String metaContent = "{\n  \"pack\": {\n    \"pack_format\": 15,\n    \"description\": \"Generated Custom NPCs DataPack\"\n  }\n}";
                try (FileWriter writer = new FileWriter(mcmeta)) {
                    writer.write(metaContent);
                }
            }

            // Path: data/<namespace>/npcs/<path>.json
            String namespace = definition.id().getNamespace();
            String path = definition.id().getPath();
            File targetDir = new File(generatedPack, "data/" + namespace + "/npcs");
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            File targetFile = new File(targetDir, path + ".json");
            JsonElement json = NpcDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                    .resultOrPartial(err -> FracturedUtils.LOGGER.error("Failed to encode NPC definition: {}", err))
                    .orElse(null);

            if (json == null) {
                return false;
            }

            try (FileWriter writer = new FileWriter(targetFile)) {
                GSON.toJson(json, writer);
            }

            // Register in memory and broadcast immediately
            NpcDefinitionManager.getInstance().registerOrUpdateDefinition(definition);
            FracturedUtils.LOGGER.info("[GeneratedPackManager] Saved NPC template '{}' to {}", definition.id(), targetFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            FracturedUtils.LOGGER.error("[GeneratedPackManager] Error saving NPC template '{}': {}", definition.id(), e.getMessage());
            return false;
        }
    }
}
