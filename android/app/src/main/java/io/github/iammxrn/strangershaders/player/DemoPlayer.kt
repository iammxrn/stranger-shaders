package io.github.iammxrn.strangershaders.player

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import io.github.iammxrn.strangershaders.R
import io.github.iammxrn.strangershaders.teaching.LocalCaptureOptions

/**
 * A muted, looping ExoPlayer over the bundled clip.
 *
 * @param effects passed to [ExoPlayer.setVideoEffects]. Empty for every demo except the Media3
 * video-pipeline one.
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberDemoPlayer(effects: ImmutableList<Effect> = persistentListOf()): ExoPlayer {
  val context = LocalContext.current
  val capture = LocalCaptureOptions.current
  val clip = LocalDemoClip.current

  val player = remember {
    ExoPlayer.Builder(context).build().apply {
      setMediaItem(MediaItem.fromUri(rawResourceUri(context.packageName, clip.resource)))
      repeatMode = Player.REPEAT_MODE_ALL
      volume = 0f
      if (effects.isNotEmpty()) {
        setVideoEffects(effects)
      }
      if (capture.enabled) seekTo(capture.videoPositionMs)
      playWhenReady = !capture.paused
      prepare()
    }
  }

  val lifecycle = LocalLifecycleOwner.current.lifecycle
  DisposableEffect(player, lifecycle) {
    var resumePlayback = !capture.paused
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_STOP -> {
          resumePlayback = player.playWhenReady
          player.pause()
        }
        Lifecycle.Event.ON_START -> player.playWhenReady = resumePlayback
        else -> Unit
      }
    }
    lifecycle.addObserver(observer)
    onDispose {
      lifecycle.removeObserver(observer)
      player.release()
    }
  }

  return player
}

/** DefaultDataSource resolves the `android.resource` scheme through RawResourceDataSource. */
private fun rawResourceUri(packageName: String, resourceId: Int): String =
  "android.resource://$packageName/$resourceId"

/**
 * Video in a [SurfaceView]: frames go to a dedicated Surface that SurfaceFlinger composites as its
 * own layer. The View hierarchy only punches a transparent hole where this sits, so no RenderEffect
 * on any ancestor can ever see a video pixel.
 */
@Composable
fun SurfaceViewVideo(
  player: ExoPlayer,
  modifier: Modifier = Modifier,
) {
  val videoSize = rememberVideoSize(player)

  Box(modifier = modifier.clipToBounds()) {
    AndroidView(
        modifier = Modifier.centerCropVideo(videoSize),
        factory = { context -> SurfaceView(context) },
        update = { view -> player.setVideoSurfaceView(view) },
    )
  }
}

/**
 * Video in a [TextureView]: frames land in a SurfaceTexture that HWUI draws as an ordinary texture
 * inside the View hierarchy's render node, which is exactly what makes an ancestor RenderEffect
 * able to sample them.
 */
@Composable
fun TextureViewVideo(
  player: ExoPlayer,
  modifier: Modifier = Modifier,
) {
  val videoSize = rememberVideoSize(player)

  Box(modifier = modifier.clipToBounds()) {
    AndroidView(
        modifier = Modifier.centerCropVideo(videoSize),
        factory = { context -> TextureView(context) },
        update = { view -> player.setVideoTextureView(view) },
    )
  }
}
