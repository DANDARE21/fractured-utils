#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform float LumaRamp;
uniform float LumaLevel;
uniform vec4 ColorDark;
uniform vec4 ColorLight;

out vec4 fragColor;

void main(){
    vec4 center = texture(DiffuseSampler, texCoord);

    // Multi-tap edge outline detection (cardinal 1-step and 2-step offsets)
    vec4 up     = texture(DiffuseSampler, texCoord + vec2(        0.0, -oneTexel.y));
    vec4 up2    = texture(DiffuseSampler, texCoord + vec2(        0.0, -oneTexel.y) * 2.0);
    vec4 down   = texture(DiffuseSampler, texCoord + vec2( oneTexel.x,         0.0));
    vec4 down2  = texture(DiffuseSampler, texCoord + vec2( oneTexel.x,         0.0) * 2.0);
    vec4 left   = texture(DiffuseSampler, texCoord + vec2(-oneTexel.x,         0.0));
    vec4 left2  = texture(DiffuseSampler, texCoord + vec2(-oneTexel.x,         0.0) * 2.0);
    vec4 right  = texture(DiffuseSampler, texCoord + vec2(        0.0,  oneTexel.y));
    vec4 right2 = texture(DiffuseSampler, texCoord + vec2(        0.0,  oneTexel.y) * 2.0);

    vec4 uDiff = abs(center - up);
    vec4 dDiff = abs(center - down);
    vec4 lDiff = abs(center - left);
    vec4 rDiff = abs(center - right);
    vec4 u2Diff = abs(center - up2);
    vec4 d2Diff = abs(center - down2);
    vec4 l2Diff = abs(center - left2);
    vec4 r2Diff = abs(center - right2);

    vec4 sum = uDiff + dDiff + lDiff + rDiff + u2Diff + d2Diff + l2Diff + r2Diff;
    vec4 gray = vec4(0.299, 0.587, 0.114, 0.0);
    float sumLuma = 1.0 - dot(clamp(sum * 1.5, 0.0, 1.0), gray);

    // Get luminance of center pixel with contrast ramp
    float centerLuma = dot(center + (center - pow(center, vec4(LumaRamp))), gray);

    // Quantize the luma value into distinct manga ink levels
    float levels = max(2.0, LumaLevel);
    centerLuma = centerLuma - fract(centerLuma * levels) / levels;

    // Re-scale to full range
    centerLuma = centerLuma * (levels / (levels - 1.0));

    // Combine with edge outlines
    centerLuma = clamp(centerLuma * sumLuma, 0.0, 1.0);

    // Remap the monochrome sketch into the two designated colors
    vec3 finalRgb = mix(ColorDark.rgb, ColorLight.rgb, centerLuma);

    fragColor = vec4(finalRgb, 1.0);
}
