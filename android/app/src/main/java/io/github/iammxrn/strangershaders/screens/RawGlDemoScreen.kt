package io.github.iammxrn.strangershaders.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import io.github.iammxrn.strangershaders.card.DemoCard
import io.github.iammxrn.strangershaders.effect.agsl.upsideDownAgsl
import io.github.iammxrn.strangershaders.effect.mesh.upsideDownRouteVines
import io.github.iammxrn.strangershaders.effect.snow.upsideDownSnow
import io.github.iammxrn.strangershaders.effect.rememberUpsideDownEffectState
import io.github.iammxrn.strangershaders.perf.VideoPipelineStats
import io.github.iammxrn.strangershaders.player.GlVideoSurface
import io.github.iammxrn.strangershaders.player.rememberDemoPlayer

/**
 * Demo 5. Same shader as demo 4, but we own the render loop instead of media3. The player decodes
 * into our SurfaceTexture and never learns where the pixels end up.
 *
 * Compare the two with scripts/benchmark.sh: demo 4's video layer presents at the clip's frame rate
 * and stops on pause; in continuous mode this one presents at display rate and keeps going.
 */
@Composable
fun RawGlDemoScreen(
  modifier: Modifier = Modifier,
  startActive: Boolean = false,
  renderContinuously: Boolean = true,
  onBack: () -> Unit = {},
) {
  val effect = rememberUpsideDownEffectState(initiallyActive = startActive)
  // Switchable at runtime so the trade-off can be shown live: vsync keeps the effect alive over a
  // paused video and costs the most; on-demand matches what setVideoEffects does.
  var continuous by rememberSaveable(renderContinuously) { mutableStateOf(renderContinuously) }
  val player = rememberDemoPlayer()

  DisposableEffect(Unit) {
    VideoPipelineStats.reset()
    onDispose {}
  }

  DemoScaffold(
      title = Demo.SurfaceViewRawGl.title + if (continuous) " · continuous" else " · on-demand",
      modifier = modifier,
      player = player,
      onBack = onBack,
      extraControls = {
        TextButton(onClick = { continuous = !continuous }) {
          Text(
              text = if (continuous) "continuous" else "on-demand",
              color = Color.White,
              fontSize = 13.sp,
          )
        }
      },
  ) {
    Box(modifier = Modifier.fillMaxSize().upsideDownSnow(effect).upsideDownRouteVines(effect)) {
      DemoCard(
          chromeModifier = Modifier.upsideDownAgsl(
              state = effect,
              particleCount = 0,
              drawVines = false,
          ),
          isUpsideDownActive = effect.isActive,
          onUpsideDownClick = effect::toggle,
          background = {
            GlVideoSurface(
                player = player,
                state = effect,
                modifier = Modifier.matchParentSize(),
                renderContinuously = continuous,
            )
          },
      )
    }
  }
}
