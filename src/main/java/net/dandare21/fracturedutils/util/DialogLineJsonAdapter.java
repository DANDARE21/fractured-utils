package net.dandare21.fracturedutils.util;

import com.google.gson.*;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.dialog.DialogLine;

import java.lang.reflect.Type;

public class DialogLineJsonAdapter implements JsonSerializer<DialogLine>, JsonDeserializer<DialogLine> {
    public static final DialogLineJsonAdapter INSTANCE = new DialogLineJsonAdapter();

    @Override
    public JsonElement serialize(DialogLine src, Type typeOfSrc, JsonSerializationContext context) {
        if (src == null) return JsonNull.INSTANCE;
        DataResult<JsonElement> result = DialogLine.CODEC.encodeStart(JsonOps.INSTANCE, src);
        return result.result().orElse(new JsonObject());
    }

    @Override
    public DialogLine deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json == null || json.isJsonNull()) return null;
        DataResult<DialogLine> result = DialogLine.CODEC.parse(JsonOps.INSTANCE, json);
        if (result.error().isPresent()) {
            FracturedUtils.LOGGER.warn("[DialogLineJsonAdapter] Warning while parsing DialogLine: {}", result.error().get().message());
        }
        return result.result().orElse(null);
    }
}

