package net.dandare21.fracturedutils.util;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Type;

public class ResourceLocationJsonAdapter implements JsonSerializer<ResourceLocation>, JsonDeserializer<ResourceLocation> {
    public static final ResourceLocationJsonAdapter INSTANCE = new ResourceLocationJsonAdapter();

    @Override
    public JsonElement serialize(ResourceLocation src, Type typeOfSrc, JsonSerializationContext context) {
        return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.toString());
    }

    @Override
    public ResourceLocation deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json == null || json.isJsonNull()) return null;
        try {
            return new ResourceLocation(json.getAsString());
        } catch (Exception e) {
            return null;
        }
    }
}

