package io.github.iammxrn.strangershaders.wholescene

/** The display's frame callback, abstracted so the policy can be tested without a Choreographer. */
internal interface FrameSource {
  fun postFrame(callback: (frameTimeNanos: Long) -> Unit)
  fun cancel()
}

/**
 * Display-paced, coalescing render requests. Main thread only.
 *
 * Every source (decoder frame, UI frame, effect clock, vine growth, surface or lifecycle change)
 * calls [request]. The first request in a vsync interval posts one display frame callback; later
 * requests only add their reason bits. That callback asks the GL thread for exactly one frame. With
 * no requests no callback is posted, so an idle screen is not polled.
 *
 * This bounds requests, not draws: GLSurfaceView in RENDERMODE_WHEN_DIRTY merges a request that
 * arrives while it is still drawing, and eglSwapBuffers back-pressure decides when the GL thread can
 * start the next one. Neither is a promise that each vsync is presented.
 */
internal class GlFrameScheduler(
  private val frames: FrameSource,
  private val render: (reasons: Int) -> Unit,
) {
  private var pending = 0
  private var scheduled = false
  private var closed = false

  val isScheduled: Boolean get() = scheduled

  fun request(reason: Int) {
    if (closed) return
    pending = pending or reason
    if (scheduled) return
    scheduled = true
    frames.postFrame(::onFrame)
  }

  private fun onFrame(@Suppress("UNUSED_PARAMETER") frameTimeNanos: Long) {
    scheduled = false
    if (closed) return
    val reasons = pending
    pending = 0
    if (reasons != 0) render(reasons)
  }

  /** Drops pending work and refuses later requests; a delayed callback becomes a no-op. */
  fun close() {
    if (closed) return
    closed = true
    pending = 0
    if (scheduled) frames.cancel()
    scheduled = false
  }
}
