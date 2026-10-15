package io.github.iammxrn.strangershaders.effect.mesh

import org.intellij.lang.annotations.Language

/**
 * Vertex layout of one vine strip, matching [VINES_VERTEX_SHADER]'s attributes.
 *
 * An AGSL mesh vertex shader reads its own attributes and uniforms only, never a buffer, so where the
 * Metal `vinesVertex` looks up `line[i - 1]` and `line[i + 1]` each vertex carries its neighbours.
 *
 * Per vertex: position (2), prev (2), next (2), meta (index, side).
 */
const val VineVertexFloats = 8
const val VineVertexStrideBytes = VineVertexFloats * Float.SIZE_BYTES

/** Two strip vertices per centerline point: side +1 first, then side -1. */
fun packVineVertices(line: VineCenterline): FloatArray {
  val out = FloatArray(line.count * 2 * VineVertexFloats)
  var o = 0
  for (i in 0 until line.count) {
    val prev = maxOf(i - 1, 0)
    val next = minOf(i + 1, line.count - 1)
    for (side in floatArrayOf(1f, -1f)) {
      out[o++] = line.x(i)
      out[o++] = line.y(i)
      out[o++] = line.x(prev)
      out[o++] = line.y(prev)
      out[o++] = line.x(next)
      out[o++] = line.y(next)
      out[o++] = i.toFloat()
      out[o++] = side
    }
  }
  return out
}

/**
 * Port of `vinesVertex` in ios/.../UpsideDown/Vines.metal to an AGSL mesh vertex shader.
 *
 * Every frame the shader decides how many points of the pre-walked centerline are visible from
 * `progress`, offsets the strip vertex along its normal by the tapered half width, and collapses the
 * unused tail. Metal collapses the tail onto the tip by reading `line[validPoints - 1]`; without a
 * buffer each hidden vertex moves onto its own previous point with zero width instead, which makes
 * every triangle past the tip zero-area as well.
 */
@Language("AGSL")
val VINES_VERTEX_SHADER = """
    uniform float2 screenSize;
    uniform float progress;
    uniform float mainWidth;
    uniform float pointCount;

    Varyings main(const Attributes attributes) {
        Varyings varyings;

        // The number of points the truncated walk would have produced: the start point plus
        // every step that began before targetLen. Same arithmetic as the Metal shader.
        float maxLen = length(screenSize) * 0.53;
        float targetLen = maxLen * progress;
        float steps = targetLen / 4.0;
        float grown = steps > 0.0 ? ceil(steps) : 0.0;
        float validPoints = min(pointCount, 1.0 + grown);

        float i = attributes.meta.x;
        float side = attributes.meta.y;
        float2 p = attributes.position;
        float2 d = float2(1.0, 0.0);
        float halfWidth = 0.0;

        if (i >= validPoints) {
            // Hidden tail: onto the previous point, zero width.
            p = attributes.prev;
        } else {
            // Normal from the neighbouring points: forward at the root, backward at the tip,
            // averaged in between.
            if (i == 0.0 && validPoints > 1.0) {
                d = attributes.next - p;
            } else if (i == validPoints - 1.0 && i > 0.0) {
                d = p - attributes.prev;
            } else if (i > 0.0 && i < validPoints - 1.0) {
                d = attributes.next - attributes.prev;
            }
            float t = i / max(1.0, validPoints - 1.0);
            halfWidth = mix(mainWidth, 6.0, t) * 0.5;
        }

        float2 nrm = length(d) > 0.001 ? float2(-d.y, d.x) / length(d) : float2(0.0, 1.0);
        varyings.position = p + nrm * halfWidth * side;
        return varyings;
    }
"""

/** Port of `vinesFragment`: flat black at 0.92, premultiplied (black is its own premultiply). */
@Language("AGSL")
val VINES_FRAGMENT_SHADER = """
    float2 main(const Varyings varyings, out float4 color) {
        color = float4(0.0, 0.0, 0.0, 0.92);
        return varyings.position;
    }
"""
