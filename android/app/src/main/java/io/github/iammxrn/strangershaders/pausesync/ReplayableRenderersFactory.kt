package io.github.iammxrn.strangershaders.pausesync

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.PlaybackVideoGraphWrapper
import androidx.media3.exoplayer.video.VideoFrameReleaseControl
import androidx.media3.exoplayer.video.VideoRendererEventListener

/**
 * Renderers whose video pipeline keeps the last frame, so `setVideoEffects(REDRAW)` can process it
 * again with new shader parameters.
 *
 * Media3 1.11.0 has no public switch for this: the replayable cache is off by default and only
 * [PlaybackVideoGraphWrapper.Builder.setEnableReplayableCache] turns it on, so the video renderer
 * overrides the one method that builds that wrapper. That wrapper is a library-restricted API, see
 * below. The cache costs memory and a copy per frame.
 */
@UnstableApi
internal class ReplayableRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

  override fun buildVideoRenderers(
    context: Context,
    extensionRendererMode: Int,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: VideoRendererEventListener,
    allowedVideoJoiningTimeMs: Long,
    out: ArrayList<Renderer>,
  ) {
    out += ReplayableVideoRenderer(
        MediaCodecVideoRenderer.Builder(context)
            .setMediaCodecSelector(mediaCodecSelector)
            .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
            .setEnableDecoderFallback(enableDecoderFallback)
            .setEventHandler(eventHandler)
            .setEventListener(eventListener)
            .setMaxDroppedFramesToNotify(MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY),
    )
  }
}

@UnstableApi
private class ReplayableVideoRenderer(builder: Builder) : MediaCodecVideoRenderer(builder) {
  // PlaybackVideoGraphWrapper is restricted to the androidx.media3 library group in 1.11.0, and
  // this override is the only way to reach setEnableReplayableCache (Media3's own TODO b/391109644
  // asks for an explicit API). It can break with any Media3 update: a cost of this fix, not a detail.
  @SuppressLint("RestrictedApi")
  override fun createPlaybackVideoGraphWrapper(
    context: Context,
    videoFrameReleaseControl: VideoFrameReleaseControl,
  ): PlaybackVideoGraphWrapper =
      PlaybackVideoGraphWrapper.Builder(context, videoFrameReleaseControl)
          .setEnablePlaylistMode(true)
          .setEnableReplayableCache(true)
          .setClock(clock)
          .build()
}
