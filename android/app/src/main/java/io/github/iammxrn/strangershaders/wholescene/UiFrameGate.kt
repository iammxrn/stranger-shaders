package io.github.iammxrn.strangershaders.wholescene

/**
 * Decides when the recorded UI may be rendered into the UI texture's BufferQueue. Main thread only.
 *
 * HWUI's RenderThread is shared with the window. If it rendered UI frames faster than the GL thread
 * latches them, the queue would fill and a later dequeue would stall that thread for the whole app.
 * The gate therefore allows a new submission only when the previous one has been committed by HWUI
 * and every buffer the queue reported has been latched by GL. Changes recorded meanwhile only mark
 * the UI dirty; the newest recording is rendered once the queue drains, so intermediate states
 * collapse into one frame instead of piling up.
 *
 * @param queueDrained true when every UI buffer that reached the queue has been latched by GL.
 */
internal class UiFrameGate(private val queueDrained: () -> Boolean) {
  /** Changes with every new target surface, so commit callbacks of an older surface are ignored. */
  var generation = 0
    private set

  private var hasSurface = false
  private var started = false
  private var dirty = false
  private var submitted = 0L
  private var committed = 0L

  val isDirty: Boolean get() = dirty
  val inFlight: Boolean get() = committed < submitted

  /** A new or removed target. A new surface starts empty, so the next frame must redraw it. */
  fun onSurface(available: Boolean) {
    generation++
    hasSurface = available
    submitted = 0
    committed = 0
    dirty = true
  }

  fun onStarted(value: Boolean) {
    started = value
    if (value) dirty = true
  }

  fun onRecorded() {
    dirty = true
  }

  /** HWUI finished the submission of [forGeneration], whether or not it produced a buffer. */
  fun onCommitted(forGeneration: Int) {
    if (forGeneration == generation) committed++
  }

  /**
   * HWUI refused the frame (stopped context, lost surface, dropped frame). No commit callback is
   * guaranteed, so the submission is settled here; the content stays dirty for the next attempt.
   */
  fun onSubmitFailed(forGeneration: Int, surfaceLost: Boolean) {
    if (forGeneration != generation) return
    committed = submitted
    dirty = true
    if (surfaceLost) hasSurface = false
  }

  /** Returns the generation to submit for, or null when nothing should be rendered now. */
  fun takeSubmission(): Int? {
    if (!dirty || !hasSurface || !started || inFlight || !queueDrained()) return null
    dirty = false
    submitted++
    return generation
  }
}
