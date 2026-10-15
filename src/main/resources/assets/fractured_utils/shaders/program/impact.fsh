#version 150

uniform sampler2D DiffuseSampler;
in vec2 texCoord;
out vec4 fragColor;

uniform float Intensity; // Fade factor, impact shake or timer
uniform float Mode;      // 0.0 = Noir Inversion, 1.0 = Chromatic Burst, 2.0 = Thermal Solarized, 3.0 = Hybrid
uniform vec4 ColorDark;  // Custom shadow/ink color (default vec4(0.0, 0.0, 0.0, 1.0))
uniform vec4 ColorLight; // Custom highlight/paper color (default vec4(1.0, 1.0, 1.0, 1.0))

void main() {
    vec4 sceneColor = texture(DiffuseSampler, texCoord);
    float k = clamp(Intensity, 0.0, 1.0);
    int m = int(Mode + 0.1);

    if (m == 1) {
        // Option 2: Chromatic Burst (Glitch High-Intensity Heavy Hit)
        // Determine screen offset based on speed/force
        vec2 center = vec2(0.5, 0.5);
        vec2 dir = texCoord - center;
        float dist = length(dir);

        // Calculate chromatic aberration offsets
        vec2 offset = dir * 0.04 * k;

        float r = texture(DiffuseSampler, texCoord - offset).r;
        float g = texture(DiffuseSampler, texCoord).g;
        float b = texture(DiffuseSampler, texCoord + offset).b;

        vec3 chromatic = vec3(r, g, b);

        // Flash and blow out whites in center
        float centerFlash = smoothstep(0.8, 0.0, dist) * k;
        chromatic += vec3(centerFlash * 0.8);

        vec3 finalColor = mix(sceneColor.rgb, chromatic, k);
        fragColor = vec4(finalColor, 1.0);
    } else if (m == 2) {
        // Option 3: Thermal / Solarized Negative Speedlines (The Sci-Fi Power Hit)
        // Invert everything
        vec3 negative = vec3(1.0) - sceneColor.rgb;

        // Boost red and blue thermal highlights
        negative.r *= 1.8;
        negative.b *= 1.5;

        // Create speed vignette (radial darkening overlay)
        vec2 uv = texCoord - 0.5;
        float vignette = dot(uv, uv) * 2.5;

        // Composite back into original
        float factor = clamp((1.0 - vignette) * k, 0.0, 1.0);
        vec3 finalColor = mix(sceneColor.rgb, negative, factor);

        fragColor = vec4(finalColor, 1.0);
    } else if (m == 3) {
        // Option 4: Hybrid (Chromatic Aberration Burst into High-Contrast Noir with Center Flash)
        vec2 center = vec2(0.5, 0.5);
        vec2 dir = texCoord - center;
        float dist = length(dir);
        vec2 offset = dir * 0.035 * k;

        float r = texture(DiffuseSampler, texCoord - offset).r;
        float g = texture(DiffuseSampler, texCoord).g;
        float b = texture(DiffuseSampler, texCoord + offset).b;
        vec3 chromatic = vec3(r, g, b);

        float gray = dot(chromatic, vec3(0.299, 0.587, 0.114));
        vec3 darkVal = (ColorDark.a > 0.01) ? ColorDark.rgb : vec3(0.0);
        vec3 lightVal = (ColorLight.a > 0.01) ? ColorLight.rgb : vec3(1.0);
        vec3 noir = gray > 0.45 ? darkVal : lightVal;

        float centerFlash = smoothstep(0.7, 0.0, dist) * k * 0.8;
        vec3 finalColor = mix(sceneColor.rgb, clamp(noir + vec3(centerFlash), 0.0, 1.0), k);

        fragColor = vec4(finalColor, 1.0);
    } else {
        // Option 1: High-Contrast Inversion (Noir Anime style)
        // Calculate luminance
        float gray = dot(sceneColor.rgb, vec3(0.299, 0.587, 0.114));

        // Create stark black and white zones based on threshold (with customizable dark/light tint)
        vec3 darkVal = (ColorDark.a > 0.01) ? ColorDark.rgb : vec3(0.0);
        vec3 lightVal = (ColorLight.a > 0.01) ? ColorLight.rgb : vec3(1.0);
        vec3 noir = gray > 0.45 ? darkVal : lightVal;

        // Mix it back with original scene based on trigger/timer
        vec3 finalColor = mix(sceneColor.rgb, noir, k);

        fragColor = vec4(finalColor, 1.0);
    }
}
