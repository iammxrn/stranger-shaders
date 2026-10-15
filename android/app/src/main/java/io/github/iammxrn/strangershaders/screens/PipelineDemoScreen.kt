package io.github.iammxrn.strangershaders.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import io.github.iammxrn.strangershaders.card.DemoCard
import io.github.iammxrn.strangershaders.effect.agsl.upsideDownAgsl
import io.github.iammxrn.strangershaders.effect.mesh.upsideDownRouteVines
import io.github.iammxrn.strangershaders.effect.snow.upsideDownSnow
import io.github.iammxrn.strangershaders.effect.gl.UpsideDownGlEffect
import io.github.iammxrn.strangershaders.effect.rememberUpsideDownEffectState
import io.github.iammxrn.strangershaders.player.SurfaceViewVideo
import io.github.iammxrn.strangershaders.perf.VideoPipelineStats
import kotlinx.collections.immutable.persistentListOf
import io.github.iammxrn.strangershaders.player.rememberDemoPlayer

/**
 * Demo 4. Same SurfaceView as the broken demo, but the video now carries its own copy of the effect
 * inside the media3 pipeline. Two renderers, one set of parameters: AGSL grades the UI, GLSL grades
 * the decoder output, and both read the same [rememberUpsideDownEffectState].
 */
@OptIn(UnstableApi::class)
@Composable
fun PipelineDemoScreen(
  modifier: Modifier = Modifier,
  startActive: Boolean = false,
  onBack: () -> Unit = {},
) {
  val effect = rememberUpsideDownEffectState(initiallyActive = startActive)
  val videoEffect = remember(effect) { UpsideDownGlEffect(effect) }
  val player = rememberDemoPlayer(effects = persistentListOf(videoEffect))

  DisposableEffect(Unit) {
    VideoPipelineStats.reset()
    onDispose {}
  }

  DemoScaffold(
      title = Demo.SurfaceViewPipeline.title,
      modifier = modifier,
      player = player,
      onBack = onBack,
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
            SurfaceViewVideo(player = player, modifier = Modifier.matchParentSize())
          },
      )
    }
  }
}
