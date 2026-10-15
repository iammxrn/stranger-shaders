package io.github.iammxrn.strangershaders.wholescene

import java.util.concurrent.atomic.AtomicLong

/**
 * Counters of the whole-scene route, for device tests and the opt-in `sceneStats` log.
 *
 * Every counter names an event in this app, not a displayed result: a GL draw is a draw call
 * sequence ending in eglSwapBuffers on our thread, not GPU completion or a presented frame; a UI
 * submission is a HardwareRenderer sync request, not a finished raster.
 */
object WholeSceneStats {
  /** Times the UI host recorded its children into the offscreen RenderNode. */
  val uiRecords = AtomicLong()

  /** HardwareRenderer.syncAndDraw calls that HWUI accepted. */
  val uiSubmits = AtomicLong()

  /** Accepted submissions HWUI reported as committed. */
  val uiCommits = AtomicLong()

  /** Recorded changes that could not be submitted at once and waited for the queue to drain. */
  val uiDeferred = AtomicLong()

  /** UI buffers latched into the GL texture (SurfaceTexture.updateTexImage). */
  val uiLatches = AtomicLong()

  /** Decoder buffers latched into the GL texture. */
  val videoLatches = AtomicLong()

  /** Vsync callbacks that asked GLSurfaceView for a frame. */
  val renderRequests = AtomicLong()

  /** onDrawFrame invocations that issued the full pass sequence. */
  val glDraws = AtomicLong()

  /** Requests per [RenderReason] bit, counted once per coalesced vsync request. */
  private val reasons = RenderReason.all.associateWith { AtomicLong() }

  /** Resource balance, so tests can check that leaving the screen releases what it created. */
  val liveSurfaceTextures = AtomicLong()
  val liveUiRenderers = AtomicLong()

  fun onRenderRequested(reasonBits: Int) {
    renderRequests.incrementAndGet()
    for ((bit, counter) in reasons) if (reasonBits and bit != 0) counter.incrementAndGet()
  }

  fun requestsFor(reason: Int): Long = reasons.getValue(reason).get()

  /** Event counters start from zero for every screen; live resource balances are never reset. */
  fun reset() {
    listOf(uiRecords, uiSubmits, uiCommits, uiDeferred, uiLatches, videoLatches, renderRequests, glDraws)
        .forEach { it.set(0) }
    reasons.values.forEach { it.set(0) }
  }

  fun summary(): String = buildString {
    append("glDraws=").append(glDraws.get())
    append(" renderRequests=").append(renderRequests.get())
    append(" videoLatches=").append(videoLatches.get())
    append(" uiRecords=").append(uiRecords.get())
    append(" uiSubmits=").append(uiSubmits.get())
    append(" uiCommits=").append(uiCommits.get())
    append(" uiDeferred=").append(uiDeferred.get())
    append(" uiLatches=").append(uiLatches.get())
    for ((bit, counter) in reasons) append(" req.").append(RenderReason.name(bit)).append('=').append(counter.get())
    append(" liveSurfaceTextures=").append(liveSurfaceTextures.get())
    append(" liveUiRenderers=").append(liveUiRenderers.get())
  }
}
