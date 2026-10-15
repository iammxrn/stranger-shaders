package io.github.iammxrn.strangershaders.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import io.github.iammxrn.strangershaders.R
import io.github.iammxrn.strangershaders.card.DemoCard
import io.github.iammxrn.strangershaders.effect.agsl.upsideDownAgsl
import io.github.iammxrn.strangershaders.effect.rememberUpsideDownEffectState
import io.github.iammxrn.strangershaders.effect.mesh.upsideDownRouteVines
import io.github.iammxrn.strangershaders.effect.snow.upsideDownSnow
import io.github.iammxrn.strangershaders.player.SurfaceViewVideo
import io.github.iammxrn.strangershaders.player.TextureViewVideo
import io.github.iammxrn.strangershaders.player.rememberDemoPlayer

/**
 * Demo 1. No video at all — a still image behind the card. The AGSL RenderEffect sits on the root
 * graphicsLayer and everything below it is a normal View-hierarchy render node, so the shader sees
 * every pixel. This is the case the "shaders are easy" half of the talk is about.
 */
@Composable
fun NoVideoAgslScreen(
  modifier: Modifier = Modifier,
  startActive: Boolean = false,
  onBack: () -> Unit = {},
) {
  val effect = rememberUpsideDownEffectState(initiallyActive = startActive)

  DemoScaffold(
      title = Demo.NoVideoAgsl.title,
      modifier = modifier,
      onBack = onBack,
  ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .upsideDownSnow(effect)
            .upsideDownRouteVines(effect)
            .upsideDownAgsl(effect, drawVines = false),
    ) {
      DemoCard(
          isUpsideDownActive = effect.isActive,
          onUpsideDownClick = effect::toggle,
          background = {
            Image(
                painter = painterResource(R.drawable.sample_cover),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
          },
      )
    }
  }
}

/**
 * Demo 2. The failure. Identical to demo 1 except the background is an ExoPlayer in a SurfaceView.
 * The card, the text and the buttons glitch; the video does not, because its frames never enter
 * the render node the RenderEffect operates on.
 */
@Composable
fun SurfaceViewAgslScreen(
  modifier: Modifier = Modifier,
  startActive: Boolean = false,
  onBack: () -> Unit = {},
) {
  val effect = rememberUpsideDownEffectState(initiallyActive = startActive)
  val player = rememberDemoPlayer()

  DemoScaffold(
      title = Demo.SurfaceViewAgsl.title,
      modifier = modifier,
      player = player,
      onBack = onBack,
  ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .upsideDownSnow(effect)
            .upsideDownRouteVines(effect)
            .upsideDownAgsl(effect, drawVines = false),
    ) {
      DemoCard(
          isUpsideDownActive = effect.isActive,
          onUpsideDownClick = effect::toggle,
          background = {
            SurfaceViewVideo(player = player, modifier = Modifier.matchParentSize())
          },
      )
    }
  }
}

/**
 * Demo 3. One word changed: TextureView instead of SurfaceView. Frames now go through a
 * SurfaceTexture that HWUI draws inside the View hierarchy, so the same unmodified AGSL shader
 * grades the video too.
 */
@Composable
fun TextureViewAgslScreen(
  modifier: Modifier = Modifier,
  startActive: Boolean = false,
  onBack: () -> Unit = {},
) {
  val effect = rememberUpsideDownEffectState(initiallyActive = startActive)
  val player = rememberDemoPlayer()

  DemoScaffold(
      title = Demo.TextureViewAgsl.title,
      modifier = modifier,
      player = player,
      onBack = onBack,
  ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .upsideDownSnow(effect)
            .upsideDownRouteVines(effect)
            .upsideDownAgsl(effect, drawVines = false),
    ) {
      DemoCard(
          isUpsideDownActive = effect.isActive,
          onUpsideDownClick = effect::toggle,
          background = {
            TextureViewVideo(player = player, modifier = Modifier.matchParentSize())
          },
      )
    }
  }
}
