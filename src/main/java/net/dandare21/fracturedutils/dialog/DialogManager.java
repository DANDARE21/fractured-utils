package net.dandare21.fracturedutils.dialog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.JsonOps;
import net.dandare21.fracturedutils.FracturedUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DialogManager {
    private static final DialogManager INSTANCE = new DialogManager();

    private static final Gson GSON = net.dandare21.fracturedutils.util.ModGson.GSON;

    private final Map<ResourceLocation, DialogSequence> dataPackSequences = new ConcurrentHashMap<>();
    private final List<DialogSequenceInstance> activeSequences = new CopyOnWriteArrayList<>();

    public static DialogManager getInstance() {
        return INSTANCE;
    }

    private DialogManager() {
        ensureDirectoryExists();
    }

    public File getDirectory() {
        File dir = FMLPaths.CONFIGDIR.get().resolve("dialog_sequences").toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public void ensureDirectoryExists() {
        getDirectory();
    }

    public void onDataPackReload(Map<ResourceLocation, DialogSequence> loaded) {
        dataPackSequences.clear();
        if (loaded != null) {
            dataPackSequences.putAll(loaded);
        }
    }

    public DialogSequence getSequence(ResourceLocation id) {
        if (id == null) return null;
        DialogSequence seq = dataPackSequences.get(id);
        if (seq != null) {
            return seq;
        }

        // Fallback: check config/dialog_sequences/<path>.json
        File file = new File(getDirectory(), id.getPath() + ".json");
        if (!file.exists()) {
            file = new File(getDirectory(), id.getPath());
        }
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                JsonElement json = JsonParser.parseReader(reader);
                var result = DialogSequence.CODEC.parse(JsonOps.INSTANCE, json);
                if (result.result().isPresent()) {
                    DialogSequence fallback = result.result().get();
                    fallback.setId(id);
                    return fallback;
                }
            } catch (Exception e) {
                FracturedUtils.LOGGER.error("Failed to parse config fallback dialog file '{}': {}", file.getName(), e.getMessage());
            }
        }
        return null;
    }

    public ResourceLocation parseSequenceId(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) return null;
        String clean = nameOrId.trim();
        if (clean.endsWith(".json")) {
            clean = clean.substring(0, clean.length() - 5);
        }
        clean = clean.replaceAll("[\\\\/]", "");
        if (clean.contains(":")) {
            try {
                return new ResourceLocation(clean);
            } catch (Exception e) {
                return new ResourceLocation(FracturedUtils.MOD_ID, clean.replace(':', '_'));
            }
        }
        return new ResourceLocation(FracturedUtils.MOD_ID, clean);
    }

    public boolean startSequence(ResourceLocation id, Collection<ServerPlayer> targets) {
        if (id == null) return false;
        DialogSequence seq = getSequence(id);
        if (seq == null) {
            FracturedUtils.LOGGER.error("Dialog sequence not found: {}", id);
            return false;
        }

        DialogSequenceInstance instance = new DialogSequenceInstance(id, seq.getLines(), seq.getStyleId(), targets);
        activeSequences.add(instance);
        return true;
    }

    public boolean startSequence(String nameOrId, Collection<ServerPlayer> targets) {
        ResourceLocation id = parseSequenceId(nameOrId);
        if (id == null) return false;
        return startSequence(id, targets);
    }

    public boolean startSequence(ResourceLocation id) {
        return startSequence(id, null);
    }

    public boolean startSequence(String nameOrId) {
        return startSequence(nameOrId, null);
    }

    public Collection<ResourceLocation> getSequenceIds() {
        Set<ResourceLocation> all = new HashSet<>(dataPackSequences.keySet());
        for (String fileName : getSequenceFileNames()) {
            ResourceLocation rl = parseSequenceId(fileName);
            if (rl != null) {
                all.add(rl);
            }
        }
        return all;
    }

    public String sanitizeFileName(String fileName) {
        if (fileName == null) return "";
        fileName = fileName.trim();
        fileName = fileName.replaceAll("[\\\\/]", "");
        if (!fileName.endsWith(".json")) {
            fileName += ".json";
        }
        return fileName;
    }

    public List<String> getSequenceFileNames() {
        File dir = getDirectory();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        List<String> list = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                list.add(f.getName());
            }
        }
        Collections.sort(list);
        return list;
    }

    public Map<String, String> getAllSequenceFiles() {
        Map<String, String> map = new HashMap<>();
        File dir = getDirectory();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                try {
                    String content = Files.readString(f.toPath());
                    map.put(f.getName(), content);
                } catch (IOException e) {
                    FracturedUtils.LOGGER.error("Failed to read dialog file {}: {}", f.getName(), e.getMessage());
                }
            }
        }
        return map;
    }

    public boolean saveSequenceFile(String fileName, String jsonContent) {
        String cleanName = sanitizeFileName(fileName);
        if (cleanName.isEmpty()) return false;

        try {
            JsonElement json = JsonParser.parseString(jsonContent);
            var parseResult = DialogSequence.CODEC.parse(JsonOps.INSTANCE, json);
            if (parseResult.error().isPresent()) {
                FracturedUtils.LOGGER.warn("Dialog JSON validation warning: {}", parseResult.error().get().message());
            }

            File file = new File(getDirectory(), cleanName);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(GSON.toJson(json));
            }
            return true;
        } catch (Exception e) {
            FracturedUtils.LOGGER.error("Failed to save dialog sequence file {}: {}", cleanName, e.getMessage());
            return false;
        }
    }

    public boolean deleteSequenceFile(String fileName) {
        String cleanName = sanitizeFileName(fileName);
        File file = new File(getDirectory(), cleanName);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    public boolean startSingleDialog(DialogLine line, Collection<ServerPlayer> targets) {
        if (line == null) return false;
        DialogLine copy = line.copy();
        copy.setWaitForInput(false);
        copy.setUseCamera(false);

        ResourceLocation singleId = new ResourceLocation(FracturedUtils.MOD_ID, "single_dialog");
        for (DialogSequenceInstance existing : activeSequences) {
            if (singleId.equals(existing.getSequenceId()) || "single_dialog".equals(existing.getFileName())) {
                existing.cancelWithoutClear();
            }
        }
        activeSequences.removeIf(DialogSequenceInstance::isFinished);

        DialogSequenceInstance instance = new DialogSequenceInstance(singleId, List.of(copy), null, targets);
        activeSequences.add(instance);
        return true;
    }

    public void stopAllSequences(MinecraftServer server) {
        for (DialogSequenceInstance instance : activeSequences) {
            instance.stop(server);
        }
        activeSequences.clear();
    }

    public void advanceActiveSequence(MinecraftServer server) {
        for (DialogSequenceInstance instance : activeSequences) {
            if (!instance.isFinished()) {
                instance.advanceLine(server);
                break;
            }
        }
    }

    public boolean skipCurrentLine(MinecraftServer server) {
        for (DialogSequenceInstance instance : activeSequences) {
            if (!instance.isFinished()) {
                instance.advanceLine(server);
                return true;
            }
        }
        return false;
    }

    public void recordPlayerReady(ServerPlayer player, MinecraftServer server) {
        for (DialogSequenceInstance instance : activeSequences) {
            if (!instance.isFinished()) {
                instance.recordPlayerReady(player, server);
                break;
            }
        }
    }

    public void handlePlayerLoggedOut(ServerPlayer player) {
        if (player == null) return;
        for (DialogSequenceInstance instance : activeSequences) {
            instance.handlePlayerLoggedOut(player);
        }
    }

    public boolean isPlayerInDialog(ServerPlayer player) {
        if (player == null) return false;
        UUID uuid = player.getUUID();
        for (DialogSequenceInstance instance : activeSequences) {
            if (!instance.isFinished() && instance.isPlayerTargeted(uuid)) {
                return true;
            }
        }
        return false;
    }

    public boolean isCameraActiveForPlayer(ServerPlayer player) {
        if (player == null) return false;
        UUID uuid = player.getUUID();
        for (DialogSequenceInstance instance : activeSequences) {
            if (!instance.isFinished() && instance.isCameraActiveForPlayer(uuid)) {
                return true;
            }
        }
        return false;
    }

    public boolean isSequenceRunning() {
        return !activeSequences.isEmpty();
    }

    public boolean isSequenceRunning(ResourceLocation id) {
        if (id == null) return isSequenceRunning();
        for (DialogSequenceInstance instance : activeSequences) {
            if (!instance.isFinished() && id.equals(instance.getSequenceId())) {
                return true;
            }
        }
        return false;
    }

    public boolean isSequenceRunning(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) return isSequenceRunning();
        ResourceLocation rl = parseSequenceId(nameOrId);
        if (rl != null && isSequenceRunning(rl)) return true;

        String clean = sanitizeFileName(nameOrId);
        for (DialogSequenceInstance instance : activeSequences) {
            if (!instance.isFinished() && instance.getFileName().equalsIgnoreCase(clean)) {
                return true;
            }
        }
        return false;
    }

    public void tick(MinecraftServer server) {
        Iterator<DialogSequenceInstance> iterator = activeSequences.iterator();
        while (iterator.hasNext()) {
            DialogSequenceInstance instance = iterator.next();
            instance.tick(server);
            if (instance.isFinished()) {
                activeSequences.remove(instance);
            }
        }
    }
}
