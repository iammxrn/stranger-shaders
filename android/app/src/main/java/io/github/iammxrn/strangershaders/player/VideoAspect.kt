package io.github.iammxrn.strangershaders.player

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The clip's native size.
 *
 * Falls back to the bundled clip's intrinsic size while the player reports nothing, which keeps the
 * layout stable from the very first frame instead of letting the surface pop once the size arrives.
 */
@Composable
fun rememberVideoSize(player: ExoPlayer): IntSize {
  var reported by remember(player) {
    mutableStateOf(IntSize(player.videoSize.width, player.videoSize.height))
  }

  DisposableEffect(player) {
    val listener = object : Player.Listener {
      override fun onVideoSizeChanged(videoSize: VideoSize) {
        Log.d(VideoAspectTag, "onVideoSizeChanged: ${videoSize.width}x${videoSize.height}")
        reported = IntSize(videoSize.width, videoSize.height)
      }
    }
    player.addListener(listener)
    onDispose { player.removeListener(listener) }
  }

  return if (reported.width > 0 && reported.height > 0) reported else BundledClipSize
}

/** Intrinsic size of res/raw/sample_motion.mp4. */
private val BundledClipSize = IntSize(720, 1280)

private const val VideoAspectTag = "VideoAspect"

/**
 * Sizes the video surface to *cover* its container at the clip's own aspect ratio, centred, so the
 * overflow is clipped instead of the picture being stretched. The Compose equivalent of
 * ContentScale.Crop, or of AspectRatioFrameLayout.RESIZE_MODE_ZOOM.
 *
 * ExoPlayer does no scaling of its own when handed a bare SurfaceView or TextureView: the surface
 * is simply stretched to whatever bounds the layout gives it. A 1086x1852 clip in a 1080x2424 box
 * therefore comes out visibly elongated.
 *
 * It also fixes the letterboxing in the setVideoEffects demo. There media3 fits the frame into the
 * output surface preserving aspect, so a surface with the wrong aspect gets black bars. Give the
 * surface the clip's aspect ratio and there is nothing left to letterbox.
 *
 * The parent must clip - see [Modifier.clipToBounds] - or the overflow paints outside the container.
 */
fun Modifier.centerCropVideo(videoSize: IntSize): Modifier = layout { measurable, constraints ->
  val containerWidth = constraints.maxWidth
  val containerHeight = constraints.maxHeight

  val known = minOf(videoSize.width, videoSize.height, containerWidth, containerHeight) > 0
  if (!known) {
    val placeable = measurable.measure(constraints)
    return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
  }

  val scale = max(
      containerWidth.toFloat() / videoSize.width,
      containerHeight.toFloat() / videoSize.height,
  )
  val childWidth = (videoSize.width * scale).roundToInt()
  val childHeight = (videoSize.height * scale).roundToInt()

  val placeable = measurable.measure(Constraints.fixed(childWidth, childHeight))
  layout(containerWidth, containerHeight) {
    placeable.place((containerWidth - childWidth) / 2, (containerHeight - childHeight) / 2)
  }
}
