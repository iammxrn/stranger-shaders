package io.github.iammxrn.strangershaders.pausesync

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoFrameProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import org.intellij.lang.annotations.Language
import io.github.iammxrn.strangershaders.player.LocalDemoClip
import io.github.iammxrn.strangershaders.teaching.captureAwareStatusBarsPadding
import io.github.iammxrn.strangershaders.teaching.LocalCaptureOptions

/** The two pause-sync screens: `--es pauseSync Problem` or `--es pauseSync Redraw`. */
enum class PauseSyncDemo(val title: String, val subtitle: String, val redrawOnPause: Boolean) {
  Problem(
      title = "Pause: Media3 effect freezes",
      subtitle = "setVideoEffects only. On pause the video line stops, the UI line keeps going.",
      redrawOnPause = false,
  ),
  Redraw(
      title = "Pause: Media3 REDRAW",
      subtitle = "Same screen, replayable cache on, REDRAW every UI frame while paused.",
      redrawOnPause = true,
  ),
  ;

  companion object {
    fun fromExtra(name: String?): PauseSyncDemo? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
  }
}

/**
 * One vertical line, drawn twice from one clock: by a Media3 GL effect into the video and by an
 * AGSL RenderEffect onto a large button above it. The clip plays for [PlayMs] and pauses for
 * [PauseMs]. On [PauseSyncDemo.Problem] the video line freezes during the pause and splits from
 * the button's line; [PauseSyncDemo.Redraw] asks Media3 to process the paused frame again on every
 * UI frame, so both lines keep moving together.
 */
@OptIn(UnstableApi::class)
@Composable
fun PauseSyncScreen(demo: PauseSyncDemo, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val clip = LocalDemoClip.current
  val clock = remember { SweepClock() }
  val player = remember(demo, clip) {
    val builder = if (demo.redrawOnPause) {
      ExoPlayer.Builder(context, ReplayableRenderersFactory(context))
    } else {
      ExoPlayer.Builder(context)
    }
    builder.build().apply {
      setMediaItem(MediaItem.fromUri("android.resource://${context.packageName}/${clip.resource}"))
      repeatMode = Player.REPEAT_MODE_ALL
      volume = 0f
      setVideoEffects(listOf(SweepLineGlEffect(clock)))
      prepare()
    }
  }
  var paused by remember { mutableStateOf(false) }

  LaunchedEffect(player) {
    val start = withFrameNanos { it }
    while (true) {
      val elapsedMs = (withFrameNanos { it } - start) / NanosPerMilli
      clock.publish(elapsedMs)
      paused = isPausedAt(elapsedMs)
      player.playWhenReady = !paused
      // The fix: process the last video frame again, with the line position just published.
      if (paused && demo.redrawOnPause) player.setVideoEffects(VideoFrameProcessor.REDRAW)
    }
  }

  val lifecycle = LocalLifecycleOwner.current.lifecycle
  DisposableEffect(player, lifecycle) {
    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
    lifecycle.addObserver(observer)
    onDispose {
      lifecycle.removeObserver(observer)
      player.release()
    }
  }

  var screenWidth by remember { mutableFloatStateOf(1f) }
  Box(
      modifier = modifier
          .fillMaxSize()
          .background(Color.Black)
          .onSizeChanged { screenWidth = it.width.toFloat() },
      contentAlignment = Alignment.Center,
  ) {
    // Full width, so a horizontal position on the video is the same position on screen.
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ClipAspect),
        factory = { SurfaceView(it) },
        update = { player.setVideoSurfaceView(it) },
    )

    HugeButton(
        clock = clock,
        screenWidth = { screenWidth },
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(ButtonHeightFraction)
            .padding(horizontal = 24.dp),
    )

    if (!LocalCaptureOptions.current.enabled) Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .captureAwareStatusBarsPadding()
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(demo.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
      Text(
          text = if (paused) "❚❚ Video paused" else "▶ Video playing",
          color = if (paused) PausedColor else Color.White,
          fontSize = 16.sp,
      )
    }
  }
}

@Composable
private fun HugeButton(clock: SweepClock, screenWidth: () -> Float, modifier: Modifier = Modifier) {
  val shader = remember { RuntimeShader(UI_LINE_SHADER) }
  var left by remember { mutableFloatStateOf(0f) }
  Box(
      modifier = modifier
          .onGloballyPositioned { left = it.positionInRoot().x }
          .graphicsLayer {
            // Read inside the layer block: the line moves without recomposing the button.
            shader.setFloatUniform("lineX", clock.lineX)
            shader.setFloatUniform("left", left)
            shader.setFloatUniform("screenWidth", screenWidth())
            shader.setFloatUniform("halfWidth", LineHalfWidthPx * screenWidth() / ClipWidthPx)
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
          }
          .clip(RoundedCornerShape(36.dp))
          .background(ButtonColor)
          .clickable {},
      contentAlignment = Alignment.Center,
  ) {
    Text("КНОПКА", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
  }
}

@Language("AGSL")
private const val UI_LINE_SHADER = """
uniform shader content;
uniform float lineX;
uniform float left;
uniform float screenWidth;
uniform float halfWidth;

half4 main(float2 p) {
    half4 color = content.eval(p);
    float x = (p.x + left) / screenWidth;
    float line = 1.0 - step(halfWidth, abs(x - lineX) * screenWidth);
    // Premultiplied: the line only covers pixels the button actually draws.
    return half4(mix(color.rgb, half3(1.0, 0.1, 0.55) * color.a, line), color.a);
}
"""

private const val NanosPerMilli = 1_000_000L
private const val ClipWidthPx = 1086f
private const val ClipAspect = 1086f / 1852f
private const val ButtonHeightFraction = 0.35f
private val ButtonColor = Color(0xFF6D5AE6)
private val PausedColor = Color(0xFFFFB86B)
