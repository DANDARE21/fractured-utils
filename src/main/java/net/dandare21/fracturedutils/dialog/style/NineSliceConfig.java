package net.dandare21.fracturedutils.dialog.style;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record NineSliceConfig(
        int textureWidth,
        int textureHeight,
        int sliceLeft,
        int sliceTop,
        int sliceRight,
        int sliceBottom
) {
    public static final Codec<NineSliceConfig> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("textureWidth").forGetter(NineSliceConfig::textureWidth),
                    Codec.INT.fieldOf("textureHeight").forGetter(NineSliceConfig::textureHeight),
                    Codec.INT.fieldOf("sliceLeft").forGetter(NineSliceConfig::sliceLeft),
                    Codec.INT.fieldOf("sliceTop").forGetter(NineSliceConfig::sliceTop),
                    Codec.INT.fieldOf("sliceRight").forGetter(NineSliceConfig::sliceRight),
                    Codec.INT.fieldOf("sliceBottom").forGetter(NineSliceConfig::sliceBottom)
            ).apply(instance, NineSliceConfig::new)
    );

    public static NineSliceConfig uniform(int texW, int texH, int border) {
        return new NineSliceConfig(texW, texH, border, border, border, border);
    }
}

