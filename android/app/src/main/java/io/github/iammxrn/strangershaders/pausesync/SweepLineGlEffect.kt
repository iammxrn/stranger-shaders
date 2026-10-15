package io.github.iammxrn.strangershaders.pausesync

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import org.intellij.lang.annotations.Language

/**
 * Draws the sweeping line into every video frame inside Media3's frame processor.
 *
 * [drawFrame][SweepLineProgram.drawFrame] runs only when Media3 processes a frame. While the clip
 * plays that is every video frame; on pause it stops, and the line on the video stops with it,
 * unless something asks Media3 to process the last frame again.
 */
@UnstableApi
internal class SweepLineGlEffect(private val clock: SweepClock) : GlEffect {
  override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
      SweepLineProgram(clock, useHdr)
}

@UnstableApi
private class SweepLineProgram(
  private val clock: SweepClock,
  useHdr: Boolean,
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

  private val program = try {
    GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
  } catch (e: GlUtil.GlException) {
    throw VideoFrameProcessingException(e)
  }

  private var width = 1

  override fun configure(inputWidth: Int, inputHeight: Int): Size {
    width = inputWidth
    return Size(inputWidth, inputHeight)
  }

  override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
    try {
      program.use()
      program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
      // The shared clock, not the clip's presentation time: the line keeps its own pace.
      program.setFloatUniform("uLineX", clock.videoLineX)
      program.setFloatUniform("uHalfWidth", LineHalfWidthPx / width)
      program.setBufferAttribute(
          "aFramePosition",
          GlUtil.getNormalizedCoordinateBounds(),
          GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
      )
      program.bindAttributesAndUniforms()
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
      GlUtil.checkGlError()
    } catch (e: GlUtil.GlException) {
      throw VideoFrameProcessingException(e, presentationTimeUs)
    }
  }

  override fun release() {
    super.release()
    try {
      program.delete()
    } catch (e: GlUtil.GlException) {
      throw VideoFrameProcessingException(e)
    }
  }
}

@Language("GLSL")
private const val VERTEX_SHADER = """
attribute vec4 aFramePosition;
varying vec2 vUv;
void main() {
  gl_Position = aFramePosition;
  vUv = aFramePosition.xy * 0.5 + 0.5;
}
"""

@Language("GLSL")
private const val FRAGMENT_SHADER = """
precision mediump float;
uniform sampler2D uTexSampler;
uniform float uLineX;
uniform float uHalfWidth;
varying vec2 vUv;
void main() {
  vec4 color = texture2D(uTexSampler, vUv);
  float line = 1.0 - step(uHalfWidth, abs(vUv.x - uLineX));
  gl_FragColor = vec4(mix(color.rgb, vec3(1.0, 0.1, 0.55), line), 1.0);
}
"""

/** In video pixels; the clip is 1086 wide, so this is close to the UI line's width on a 1080 screen. */
internal const val LineHalfWidthPx = 9f
