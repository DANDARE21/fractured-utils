package net.dandare21.fracturedutils.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.dandare21.fracturedutils.FracturedUtils;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FMLPaths.CONFIGDIR.get().resolve("fracturedutils-client.json");

    private static int videoVolumePercent = 100;
    private static boolean opMonitorEnabled = true;
    private static int opMonitorX = -1;
    private static int opMonitorY = -1;
    private static float opMonitorOpacity = 0.6f;
    private static float opMonitorScale = 1.0f;
    private static float regionMapOpacity = 0.5f;
    private static String regionMapDisplayMode = "ALL";

    private static boolean loaded = false;

    public static synchronized void load() {
        if (loaded) return;
        try {
            if (Files.exists(CONFIG_FILE)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json != null) {
                        if (json.has("videoVolumePercent")) {
                            videoVolumePercent = Math.max(0, Math.min(100, json.get("videoVolumePercent").getAsInt()));
                        }
                        if (json.has("opMonitorEnabled")) {
                            opMonitorEnabled = json.get("opMonitorEnabled").getAsBoolean();
                        }
                        if (json.has("opMonitorX")) {
                            opMonitorX = json.get("opMonitorX").getAsInt();
                        }
                        if (json.has("opMonitorY")) {
                            opMonitorY = json.get("opMonitorY").getAsInt();
                        }
                        if (json.has("opMonitorOpacity")) {
                            opMonitorOpacity = Math.max(0.1f, Math.min(1.0f, json.get("opMonitorOpacity").getAsFloat()));
                        }
                        if (json.has("opMonitorScale")) {
                            opMonitorScale = Math.max(0.5f, Math.min(2.0f, json.get("opMonitorScale").getAsFloat()));
                        }
                        if (json.has("regionMapOpacity")) {
                            regionMapOpacity = Math.max(0.0f, Math.min(1.0f, json.get("regionMapOpacity").getAsFloat()));
                        }
                        if (json.has("regionMapDisplayMode")) {
                            regionMapDisplayMode = json.get("regionMapDisplayMode").getAsString();
                        }
                        FracturedUtils.LOGGER.info("[ClientConfig] Loaded client configuration.");
                    }
                }
            } else {
                save();
            }
        } catch (Exception e) {
            FracturedUtils.LOGGER.warn("[ClientConfig] Failed to load client config", e);
        } finally {
            loaded = true;
        }
    }

    public static synchronized void save() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("videoVolumePercent", videoVolumePercent);
            json.addProperty("opMonitorEnabled", opMonitorEnabled);
            json.addProperty("opMonitorX", opMonitorX);
            json.addProperty("opMonitorY", opMonitorY);
            json.addProperty("opMonitorOpacity", opMonitorOpacity);
            json.addProperty("opMonitorScale", opMonitorScale);
            json.addProperty("regionMapOpacity", regionMapOpacity);
            json.addProperty("regionMapDisplayMode", regionMapDisplayMode);

            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            FracturedUtils.LOGGER.warn("[ClientConfig] Failed to save client config", e);
        }
    }

    public static int getVideoVolumePercent() {
        load();
        return videoVolumePercent;
    }

    public static void setVideoVolumePercent(int percent) {
        load();
        int clamped = Math.max(0, Math.min(100, percent));
        if (videoVolumePercent != clamped) {
            videoVolumePercent = clamped;
            save();
        }
    }

    public static boolean isOpMonitorEnabled() {
        load();
        return opMonitorEnabled;
    }

    public static void setOpMonitorEnabled(boolean enabled) {
        load();
        if (opMonitorEnabled != enabled) {
            opMonitorEnabled = enabled;
            save();
        }
    }

    public static int getOpMonitorX() {
        load();
        return opMonitorX;
    }

    public static void setOpMonitorX(int x) {
        load();
        int val = x < 0 ? -1 : x;
        if (opMonitorX != val) {
            opMonitorX = val;
            save();
        }
    }

    public static int getOpMonitorY() {
        load();
        return opMonitorY;
    }

    public static void setOpMonitorY(int y) {
        load();
        int val = y < 0 ? -1 : y;
        if (opMonitorY != val) {
            opMonitorY = val;
            save();
        }
    }

    public static float getOpMonitorOpacity() {
        load();
        return opMonitorOpacity;
    }

    public static void setOpMonitorOpacity(float opacity) {
        load();
        float clamped = Math.max(0.1f, Math.min(1.0f, opacity));
        if (opMonitorOpacity != clamped) {
            opMonitorOpacity = clamped;
            save();
        }
    }

    public static float getOpMonitorScale() {
        load();
        return opMonitorScale;
    }

    public static void setOpMonitorScale(float scale) {
        load();
        float clamped = Math.max(0.5f, Math.min(2.0f, scale));
        if (opMonitorScale != clamped) {
            opMonitorScale = clamped;
            save();
        }
    }

    public static void resetOpMonitorSettings() {
        load();
        opMonitorX = -1;
        opMonitorY = -1;
        opMonitorOpacity = 0.6f;
        opMonitorScale = 1.0f;
        save();
    }

    public static float getRegionMapOpacity() {
        load();
        return regionMapOpacity;
    }

    public static void setRegionMapOpacity(float opacity) {
        load();
        float clamped = Math.max(0.0f, Math.min(1.0f, opacity));
        if (Math.abs(regionMapOpacity - clamped) > 0.001f) {
            regionMapOpacity = clamped;
            save();
        }
    }

    public static String getRegionMapDisplayMode() {
        load();
        return regionMapDisplayMode != null ? regionMapDisplayMode : "ALL";
    }

    public static void setRegionMapDisplayMode(String mode) {
        load();
        if (mode != null && !mode.equals(regionMapDisplayMode)) {
            regionMapDisplayMode = mode;
            save();
        }
    }
}
