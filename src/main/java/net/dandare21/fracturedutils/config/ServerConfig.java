package net.dandare21.fracturedutils.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.dandare21.fracturedutils.FracturedUtils;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FMLPaths.CONFIGDIR.get().resolve("fracturedutils-server.json");

    private static boolean keepInventoryNoXp = false;
    private static boolean enableDebugBoss = false;
    private static int teamWipeScreenDurationSeconds = 3;
    private static int eventAudioPort = 8085;
    private static String eventAudioExternalUrl = "";
    private static boolean eventAudioRequirePack = true;
    private static String eventAudioNamespace = "fracturedutils";
    private static boolean loaded = false;

    public static synchronized void load() {
        if (loaded) return;
        try {
            if (Files.exists(CONFIG_FILE)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json != null) {
                        if (json.has("keepInventoryNoXp")) {
                            keepInventoryNoXp = json.get("keepInventoryNoXp").getAsBoolean();
                        }
                        if (json.has("enableDebugBoss")) {
                            enableDebugBoss = json.get("enableDebugBoss").getAsBoolean();
                        }
                        if (json.has("teamWipeScreenDurationSeconds")) {
                            teamWipeScreenDurationSeconds = Math.max(1, json.get("teamWipeScreenDurationSeconds").getAsInt());
                        }
                        if (json.has("eventAudioPort")) {
                            eventAudioPort = Math.max(1024, json.get("eventAudioPort").getAsInt());
                        }
                        if (json.has("eventAudioExternalUrl")) {
                            eventAudioExternalUrl = json.get("eventAudioExternalUrl").getAsString();
                        }
                        if (json.has("eventAudioRequirePack")) {
                            eventAudioRequirePack = json.get("eventAudioRequirePack").getAsBoolean();
                        }
                        if (json.has("eventAudioNamespace")) {
                            eventAudioNamespace = json.get("eventAudioNamespace").getAsString();
                        }
                        FracturedUtils.LOGGER.info("[ServerConfig] Loaded server configuration.");
                    }
                }
            } else {
                save();
            }
        } catch (Exception e) {
            FracturedUtils.LOGGER.warn("[ServerConfig] Failed to load server config", e);
        } finally {
            loaded = true;
        }
    }

    public static synchronized void save() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("keepInventoryNoXp", keepInventoryNoXp);
            json.addProperty("enableDebugBoss", enableDebugBoss);
            json.addProperty("teamWipeScreenDurationSeconds", teamWipeScreenDurationSeconds);
            json.addProperty("eventAudioPort", eventAudioPort);
            json.addProperty("eventAudioExternalUrl", eventAudioExternalUrl);
            json.addProperty("eventAudioRequirePack", eventAudioRequirePack);
            json.addProperty("eventAudioNamespace", eventAudioNamespace);

            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            FracturedUtils.LOGGER.warn("[ServerConfig] Failed to save server config", e);
        }
    }

    public static boolean isKeepInventoryNoXpEnabled() {
        load();
        return keepInventoryNoXp;
    }

    public static void setKeepInventoryNoXpEnabled(boolean enabled) {
        load();
        if (keepInventoryNoXp != enabled) {
            keepInventoryNoXp = enabled;
            save();
        }
    }

    public static boolean isEnableDebugBoss() {
        load();
        return enableDebugBoss;
    }

    public static void setEnableDebugBoss(boolean enabled) {
        load();
        if (enableDebugBoss != enabled) {
            enableDebugBoss = enabled;
            save();
        }
    }

    public static int getTeamWipeScreenDurationSeconds() {
        load();
        return teamWipeScreenDurationSeconds;
    }

    public static void setTeamWipeScreenDurationSeconds(int seconds) {
        load();
        int val = Math.max(1, seconds);
        if (teamWipeScreenDurationSeconds != val) {
            teamWipeScreenDurationSeconds = val;
            save();
        }
    }

    public static int getEventAudioPort() {
        load();
        return eventAudioPort;
    }

    public static void setEventAudioPort(int port) {
        load();
        if (eventAudioPort != port) {
            eventAudioPort = port;
            save();
        }
    }

    public static String getEventAudioExternalUrl() {
        load();
        return eventAudioExternalUrl;
    }

    public static void setEventAudioExternalUrl(String url) {
        load();
        if (url != null && !eventAudioExternalUrl.equals(url)) {
            eventAudioExternalUrl = url;
            save();
        }
    }

    public static boolean isEventAudioRequirePack() {
        load();
        return eventAudioRequirePack;
    }

    public static void setEventAudioRequirePack(boolean requirePack) {
        load();
        if (eventAudioRequirePack != requirePack) {
            eventAudioRequirePack = requirePack;
            save();
        }
    }

    public static String getEventAudioNamespace() {
        load();
        return eventAudioNamespace;
    }

    public static void setEventAudioNamespace(String namespace) {
        load();
        if (namespace != null && !eventAudioNamespace.equals(namespace)) {
            eventAudioNamespace = namespace;
            save();
        }
    }
}
