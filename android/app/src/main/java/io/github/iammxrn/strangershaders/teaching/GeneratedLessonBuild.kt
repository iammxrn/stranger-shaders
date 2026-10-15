package io.github.iammxrn.strangershaders.teaching

/** Actual source override for this build/capture revision. */
internal object GeneratedLessonBuild {
    const val id = "glitch-displaced"
    const val source = """uniform shader image;
uniform float2 imageSize;
uniform float time;

half4 main(float2 coord) {
    float stripe = floor(coord.y / (imageSize.y / 12.0));
    float shift = (mod(stripe, 2.0) * 2.0 - 1.0) * imageSize.x * 0.18;
    float2 displaced = coord + float2(shift, 0.0);
    half4 c = image.eval(displaced);
    half4 offset = image.eval(displaced + float2(imageSize.x * 0.035, 0.0));
    float red = offset.r / max(offset.a, 0.001) * c.a;
    return half4(red, c.g, c.b, c.a);
}
"""
}
