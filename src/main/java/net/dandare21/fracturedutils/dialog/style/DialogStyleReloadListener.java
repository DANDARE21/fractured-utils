package net.dandare21.fracturedutils.dialog.style;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.dandare21.fracturedutils.FracturedUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

public class DialogStyleReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public DialogStyleReloadListener() {
        super(GSON, "dialog_styles");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        DialogStyleRegistry.reset();
        int count = 0;

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            JsonElement json = entry.getValue();

            DialogStyle.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(err -> FracturedUtils.LOGGER.error("[DialogStyle] Error decoding style '{}': {}", fileId, err))
                    .ifPresent(style -> {
                        // If the style inside JSON did not explicitly match file ID, use fileId
                        DialogStyle registeredStyle = style.id().equals(fileId) ? style : new DialogStyle(
                                fileId,
                                style.texture(),
                                style.nineSlice(),
                                style.backgroundColor(),
                                style.borderColor(),
                                style.innerBorderColor(),
                                style.cornerColor(),
                                style.defaultTextColor(),
                                style.speakerBadgeBgColor(),
                                style.speakerBadgeBorderColor(),
                                style.speakerTextColor(),
                                style.paddingLeft(),
                                style.paddingRight(),
                                style.paddingTop(),
                                style.paddingBottom(),
                                style.drawCornerNotches()
                        );
                        DialogStyleRegistry.register(registeredStyle);
                    });
            count++;
        }

        FracturedUtils.LOGGER.info("[DialogStyle] Reloaded {} custom dialog styles from DataPacks", count);
    }
}

