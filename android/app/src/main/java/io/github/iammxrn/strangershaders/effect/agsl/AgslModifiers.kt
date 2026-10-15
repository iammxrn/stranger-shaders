package io.github.iammxrn.strangershaders.effect.agsl

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import io.github.iammxrn.strangershaders.bench.UiBench
import io.github.iammxrn.strangershaders.effect.ParticleCount
import io.github.iammxrn.strangershaders.effect.UpsideDownEffectState
import io.github.iammxrn.strangershaders.effect.strangerVines

/** AGSL needs RuntimeShader, which arrived in Tiramisu. */
val isAgslSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * The whole effect as a single modifier: glitch, then upside-down grade plus vines.
 *
 * Unlike the production version this reads its parameters from [state] instead of running its own
 * animation, so the same timeline can drive a video renderer in parallel.
 *
 * @param particleCount 0 when another renderer already draws the particles.
 * @param drawVines false when the vines are drawn by a separate, full-screen layer. Scoping the
 * shader to a small area is only a win if the vines are not dragged into that same small area.
 */
@Composable
fun Modifier.upsideDownAgsl(
  state: UpsideDownEffectState,
  particleCount: Int = ParticleCount,
  drawVines: Boolean = true,
): Modifier {
  if (!isAgslSupported) return this

  val snapshot = state.snapshot
  var modifier = this

  if (snapshot.glitchIntensity > 0f) {
    modifier = modifier.agslGlitch(state)
  }
  if (snapshot.upsideDownAmount > 0f) {
    modifier = modifier.agslUpsideDown(state, particleCount)
    if (drawVines) {
      modifier = modifier.strangerVines()
    }
  }

  return modifier
}

/**
 * Canvas vines only, no shader.
 *
 * Since 2026-09-26 the routes draw mesh vines through
 * [io.github.iammxrn.strangershaders.effect.mesh.upsideDownRouteVines], which falls back to this below
 * Android 14. Drawn on the root, the vines span the whole screen while a split route's shader pass
 * stays scoped to the pixels the View hierarchy actually owns.
 *
 * These vines are therefore never graded, so they are drawn in the colour the grade would have
 * produced. Feeding the default vine colour through the shader's hue shift, desaturation and
 * contrast lands on near-black; using the ungraded purple here makes demo 4 and 5 visibly differ
 * from demo 3.
 */
@Composable
fun Modifier.upsideDownVines(state: UpsideDownEffectState): Modifier {
  return if (state.snapshot.upsideDownAmount > 0f) {
    strangerVines(baseColor = GradedVineColor)
  } else {
    this
  }
}

private val GradedVineColor = Color(0xFF03060A)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Stable
@Composable
fun Modifier.agslGlitch(
  state: UpsideDownEffectState,
  slices: Int = DefaultSlices,
): Modifier {
  val shader = remember { RuntimeShader(GLITCH_SHADER) }

  return clipToBounds().graphicsLayer {
    val snapshot = state.snapshot
    if (UiBench.enabled) UiBench.agslGlitchLayers.incrementAndGet()
    shader.setIntUniform("slices", slices)
    shader.setFloatUniform("imageSize", size.width, size.height)
    shader.setFloatUniform("intensity", snapshot.glitchIntensity)
    shader.setFloatUniform("realRandom", snapshot.realRandom)

    renderEffect = shader.asRenderEffect()
  }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Stable
@Composable
fun Modifier.agslUpsideDown(
  state: UpsideDownEffectState,
  particleCount: Int = ParticleCount,
): Modifier {
  val shader = remember(particleCount) { RuntimeShader(upsideDownShader(particleCount)) }

  return clipToBounds().graphicsLayer {
    val snapshot = state.snapshot
    if (UiBench.enabled) UiBench.agslGradeLayers.incrementAndGet()
    shader.setFloatUniform("imageSize", size.width, size.height)
    shader.setFloatUniform("time", snapshot.timeSeconds)
    shader.setFloatUniform("darknessIntensity", snapshot.darknessIntensity)

    renderEffect = shader.asRenderEffect()
  }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun RuntimeShader.asRenderEffect(): RenderEffect {
  return android.graphics.RenderEffect
      .createRuntimeShaderEffect(this, "image")
      .asComposeRenderEffect()
}

private const val DefaultSlices = 70
