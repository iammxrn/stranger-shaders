package io.github.iammxrn.strangershaders.perf

import java.util.concurrent.atomic.AtomicLong

/**
 * Counts frames the video shader program actually rendered.
 *
 * Test instrumentation, not a displayed metric: the device smoke test uses it to check that a
 * paused pipeline stops drawing while continuous GL keeps redrawing. A count is a draw submission,
 * not GPU completion or a presented frame.
 */
object VideoPipelineStats {

  private val frameCount = AtomicLong(0)

  fun onFrameRendered() {
    frameCount.incrementAndGet()
  }

  fun frames(): Long = frameCount.get()

  fun reset() {
    frameCount.set(0)
  }
}
