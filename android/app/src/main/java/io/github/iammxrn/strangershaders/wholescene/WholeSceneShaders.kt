package io.github.iammxrn.strangershaders.wholescene

import org.intellij.lang.annotations.Language

/*
 * GLSL for the whole-scene route. Grade and glitch are not here: that pass runs the unchanged
 * UPSIDE_DOWN_FRAGMENT_SHADER_2D of the Media3 route over the composited scene texture. This file
 * holds only what the other routes do outside GLSL: putting UI over video, and the snow and vine
 * overlays that the reference draws with AGSL after the grade.
 */

/**
 * Video under UI, before any effect: the composition the TextureView route hands to its AGSL layer.
 *
 * Video sampling repeats the own-renderer route: SurfaceTexture transform matrix plus the centre
 * crop that RESIZE_MODE_ZOOM or Modifier.centerCropVideo would apply. The UI buffer has the size of
 * the screen and is sampled one to one. HWUI renders it premultiplied, so "over" is ui + video * (1 -
 * ui.a). Before the first buffer of a stream arrives its texture is undefined and is not sampled.
 */
@Language("GLSL")
internal val COMPOSITE_FRAGMENT_SHADER =
  """
    #version 100
    #extension GL_OES_EGL_image_external : require
    precision highp float;

    uniform samplerExternalOES uVideo;
    uniform mat4 uVideoMatrix;
    uniform vec2 uCropScale;
    uniform float uHasVideo;

    uniform samplerExternalOES uUi;
    uniform mat4 uUiMatrix;
    uniform float uHasUi;

    varying vec2 vTexSamplingCoord;

    void main() {
      vec4 video = vec4(0.0, 0.0, 0.0, 1.0);
      if (uHasVideo > 0.5) {
        vec2 tex = (vTexSamplingCoord - 0.5) * uCropScale + 0.5;
        video = vec4(texture2D(uVideo, (uVideoMatrix * vec4(tex, 0.0, 1.0)).xy).rgb, 1.0);
      }
      vec4 ui = vec4(0.0);
      if (uHasUi > 0.5) {
        ui = texture2D(uUi, (uUiMatrix * vec4(vTexSamplingCoord, 0.0, 1.0)).xy);
      }
      gl_FragColor = ui + video * (1.0 - ui.a);
    }
"""

/**
 * GLSL ES port of SNOW_SHADER (effect/snow/SnowShader.kt): same hash, three layers, scales,
 * velocities, time offsets and 0.9 coverage, drawn once above everything with premultiplied
 * source-over blending.
 *
 * Two mechanical differences. AGSL hands main() a top-down pixel centre; GL counts rows from the
 * bottom, so the coordinate is flipped back. SkSL defines smoothstep for edge0 > edge1, which the
 * snow uses for its falloff, while GLSL ES 1.00 leaves that case undefined, so the SkSL formula is
 * written out as smoothstepSksl.
 */
@Language("GLSL")
internal val SNOW_FRAGMENT_SHADER =
  """
    #version 100
    precision highp float;

    uniform vec2 size;
    uniform float time;

    float smoothstepSksl(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    vec2 hash2(vec2 p) {
        vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
        p3 += dot(p3, p3.yzx + 33.33);
        return fract(vec2((p3.x + p3.y) * p3.z, (p3.y + p3.z) * p3.x));
    }

    // One layer of drifting flakes on a jittered grid; velocity.y > 0 falls down.
    float snowLayer(vec2 p, float t, float scale, vec2 velocity) {
        vec2 q = p * scale - velocity * t;
        vec2 cell = floor(q);
        vec2 f = q - cell;
        vec2 rnd = hash2(cell);
        vec2 center = vec2(0.2) + 0.6 * rnd;
        center.x += 0.08 * sin(t * (0.6 + rnd.y) + rnd.x * 6.2831);
        float radius = 0.05 + 0.1 * rnd.y;
        float d = length(f - center);
        float core = smoothstepSksl(radius, radius * 0.2, d);
        float halo = smoothstepSksl(radius * 2.4, 0.0, d) * 0.25;
        return (core + halo) * (0.35 + 0.65 * rnd.x);
    }

    void main() {
        vec2 fragCoord = vec2(gl_FragCoord.x, size.y - gl_FragCoord.y);
        vec2 unit = fragCoord / max(size, vec2(1.0));
        float aspect = size.x / max(size.y, 1.0);
        vec2 p = vec2(unit.x * aspect, unit.y);
        float flakes = 0.8 * snowLayer(p, time, 8.0, vec2(0.05, 0.22));
        flakes += 0.9 * snowLayer(p, time + 40.0, 14.0, vec2(0.03, 0.1));
        flakes += 0.7 * snowLayer(p, time + 80.0, 22.0, vec2(-0.04, 0.16));
        float a = clamp(flakes, 0.0, 1.0) * 0.9;
        gl_FragColor = vec4(a, a, a, a);
    }
"""

/**
 * GLSL ES port of VINES_VERTEX_SHADER (effect/mesh/MeshVinesShaders.kt). The vertex buffer is the
 * same packVineVertices() layout of the same CPU-walked centerline; an attribute per field replaces
 * the mesh `Attributes` struct. The body is unchanged. The last line maps the top-down pixel
 * position that Canvas.drawMesh would consume to clip space.
 */
@Language("GLSL")
internal val VINES_GL_VERTEX_SHADER =
  """
    #version 100
    precision highp float;

    attribute vec2 position;
    attribute vec2 prev;
    attribute vec2 next;
    attribute vec2 meta;

    uniform vec2 screenSize;
    uniform float progress;
    uniform float mainWidth;
    uniform float pointCount;

    void main() {
        // The number of points the truncated walk would have produced: the start point plus
        // every step that began before targetLen. Same arithmetic as the Metal shader.
        float maxLen = length(screenSize) * 0.53;
        float targetLen = maxLen * progress;
        float steps = targetLen / 4.0;
        float grown = steps > 0.0 ? ceil(steps) : 0.0;
        float validPoints = min(pointCount, 1.0 + grown);

        float i = meta.x;
        float side = meta.y;
        vec2 p = position;
        vec2 d = vec2(1.0, 0.0);
        float halfWidth = 0.0;

        if (i >= validPoints) {
            // Hidden tail: onto the previous point, zero width.
            p = prev;
        } else {
            // Normal from the neighbouring points: forward at the root, backward at the tip,
            // averaged in between.
            if (i == 0.0 && validPoints > 1.0) {
                d = next - p;
            } else if (i == validPoints - 1.0 && i > 0.0) {
                d = p - prev;
            } else if (i > 0.0 && i < validPoints - 1.0) {
                d = next - prev;
            }
            float t = i / max(1.0, validPoints - 1.0);
            halfWidth = mix(mainWidth, 6.0, t) * 0.5;
        }

        vec2 nrm = length(d) > 0.001 ? vec2(-d.y, d.x) / length(d) : vec2(0.0, 1.0);
        vec2 pixel = p + nrm * halfWidth * side;
        gl_Position = vec4(pixel.x / screenSize.x * 2.0 - 1.0, 1.0 - pixel.y / screenSize.y * 2.0, 0.0, 1.0);
    }
"""

/** Port of VINES_FRAGMENT_SHADER: flat black at 0.92, already premultiplied. */
@Language("GLSL")
internal val VINES_GL_FRAGMENT_SHADER =
  """
    #version 100
    precision mediump float;

    void main() {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 0.92);
    }
"""
