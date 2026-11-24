package net.dandare21.fracturedutils.npc.client.asset;

import net.dandare21.fracturedutils.npc.client.DynamicNpcGeoModel;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class NpcAssetScanner {

    public static List<String> getAvailableModels() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return List.of();
        ResourceManager rm = mc.getResourceManager();
        List<String> list = new ArrayList<>();

        try {
            var resources = rm.listResources("geo", loc -> loc.getPath().endsWith(".geo.json"));
            for (ResourceLocation loc : resources.keySet()) {
                list.add(loc.toString());
            }
        } catch (Exception ignored) {}

        if (list.isEmpty()) {
            list.add("fractured_utils:geo/void_herald.geo.json");
        }
        Collections.sort(list);
        return list;
    }

    public static List<String> getAvailableTextures() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return List.of();
        ResourceManager rm = mc.getResourceManager();
        List<String> list = new ArrayList<>();

        try {
            var resources = rm.listResources("textures/entity", loc -> loc.getPath().endsWith(".png"));
            for (ResourceLocation loc : resources.keySet()) {
                list.add(loc.toString());
            }
        } catch (Exception ignored) {}

        if (list.isEmpty()) {
            list.add("fractured_utils:textures/entity/void_herald.png");
            list.add("minecraft:textures/entity/steve.png");
            list.add("minecraft:textures/entity/alex.png");
        }
        Collections.sort(list);
        return list;
    }

    public static List<String> getAvailableAnimations() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return List.of();
        ResourceManager rm = mc.getResourceManager();
        List<String> list = new ArrayList<>();

        try {
            var resources = rm.listResources("animations", loc -> loc.getPath().endsWith(".animation.json"));
            for (ResourceLocation loc : resources.keySet()) {
                list.add(loc.toString());
            }
        } catch (Exception ignored) {}

        if (list.isEmpty()) {
            list.add("fractured_utils:animations/void_herald.animation.json");
        }
        Collections.sort(list);
        return list;
    }

    public static Optional<ResourceLocation> findBestMatchingTexture(ResourceLocation modelLoc) {
        if (modelLoc == null) return Optional.empty();
        String namespace = modelLoc.getNamespace();
        String path = modelLoc.getPath();
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        String baseName = fileName;
        if (baseName.endsWith(".geo.json")) {
            baseName = baseName.substring(0, baseName.length() - ".geo.json".length());
        } else if (baseName.endsWith(".json")) {
            baseName = baseName.substring(0, baseName.length() - ".json".length());
        }

        // Subpath between "geo/" and the filename
        String subpath = "";
        int geoIdx = path.indexOf("geo/");
        if (geoIdx != -1) {
            int start = geoIdx + "geo/".length();
            int end = path.lastIndexOf('/') + 1;
            if (end > start) {
                subpath = path.substring(start, end);
            }
        }

        // 1. Exact candidate paths
        ResourceLocation[] exactCandidates = {
                new ResourceLocation(namespace, "textures/" + subpath + baseName + ".png"),
                new ResourceLocation(namespace, "textures/entity/" + baseName + ".png"),
                new ResourceLocation(namespace, "textures/" + baseName + ".png"),
                new ResourceLocation(namespace, "textures/entity/" + subpath + baseName + ".png")
        };
        for (ResourceLocation c : exactCandidates) {
            if (DynamicNpcGeoModel.doesResourceExist(c)) {
                return Optional.of(c);
            }
        }

        // 2. Keyword & fuzzy scoring over all available entity textures
        List<String> available = getAvailableTextures();
        String[] keywords = baseName.toLowerCase(Locale.ROOT).split("[_\\-]");
        String bestMatch = null;
        int highestScore = 0;

        for (String texStr : available) {
            ResourceLocation texLoc = ResourceLocation.tryParse(texStr);
            if (texLoc == null) continue;

            int score = 0;
            boolean sameNamespace = texLoc.getNamespace().equalsIgnoreCase(namespace);
            if (sameNamespace) {
                score += 30;
            }

            String texPath = texLoc.getPath().toLowerCase(Locale.ROOT);
            String texFile = texPath.substring(texPath.lastIndexOf('/') + 1);
            if (texFile.endsWith(".png")) {
                texFile = texFile.substring(0, texFile.length() - ".png".length());
            }

            // Direct match
            if (texFile.equalsIgnoreCase(baseName)) {
                score += 100;
            } else if (texFile.contains(baseName) || baseName.contains(texFile)) {
                score += 70;
            }

            // Keyword matching (e.g. "nihil" in "general_nihil" matching "nihil_texture")
            for (String kw : keywords) {
                if (kw.length() < 3) continue;
                if (kw.equals("mob") || kw.equals("entity") || kw.equals("geo") || kw.equals("model") || kw.equals("custom")) continue;
                if (texFile.contains(kw)) {
                    score += 45;
                }
            }

            // Stripped base matching (e.g. "nihil_texture" -> "nihil", "general_nihil" contains "nihil")
            String cleanTex = texFile.replace("texture", "").replace("_", "").trim();
            String cleanBase = baseName.replace("model", "").replace("_", "").trim();
            if (!cleanTex.isEmpty() && (cleanBase.contains(cleanTex) || cleanTex.contains(cleanBase))) {
                score += 40;
            }

            if (score > highestScore && score >= 40) {
                highestScore = score;
                bestMatch = texStr;
            }
        }

        if (bestMatch != null) {
            return Optional.ofNullable(ResourceLocation.tryParse(bestMatch));
        }

        return Optional.empty();
    }

    public static Optional<ResourceLocation> findBestMatchingAnimation(ResourceLocation modelLoc) {
        if (modelLoc == null) return Optional.empty();
        String namespace = modelLoc.getNamespace();
        String path = modelLoc.getPath();
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        String baseName = fileName;
        if (baseName.endsWith(".geo.json")) {
            baseName = baseName.substring(0, baseName.length() - ".geo.json".length());
        } else if (baseName.endsWith(".json")) {
            baseName = baseName.substring(0, baseName.length() - ".json".length());
        }

        String subpath = "";
        int geoIdx = path.indexOf("geo/");
        if (geoIdx != -1) {
            int start = geoIdx + "geo/".length();
            int end = path.lastIndexOf('/') + 1;
            if (end > start) {
                subpath = path.substring(start, end);
            }
        }

        // 1. Exact candidates
        ResourceLocation[] exactCandidates = {
                new ResourceLocation(namespace, "animations/" + subpath + baseName + ".animation.json"),
                new ResourceLocation(namespace, "animations/entity/" + baseName + ".animation.json"),
                new ResourceLocation(namespace, "animations/" + baseName + ".animation.json"),
                new ResourceLocation(namespace, "animations/" + baseName + ".json")
        };
        for (ResourceLocation c : exactCandidates) {
            if (DynamicNpcGeoModel.doesResourceExist(c)) {
                return Optional.of(c);
            }
        }

        // 2. Keyword & fuzzy scoring over all available animations
        List<String> available = getAvailableAnimations();
        String[] keywords = baseName.toLowerCase(Locale.ROOT).split("[_\\-]");
        String bestMatch = null;
        int highestScore = 0;

        for (String animStr : available) {
            ResourceLocation animLoc = ResourceLocation.tryParse(animStr);
            if (animLoc == null) continue;

            int score = 0;
            boolean sameNamespace = animLoc.getNamespace().equalsIgnoreCase(namespace);
            if (sameNamespace) {
                score += 30;
            }

            String animPath = animLoc.getPath().toLowerCase(Locale.ROOT);
            String animFile = animPath.substring(animPath.lastIndexOf('/') + 1);
            if (animFile.endsWith(".animation.json")) {
                animFile = animFile.substring(0, animFile.length() - ".animation.json".length());
            } else if (animFile.endsWith(".json")) {
                animFile = animFile.substring(0, animFile.length() - ".json".length());
            }

            if (animFile.equalsIgnoreCase(baseName)) {
                score += 100;
            } else if (animFile.contains(baseName) || baseName.contains(animFile)) {
                score += 70;
            }

            for (String kw : keywords) {
                if (kw.length() < 3) continue;
                if (kw.equals("mob") || kw.equals("entity") || kw.equals("animation") || kw.equals("custom")) continue;
                if (animFile.contains(kw)) {
                    score += 45;
                }
            }

            if (score > highestScore && score >= 40) {
                highestScore = score;
                bestMatch = animStr;
            }
        }

        if (bestMatch != null) {
            return Optional.ofNullable(ResourceLocation.tryParse(bestMatch));
        }

        return Optional.empty();
    }

    public record ModelDimensions(float width, float height, float eyeHeight) {}

    private static final java.util.Map<ResourceLocation, ModelDimensions> DIMENSIONS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static ModelDimensions computeModelDimensions(ResourceLocation modelLoc) {
        if (modelLoc == null) {
            return new ModelDimensions(0.6F, 1.8F, 1.62F);
        }
        if (DIMENSIONS_CACHE.containsKey(modelLoc)) {
            return DIMENSIONS_CACHE.get(modelLoc);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) {
            return new ModelDimensions(0.6F, 1.8F, 1.62F);
        }

        try {
            var resOpt = mc.getResourceManager().getResource(modelLoc);
            if (resOpt.isPresent()) {
                try (var reader = resOpt.get().openAsReader()) {
                    com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                    if (root.has("minecraft:geometry")) {
                        com.google.gson.JsonArray geomArray = root.getAsJsonArray("minecraft:geometry");
                        if (geomArray.size() > 0) {
                            com.google.gson.JsonObject geom = geomArray.get(0).getAsJsonObject();
                            float minX = Float.POSITIVE_INFINITY;
                            float maxX = Float.NEGATIVE_INFINITY;
                            float minY = Float.POSITIVE_INFINITY;
                            float maxY = Float.NEGATIVE_INFINITY;
                            float minZ = Float.POSITIVE_INFINITY;
                            float maxZ = Float.NEGATIVE_INFINITY;

                            if (geom.has("bones")) {
                                com.google.gson.JsonArray bones = geom.getAsJsonArray("bones");
                                for (com.google.gson.JsonElement bElem : bones) {
                                    com.google.gson.JsonObject bone = bElem.getAsJsonObject();
                                    if (bone.has("cubes")) {
                                        com.google.gson.JsonArray cubes = bone.getAsJsonArray("cubes");
                                        for (com.google.gson.JsonElement cElem : cubes) {
                                            com.google.gson.JsonObject cube = cElem.getAsJsonObject();
                                            if (cube.has("origin") && cube.has("size")) {
                                                com.google.gson.JsonArray origin = cube.getAsJsonArray("origin");
                                                com.google.gson.JsonArray size = cube.getAsJsonArray("size");
                                                if (origin.size() >= 3 && size.size() >= 3) {
                                                    float ox = origin.get(0).getAsFloat();
                                                    float oy = origin.get(1).getAsFloat();
                                                    float oz = origin.get(2).getAsFloat();

                                                    float sx = size.get(0).getAsFloat();
                                                    float sy = size.get(1).getAsFloat();
                                                    float sz = size.get(2).getAsFloat();

                                                    minX = Math.min(minX, ox);
                                                    maxX = Math.max(maxX, ox + sx);

                                                    minY = Math.min(minY, oy);
                                                    maxY = Math.max(maxY, oy + sy);

                                                    minZ = Math.min(minZ, oz);
                                                    maxZ = Math.max(maxZ, oz + sz);
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (maxY > Float.NEGATIVE_INFINITY && maxX > Float.NEGATIVE_INFINITY) {
                                // 16 units in Blockbench equals 1 Minecraft block
                                float widthX = (maxX - minX) / 16.0F;
                                float widthZ = (maxZ - minZ) / 16.0F;
                                float width = Math.max(widthX, widthZ);

                                float bottomY = Math.max(0.0F, minY);
                                float height = (maxY - bottomY) / 16.0F;

                                // Round to nearest 0.05
                                width = Math.round(Math.max(0.2F, Math.min(10.0F, width)) * 20.0F) / 20.0F;
                                height = Math.round(Math.max(0.2F, Math.min(20.0F, height)) * 20.0F) / 20.0F;
                                float eyeHeight = Math.round((height * 0.88F) * 20.0F) / 20.0F;

                                ModelDimensions result = new ModelDimensions(width, height, eyeHeight);
                                DIMENSIONS_CACHE.put(modelLoc, result);
                                return result;
                            }

                            // Fallback to visible_bounds in geometry description
                            if (geom.has("description")) {
                                com.google.gson.JsonObject desc = geom.getAsJsonObject("description");
                                if (desc.has("visible_bounds_width") && desc.has("visible_bounds_height")) {
                                    float w = desc.get("visible_bounds_width").getAsFloat();
                                    float h = desc.get("visible_bounds_height").getAsFloat();
                                    ModelDimensions result = new ModelDimensions(Math.max(0.4F, w * 0.5F), Math.max(0.5F, h * 0.8F), h * 0.7F);
                                    DIMENSIONS_CACHE.put(modelLoc, result);
                                    return result;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        ModelDimensions fallback = new ModelDimensions(0.6F, 1.8F, 1.62F);
        DIMENSIONS_CACHE.put(modelLoc, fallback);
        return fallback;
    }

    public static List<String> getSkinPresets() {
        return List.of(
                "Steve",
                "Alex",
                "Ari",
                "Efe",
                "Kai",
                "Makena",
                "Noor",
                "Sunny",
                "Zuri"
        );
    }
}
