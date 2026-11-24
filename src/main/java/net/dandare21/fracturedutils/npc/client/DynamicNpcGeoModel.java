package net.dandare21.fracturedutils.npc.client;

import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.npc.NpcDefinition;
import net.dandare21.fracturedutils.npc.entity.CustomNpcEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class DynamicNpcGeoModel extends GeoModel<CustomNpcEntity> {
    public static final ResourceLocation FALLBACK_MODEL =
            new ResourceLocation(FracturedUtils.MOD_ID, "geo/void_herald.geo.json");
    public static final ResourceLocation FALLBACK_TEXTURE =
            new ResourceLocation(FracturedUtils.MOD_ID, "textures/entity/void_herald.png");
    public static final ResourceLocation FALLBACK_ANIMATION =
            new ResourceLocation(FracturedUtils.MOD_ID, "animations/void_herald.animation.json");

    public static boolean isValidTexture(ResourceLocation loc) {
        if (loc == null) return false;
        String path = loc.getPath();
        return path.endsWith(".png") && !path.endsWith("/");
    }

    public static boolean isValidModel(ResourceLocation loc) {
        if (loc == null) return false;
        String path = loc.getPath();
        return (path.endsWith(".geo.json") || path.endsWith(".json")) && !path.endsWith("/");
    }

    public static boolean isValidAnimation(ResourceLocation loc) {
        if (loc == null) return false;
        String path = loc.getPath();
        return (path.endsWith(".animation.json") || path.endsWith(".json")) && !path.endsWith("/");
    }

    public static ResourceLocation deriveMatchingTexture(ResourceLocation modelLoc) {
        if (modelLoc == null) return FALLBACK_TEXTURE;
        return net.dandare21.fracturedutils.npc.client.asset.NpcAssetScanner.findBestMatchingTexture(modelLoc)
                .orElse(FALLBACK_TEXTURE);
    }

    public static ResourceLocation deriveMatchingAnimation(ResourceLocation modelLoc) {
        if (modelLoc == null) return FALLBACK_ANIMATION;
        return net.dandare21.fracturedutils.npc.client.asset.NpcAssetScanner.findBestMatchingAnimation(modelLoc)
                .orElse(FALLBACK_ANIMATION);
    }

    public static boolean doesResourceExist(ResourceLocation loc) {
        if (loc == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) return false;
        return mc.getResourceManager().getResource(loc).isPresent();
    }

    @Override
    public ResourceLocation getModelResource(CustomNpcEntity animatable) {
        NpcDefinition def = animatable.getDefinition();
        if (def != null && def.modelLocation().isPresent()) {
            ResourceLocation candidate = def.modelLocation().get();
            if (isValidModel(candidate) && doesResourceExist(candidate)) {
                return candidate;
            }
        }
        return FALLBACK_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(CustomNpcEntity animatable) {
        NpcDefinition def = animatable.getDefinition();
        if (def != null && def.textureLocation().isPresent()) {
            ResourceLocation candidate = def.textureLocation().get();
            if (isValidTexture(candidate) && doesResourceExist(candidate)) {
                return candidate;
            }
        }
        // If texture not explicitly set or invalid, try deriving from modelLocation
        if (def != null && def.modelLocation().isPresent()) {
            ResourceLocation derived = deriveMatchingTexture(def.modelLocation().get());
            if (isValidTexture(derived) && doesResourceExist(derived)) {
                return derived;
            }
        }
        return FALLBACK_TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(CustomNpcEntity animatable) {
        NpcDefinition def = animatable.getDefinition();
        if (def != null && def.animationLocation().isPresent()) {
            ResourceLocation candidate = def.animationLocation().get();
            if (isValidAnimation(candidate) && doesResourceExist(candidate)) {
                return candidate;
            }
        }
        // If animation not explicitly set or invalid, try deriving from modelLocation
        if (def != null && def.modelLocation().isPresent()) {
            ResourceLocation derived = deriveMatchingAnimation(def.modelLocation().get());
            if (isValidAnimation(derived) && doesResourceExist(derived)) {
                return derived;
            }
        }
        return FALLBACK_ANIMATION;
    }

    public static final DynamicNpcGeoModel INSTANCE = new DynamicNpcGeoModel();

    public static String resolveAnimationName(CustomNpcEntity animatable, String baseName, String suffix) {
        String[] candidates = {
            "animation." + baseName + "." + suffix,
            baseName + "." + suffix,
            "animation." + suffix,
            suffix,
            "animation.model." + suffix
        };
        for (String c : candidates) {
            try {
                if (INSTANCE.getAnimation(animatable, c) != null) {
                    return c;
                }
            } catch (Exception ignored) {}
        }
        return "animation." + baseName + "." + suffix;
    }

    public static String resolveMovementAnimationName(CustomNpcEntity animatable, String baseName) {
        String[] candidates = {
            "animation." + baseName + ".walk",
            baseName + ".walk",
            "animation.walk",
            "walk",
            "animation." + baseName + ".move",
            baseName + ".move",
            "animation.move",
            "move",
            "animation." + baseName + ".run",
            "run",
            "animation.model.walk"
        };
        for (String c : candidates) {
            try {
                if (INSTANCE.getAnimation(animatable, c) != null) {
                    return c;
                }
            } catch (Exception ignored) {}
        }
        return "animation." + baseName + ".walk";
    }
}
