package io.github.iammxrn.strangershaders.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.iammxrn.strangershaders.card.LocalProductBack
import io.github.iammxrn.strangershaders.perf.PerfHud
import io.github.iammxrn.strangershaders.teaching.LocalCaptureOptions

/**
 * Wraps a demo so that only [content] is shaded. Play/pause, extra controls and the perf HUD stay
 * outside the shader layer, which keeps the demo drivable while the screen is glitching on stage.
 * Back belongs to the product card's top bar, which reads [onBack] through [LocalProductBack].
 */
@Composable
fun DemoScaffold(
  title: String,
  modifier: Modifier = Modifier,
  player: ExoPlayer? = null,
  onBack: () -> Unit = {},
  extraControls: (@Composable RowScope.() -> Unit)? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(modifier = modifier.fillMaxSize()) {
    CompositionLocalProvider(LocalProductBack provides onBack) {
      content()
    }

    if (!LocalCaptureOptions.current.enabled) Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .statusBarsPadding()
            // Below the card's top bar, nutrition row and pill.
            .padding(start = 12.dp, top = CardHeaderHeight),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (player != null || extraControls != null) Row(
          modifier = Modifier
              .background(ControlScrim, RoundedCornerShape(12.dp))
              .padding(horizontal = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        if (player != null) {
          PlayPauseButton(player = player)
        }
        extraControls?.invoke(this)
      }

      Text(
          text = title,
          color = Color.White,
          fontSize = ControlSize,
          modifier = Modifier
              .background(ControlScrim, RoundedCornerShape(8.dp))
              .padding(horizontal = 8.dp, vertical = 4.dp),
      )

      PerfHud()
    }
  }
}

@Composable
private fun PlayPauseButton(
  player: ExoPlayer,
  modifier: Modifier = Modifier,
) {
  var isPlaying by remember { mutableStateOf(player.isPlaying) }

  DisposableEffect(player) {
    val listener = object : Player.Listener {
      override fun onIsPlayingChanged(playing: Boolean) {
        isPlaying = playing
      }
    }
    player.addListener(listener)
    onDispose { player.removeListener(listener) }
  }

  TextButton(
      onClick = { if (isPlaying) player.pause() else player.play() },
      modifier = modifier,
  ) {
    Text(
        text = if (isPlaying) "❚❚ Pause" else "▶ Play",
        color = Color.White,
        fontSize = ControlSize,
    )
  }
}

private val ControlScrim = Color.Black.copy(alpha = 0.5f)
private val CardHeaderHeight = 174.dp
private val ControlSize = 13.sp
