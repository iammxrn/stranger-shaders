#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Exposure and white balance run in linear light, contrast and saturation in
// display gamma — the same operation order the talk explains on slides.
static inline half3 gradedColor(half3 color,
                                float exposure,
                                float temperature,
                                float contrast,
                                float saturation) {
    float3 linear = pow(float3(color), 2.2);
    linear *= exp2(exposure);
    linear *= float3(1.0 + 0.25 * temperature,
                     1.0 + 0.05 * temperature,
                     1.0 - 0.25 * temperature);
    float3 c = pow(max(linear, 0.0), 1.0 / 2.2);
    c = (c - 0.5) * contrast + 0.5;
    float luma = dot(c, float3(0.2126, 0.7152, 0.0722));
    c = mix(float3(luma), c, saturation);
    return half3(saturate(c));
}

// MARK: - Upside Down (lavender monochrome grade + white snow + glitch)
//
// The grade and glitch helpers are shared by the SwiftUI and Metal video
// paths. Snow is NOT part of either path: it is drawn exactly once for the
// whole screen by the snowFragment pass of the Metal overlay, instead of
// being recomputed inside every graded layer.

static inline float2 hash2(float2 p) {
    float3 p3 = fract(float3(p.xyx) * float3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract(float2((p3.x + p3.y) * p3.z, (p3.y + p3.z) * p3.x));
}

// One layer of drifting flakes on a jittered grid; velocity.y > 0 falls down
// because both SwiftUI positions and our video UVs grow downwards.
static inline float snowLayer(float2 p, float time, float scale, float2 velocity) {
    float2 q = p * scale - velocity * time;
    float2 cell = floor(q);
    float2 f = q - cell;
    float2 rnd = hash2(cell);
    float2 center = float2(0.2) + 0.6 * rnd;
    center.x += 0.08 * sin(time * (0.6 + rnd.y) + rnd.x * 6.2831);
    float radius = 0.05 + 0.1 * rnd.y;
    float d = length(f - center);
    float core = smoothstep(radius, radius * 0.2, d);
    float halo = smoothstep(radius * 2.4, 0.0, d) * 0.25;
    return (core + halo) * (0.35 + 0.65 * rnd.x);
}

// Returns (horizontal slice shift, rgb split distance) in unit-x space.
// Random per horizontal band, re-rolled ~16 times a second, so the image
// "tears" in steps rather than sliding smoothly.
static inline float2 glitchShift(float2 unit, float time, float amount) {
    float tQ = floor(time * 16.0);
    float bandCount = 12.0 + 12.0 * fract(tQ * 0.37);
    float band = floor(unit.y * bandCount);
    float2 rnd = hash2(float2(band + 31.7, tQ));
    float active = step(1.0 - 0.65 * amount, rnd.x);
    float shift = (rnd.y - 0.5) * 0.2 * amount * active;
    float split = 0.008 * amount * (0.4 + 0.6 * rnd.x);
    return float2(shift, split);
}

static inline float3 upsideDownGradeRGB(float3 c, float2 unit, float amount) {
    float luma = dot(c, float3(0.2126, 0.7152, 0.0722));
    // Monochrome with a cold lavender cast in shadows, clean airy whites.
    float3 tint = mix(float3(0.80, 0.83, 1.02), float3(1.02, 1.02, 1.05), smoothstep(0.45, 0.95, luma));
    float3 graded = float3(luma) * tint;
    graded += smoothstep(0.55, 1.0, luma) * 0.12;
    graded = (graded - 0.5) * 1.08 + 0.5;

    float vignette = 0.82 + 0.18 * smoothstep(0.95, 0.35, distance(unit, float2(0.5)));
    graded *= vignette;

    return mix(c, saturate(graded), amount);
}

// MARK: - SwiftUI colorEffect path

[[ stitchable ]] half4 grade(float2 position,
                             half4 color,
                             float exposure,
                             float temperature,
                             float contrast,
                             float saturation) {
    if (color.a <= 0.0h) {
        return color;
    }
    // colorEffect hands over premultiplied sRGB.
    half3 rgb = color.rgb / color.a;
    rgb = gradedColor(rgb, exposure, temperature, contrast, saturation);
    return half4(rgb * color.a, color.a);
}

// MARK: - SwiftUI upside-down path
//
// Two cheap single-purpose effects instead of one fullscreen layerEffect per
// layer: the grade is a time-independent colorEffect, and the glitch is the
// only layerEffect and is enabled only while the toggle transition runs. The
// snow is a separate Metal pass below.

[[ stitchable ]] half4 upsideDownGrade(float2 position,
                                       half4 color,
                                       float2 size,
                                       float amount) {
    if (color.a <= 0.0h || amount <= 0.0) {
        return color;
    }
    // colorEffect hands over premultiplied sRGB.
    float2 unit = position / max(size, float2(1.0));
    float3 rgb = float3(color.rgb) / float(color.a);
    float3 graded = upsideDownGradeRGB(rgb, unit, amount);
    return half4(half3(graded) * color.a, color.a);
}

// MARK: - Metal overlay snow path
//
// A fullscreen quad in the Upside Down overlay layer, above the vines. The
// fragment position is the pixel center in drawable pixels, which is the
// same fraction of the screen the colorEffect version saw in points, so the
// flakes land on the same pixels. The output is premultiplied white with
// flake coverage as alpha, which composites the same as the old per-layer
// mix(graded, white, flakes * 0.9).

struct SnowUniformsMSL {
    float2 size;
    float time;
};

vertex float4 overlayQuadVertex(uint vertexID [[vertex_id]]) {
    const float2 positions[4] = { {-1.0, -1.0}, {1.0, -1.0}, {-1.0, 1.0}, {1.0, 1.0} };
    return float4(positions[vertexID], 0.0, 1.0);
}

fragment half4 snowFragment(float4 position [[position]],
                            constant SnowUniformsMSL &uniforms [[buffer(0)]]) {
    float2 size = uniforms.size;
    float2 unit = position.xy / max(size, float2(1.0));
    float aspect = size.x / max(size.y, 1.0);
    float2 p = float2(unit.x * aspect, unit.y);
    float flakes = 0.8 * snowLayer(p, uniforms.time, 8.0, float2(0.05, 0.22));
    flakes += 0.9 * snowLayer(p, uniforms.time + 40.0, 14.0, float2(0.03, 0.1));
    flakes += 0.7 * snowLayer(p, uniforms.time + 80.0, 22.0, float2(-0.04, 0.16));
    half a = half(clamp(flakes, 0.0, 1.0) * 0.9);
    return half4(a, a, a, a);
}

[[ stitchable ]] half4 glitchLayer(float2 position,
                                   SwiftUI::Layer layer,
                                   float2 size,
                                   float time,
                                   float amount) {
    float2 unit = position / max(size, float2(1.0));
    float2 g = glitchShift(unit, time, amount);
    float2 samplePosition = position + float2(g.x * size.x, 0.0);
    float split = g.y * size.x;
    half4 red = layer.sample(samplePosition + float2(split, 0.0));
    half4 green = layer.sample(samplePosition);
    half4 blue = layer.sample(samplePosition - float2(split, 0.0));
    return half4(red.r, green.g, blue.b, green.a);
}

// MARK: - Metal video path

struct GradingUniformsMSL {
    float exposure;
    float temperature;
    float contrast;
    float saturation;
    float lutAmount;
    float upsideDownAmount;
    float glitchAmount;
    float time;
};

struct QuadVertexOut {
    float4 position [[position]];
    float2 uv;
};

vertex QuadVertexOut videoQuadVertex(uint vertexID [[vertex_id]],
                                     constant float2 &fitScale [[buffer(0)]]) {
    const float2 positions[4] = { {-1.0, -1.0}, {1.0, -1.0}, {-1.0, 1.0}, {1.0, 1.0} };
    const float2 uvs[4] = { {0.0, 1.0}, {1.0, 1.0}, {0.0, 0.0}, {1.0, 0.0} };
    QuadVertexOut out;
    out.position = float4(positions[vertexID] * fitScale, 0.0, 1.0);
    out.uv = uvs[vertexID];
    return out;
}

fragment half4 videoGradeFragment(QuadVertexOut in [[stage_in]],
                                  texture2d<half> videoTexture [[texture(0)]],
                                  texture3d<half> lutTexture [[texture(1)]],
                                  constant GradingUniformsMSL &uniforms [[buffer(0)]]) {
    constexpr sampler linearSampler(address::clamp_to_edge, filter::linear);
    half3 c;
    if (uniforms.glitchAmount > 0.0) {
        float2 g = glitchShift(in.uv, uniforms.time, uniforms.glitchAmount);
        float2 uv = in.uv + float2(g.x, 0.0);
        c.r = videoTexture.sample(linearSampler, uv + float2(g.y, 0.0)).r;
        c.g = videoTexture.sample(linearSampler, uv).g;
        c.b = videoTexture.sample(linearSampler, uv - float2(g.y, 0.0)).b;
    } else {
        c = videoTexture.sample(linearSampler, in.uv).rgb;
    }
    c = gradedColor(c, uniforms.exposure, uniforms.temperature, uniforms.contrast, uniforms.saturation);
    // Sample at texel centers: raw coordinates clamp the outer half-texel of
    // the cube, crushing shadows and blowing highlights.
    float lutSize = float(lutTexture.get_width());
    float3 lutCoord = (float3(c) * (lutSize - 1.0) + 0.5) / lutSize;
    half3 lutted = lutTexture.sample(linearSampler, lutCoord).rgb;
    c = mix(c, lutted, half(uniforms.lutAmount));
    if (uniforms.upsideDownAmount > 0.0) {
        c = half3(upsideDownGradeRGB(float3(c), in.uv, uniforms.upsideDownAmount));
    }
    return half4(c, 1.0h);
}
