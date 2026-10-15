package io.github.iammxrn.strangershaders.effect.snow

import android.graphics.RuntimeShader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ShaderBrush
import io.github.iammxrn.strangershaders.bench.UiBench
import io.github.iammxrn.strangershaders.effect.UpsideDownEffectState
import io.github.iammxrn.strangershaders.effect.agsl.isAgslSupported

/**
 * Upside Down snow as one full-screen pass above everything this modifier wraps, like the snow pass
 * of the iOS Metal overlay. Put it outermost on a screen root, so the flakes sit above the graded
 * card and the vines, are not glitched, and are drawn exactly once per frame.
 *
 * It is a plain draw, not a RenderEffect, so it also covers a SurfaceView hole: the snow shows over
 * video on every route, whichever renderer grades that video.
 *
 * Time comes from the shared effect snapshot, so a fixed capture freezes the flakes as well.
 */
@Composable
fun Modifier.upsideDownSnow(state: UpsideDownEffectState): Modifier {
  if (!isAgslSupported) return this
  val shader = remember { RuntimeShader(SNOW_SHADER) }
  val brush = remember(shader) { ShaderBrush(shader) }
  if (state.snapshot.upsideDownAmount <= 0f) return this

  return drawWithContent {
    drawContent()
    if (UiBench.enabled) UiBench.agslSnowDraws.incrementAndGet()
    shader.setFloatUniform("size", size.width, size.height)
    shader.setFloatUniform("time", state.snapshot.timeSeconds)
    drawRect(brush)
  }
}
