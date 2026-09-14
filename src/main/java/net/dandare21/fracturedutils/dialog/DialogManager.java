package net.dandare21.fracturedutils.dialog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.dandare21.fracturedutils.FracturedUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class DialogManager {
    private static final DialogManager INSTANCE = new DialogManager();

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

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
            // Validate JSON syntax by attempting parsing
            List<DialogLine> lines = GSON.fromJson(jsonContent, new TypeToken<List<DialogLine>>() {}.getType());
            if (lines == null) {
                lines = new ArrayList<>();
            }
            String formattedJson = GSON.toJson(lines);

            File file = new File(getDirectory(), cleanName);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(formattedJson);
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

    public boolean startSequence(String fileName, Collection<ServerPlayer> targets) {
        String cleanName = sanitizeFileName(fileName);
        File file = new File(getDirectory(), cleanName);

        if (!file.exists()) {
            FracturedUtils.LOGGER.error("Dialog file not found: {}", file.getAbsolutePath());
            return false;
        }

        try (FileReader reader = new FileReader(file)) {
            List<DialogLine> lines = GSON.fromJson(reader, new TypeToken<List<DialogLine>>() {}.getType());
            if (lines == null) {
                lines = new ArrayList<>();
            }
            DialogSequenceInstance instance = new DialogSequenceInstance(cleanName, lines, targets);
            activeSequences.add(instance);
            return true;
        } catch (Exception e) {
            FracturedUtils.LOGGER.error("Failed to parse dialog file {}: {}", cleanName, e.getMessage());
            return false;
        }
    }

    public boolean startSequence(String fileName) {
        return startSequence(fileName, null);
    }

    public boolean startSingleDialog(DialogLine line, Collection<ServerPlayer> targets) {
        if (line == null) return false;
        DialogLine copy = line.copy();
        copy.setWaitForInput(false);
        copy.setUseCamera(false);

        for (DialogSequenceInstance existing : activeSequences) {
            if ("single_dialog".equals(existing.getFileName())) {
                existing.cancelWithoutClear();
            }
        }
        activeSequences.removeIf(DialogSequenceInstance::isFinished);

        DialogSequenceInstance instance = new DialogSequenceInstance("single_dialog", List.of(copy), targets);
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

    public boolean isSequenceRunning(String fileName) {
        if (fileName == null || fileName.isBlank()) return isSequenceRunning();
        String cleanName = sanitizeFileName(fileName);
        for (DialogSequenceInstance instance : activeSequences) {
            if (!instance.isFinished() && instance.getFileName().equalsIgnoreCase(cleanName)) {
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
