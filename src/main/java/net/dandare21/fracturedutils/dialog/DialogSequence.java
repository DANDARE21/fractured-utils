package net.dandare21.fracturedutils.dialog;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DialogSequence {
    private ResourceLocation id;
    private String name;
    private List<DialogLine> lines;
    private ResourceLocation styleId;

    private static final Codec<DialogSequence> OBJECT_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    DialogLine.CODEC.listOf().fieldOf("lines").forGetter(DialogSequence::getLines),
                    ResourceLocation.CODEC.optionalFieldOf("style").forGetter(DialogSequence::getStyleIdOptional)
            ).apply(instance, (lines, styleOpt) -> new DialogSequence(null, lines, styleOpt.orElse(null)))
    );

    public static final Codec<DialogSequence> CODEC = Codec.either(DialogLine.CODEC.listOf(), OBJECT_CODEC).xmap(
            either -> either.map(
                    lines -> new DialogSequence(null, lines, null),
                    obj -> obj
            ),
            Either::right
    );

    public DialogSequence() {
        this.name = "new_dialog";
        this.lines = new ArrayList<>();
        this.styleId = null;
    }

    public DialogSequence(String name, List<DialogLine> lines) {
        this(name != null && name.contains(":") ? new ResourceLocation(name) : null, lines, null);
        this.name = name != null ? name : "new_dialog";
    }

    public DialogSequence(ResourceLocation id, List<DialogLine> lines, ResourceLocation styleId) {
        this.id = id;
        this.name = id != null ? id.toString() : "new_dialog";
        this.lines = lines != null ? new ArrayList<>(lines) : new ArrayList<>();
        this.styleId = styleId;
    }

    public ResourceLocation getId() {
        return id;
    }

    public void setId(ResourceLocation id) {
        this.id = id;
        if (id != null) {
            this.name = id.toString();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name : "";
    }

    public List<DialogLine> getLines() {
        return lines;
    }

    public void setLines(List<DialogLine> lines) {
        this.lines = lines != null ? lines : new ArrayList<>();
    }

    public ResourceLocation getStyleId() {
        return styleId;
    }

    public Optional<ResourceLocation> getStyleIdOptional() {
        return Optional.ofNullable(styleId);
    }

    public void setStyleId(ResourceLocation styleId) {
        this.styleId = styleId;
    }
}
