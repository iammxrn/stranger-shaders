package io.github.iammxrn.strangershaders.wholescene

import android.content.Context
import android.opengl.GLSurfaceView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The visible output: one SurfaceView whose every frame comes from [WholeSceneRenderer].
 *
 * Same ownership rule as the own-renderer route's view: cleanup runs on the GL thread before
 * GLSurfaceView stops it, and Compose onRelease may arrive after Android already detached the view.
 */
internal class WholeSceneGlView(
  context: Context,
  private val renderer: WholeSceneRenderer,
  private val beforeShutdown: () -> Unit,
) : GLSurfaceView(context) {
  private var closed = false

  init {
    setEGLContextClientVersion(2)
    preserveEGLContextOnPause = true
    setRenderer(renderer)
    // Frames are requested by GlFrameScheduler only; there is no continuous loop on this route.
    renderMode = RENDERMODE_WHEN_DIRTY
  }

  fun shutdown() {
    if (closed) return
    closed = true
    // Producers first: the UI renderer and the player stop writing before their textures go.
    beforeShutdown()
    renderer.stopRendering()
    val released = CountDownLatch(1)
    queueEvent {
      try { renderer.release() } finally { released.countDown() }
    }
    if (!released.await(2, TimeUnit.SECONDS)) renderer.releaseStreams()
  }

  override fun onDetachedFromWindow() {
    shutdown()
    super.onDetachedFromWindow()
  }
}
