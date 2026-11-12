package net.dandare21.fracturedutils.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.dandare21.fracturedutils.dialog.DialogLine;
import net.minecraft.resources.ResourceLocation;

public class ModGson {
    public static GsonBuilder createBuilder() {
        return new GsonBuilder()
                .registerTypeAdapterFactory(OptionalTypeAdapterFactory.INSTANCE)
                .registerTypeAdapter(DialogLine.class, DialogLineJsonAdapter.INSTANCE)
                .registerTypeAdapter(ResourceLocation.class, ResourceLocationJsonAdapter.INSTANCE);
    }

    public static final Gson GSON = createBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
}

