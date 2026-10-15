package io.github.iammxrn.strangershaders.wholescene

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Surface
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.ui.platform.ComposeView
import androidx.media3.exoplayer.ExoPlayer
import io.github.iammxrn.strangershaders.player.ResourceSlot

/**
 * Owns every resource of the whole-scene route and the order in which they are wired and released.
 *
 *   index 0  WholeSceneGlView  SurfaceView below the window: the only visible picture of the scene
 *   index 1  UiTextureHost     the product UI, laid out and touchable here, drawn into a texture
 *
 * Both fill this container, so a UI texel, the GL output pixel it lands on and the touch target
 * under it share one coordinate system. Main thread only, except the renderer callbacks noted.
 */
@SuppressLint("ViewConstructor")
internal class WholeSceneContainer(
  context: Context,
  private val player: ExoPlayer,
  parentComposition: CompositionContext,
  content: @Composable () -> Unit,
) : FrameLayout(context) {

  private val mainHandler = Handler(Looper.getMainLooper())
  private val videoSurfaces = ResourceSlot<Surface> { it.release() }
  private val uiSurfaces = ResourceSlot<Surface> { it.release() }
  private var released = false

  private val scheduler: GlFrameScheduler = GlFrameScheduler(ChoreographerFrames()) { reasons ->
    WholeSceneStats.onRenderRequested(reasons)
    glView.requestRender()
  }

  val publisher = SceneSnapshotPublisher(onChanged = scheduler::request)

  private val renderer: WholeSceneRenderer = WholeSceneRenderer(
      snapshots = { publisher.current },
      mainHandler = mainHandler,
      onVideoStream = { stream -> mainHandler.post { attachVideo(stream) } },
      onUiStream = { stream -> mainHandler.post { attachUi(stream) } },
      onVideoFrameAvailable = { scheduler.request(RenderReason.Video) },
      onUiFrameAvailable = { scheduler.request(RenderReason.Ui) },
      onUiLatched = { stream -> if (!released) uiHost.onLatched(stream) },
  )

  private val glView: WholeSceneGlView = WholeSceneGlView(context, renderer, beforeShutdown = ::releaseProducers)

  private val uiHost: UiTextureHost = UiTextureHost(context)

  init {
    addView(glView, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    addView(uiHost, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    uiHost.addView(
        ComposeView(context).apply {
          setParentCompositionContext(parentComposition)
          setContent(content)
        },
        LayoutParams(MATCH_PARENT, MATCH_PARENT),
    )
  }

  fun setVideoSize(width: Int, height: Int) {
    renderer.videoWidth = width
    renderer.videoHeight = height
    scheduler.request(RenderReason.Surface)
  }

  fun onResume() {
    if (released) return
    glView.onResume()
    uiHost.setStarted(true)
    scheduler.request(RenderReason.Lifecycle)
  }

  fun onPause() {
    if (released) return
    uiHost.setStarted(false)
    glView.onPause()
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    scheduler.request(RenderReason.Surface)
  }

  /** Called on the main thread once the GL thread created the decoder texture of a new context. */
  private fun attachVideo(stream: ExternalStream) {
    val texture = stream.surfaceTexture
    if (released || videoSurfaces.isClosed || texture.isReleased) return
    val surface = Surface(texture)
    if (videoSurfaces.replace(surface)) player.setVideoSurface(surface)
  }

  /** Same for the UI texture: point HWUI at it before the previous Surface is released. */
  private fun attachUi(stream: ExternalStream) {
    val texture = stream.surfaceTexture
    if (released || uiSurfaces.isClosed || texture.isReleased) return
    val surface = Surface(texture)
    uiHost.setTarget(surface, stream)
    uiSurfaces.replace(surface)
    scheduler.request(RenderReason.Surface)
  }

  /** Producers stop before the GL thread releases the textures they write into. */
  private fun releaseProducers() {
    released = true
    scheduler.close()
    uiHost.release()
    uiSurfaces.close()
    player.setVideoSurface(null)
    videoSurfaces.close()
  }

  /** Idempotent: Compose onRelease, onDetachedFromWindow, whichever comes first. */
  fun release() {
    glView.shutdown()
  }

  override fun onDetachedFromWindow() {
    // Children detach before this callback; the GL view's own detach already ran shutdown().
    release()
    super.onDetachedFromWindow()
  }

  private inner class ChoreographerFrames : FrameSource {
    private var callback: Choreographer.FrameCallback? = null

    override fun postFrame(callback: (frameTimeNanos: Long) -> Unit) {
      val frameCallback = Choreographer.FrameCallback { callback(it) }
      this.callback = frameCallback
      Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun cancel() {
      callback?.let(Choreographer.getInstance()::removeFrameCallback)
      callback = null
    }
  }
}
