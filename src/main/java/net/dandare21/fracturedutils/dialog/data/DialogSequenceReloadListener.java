package net.dandare21.fracturedutils.dialog.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.dialog.DialogManager;
import net.dandare21.fracturedutils.dialog.DialogSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

public class DialogSequenceReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public DialogSequenceReloadListener() {
        super(GSON, "dialog_sequences");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, DialogSequence> loaded = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement json = entry.getValue();

            DialogSequence.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(err -> FracturedUtils.LOGGER.error("[DialogSequence] Error parsing dialog sequence '{}': {}", id, err))
                    .ifPresent(seq -> {
                        seq.setId(id);
                        loaded.put(id, seq);
                    });
        }

        DialogManager.getInstance().onDataPackReload(loaded);
        FracturedUtils.LOGGER.info("[DialogSequence] Reloaded {} dialog sequences from DataPacks", loaded.size());
    }
}

