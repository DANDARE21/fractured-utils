package net.dandare21.fracturedutils.dialog.style;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record DialogStyle(
        ResourceLocation id,
        Optional<ResourceLocation> texture,
        Optional<NineSliceConfig> nineSlice,
        int backgroundColor,
        int borderColor,
        int innerBorderColor,
        int cornerColor,
        int defaultTextColor,
        int speakerBadgeBgColor,
        int speakerBadgeBorderColor,
        int speakerTextColor,
        int paddingLeft,
        int paddingRight,
        int paddingTop,
        int paddingBottom,
        boolean drawCornerNotches
) {
    public static final Codec<DialogStyle> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("id").forGetter(DialogStyle::id),
                    ResourceLocation.CODEC.optionalFieldOf("texture").forGetter(DialogStyle::texture),
                    NineSliceConfig.CODEC.optionalFieldOf("nineSlice").forGetter(DialogStyle::nineSlice),
                    Codec.INT.optionalFieldOf("backgroundColor", 0xF505090C).forGetter(DialogStyle::backgroundColor),
                    Codec.INT.optionalFieldOf("borderColor", 0xFF00E5FF).forGetter(DialogStyle::borderColor),
                    Codec.INT.optionalFieldOf("innerBorderColor", 0x4400E5FF).forGetter(DialogStyle::innerBorderColor),
                    Codec.INT.optionalFieldOf("cornerColor", 0xFF00E5FF).forGetter(DialogStyle::cornerColor),
                    Codec.INT.optionalFieldOf("defaultTextColor", 0xFFFFFFFF).forGetter(DialogStyle::defaultTextColor),
                    Codec.INT.optionalFieldOf("speakerBadgeBgColor", 0xF50A1622).forGetter(DialogStyle::speakerBadgeBgColor),
                    Codec.INT.optionalFieldOf("speakerBadgeBorderColor", 0xFF00E5FF).forGetter(DialogStyle::speakerBadgeBorderColor),
                    Codec.INT.optionalFieldOf("speakerTextColor", 0xFFFFFFFF).forGetter(DialogStyle::speakerTextColor),
                    Codec.INT.optionalFieldOf("paddingLeft", 16).forGetter(DialogStyle::paddingLeft),
                    Codec.INT.optionalFieldOf("paddingRight", 16).forGetter(DialogStyle::paddingRight),
                    Codec.INT.optionalFieldOf("paddingTop", 8).forGetter(DialogStyle::paddingTop),
                    Codec.INT.optionalFieldOf("paddingBottom", 8).forGetter(DialogStyle::paddingBottom),
                    Codec.BOOL.optionalFieldOf("drawCornerNotches", true).forGetter(DialogStyle::drawCornerNotches)
            ).apply(instance, DialogStyle::new)
    );
}

