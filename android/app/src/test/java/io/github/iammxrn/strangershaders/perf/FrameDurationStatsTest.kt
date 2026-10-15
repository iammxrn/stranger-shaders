package io.github.iammxrn.strangershaders.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameDurationStatsTest {
  private fun ms(value: Double) = (value * 1_000_000).toLong()

  @Test fun nearestRankPercentilesOverEveryFrameSinceTheScreenOpened() {
    val stats = FrameDurationStats()
    for (i in 1..100) stats.add(ms(i.toDouble()))
    val summary = stats.summary()!!
    assertEquals(100, summary.frames)
    assertEquals(95f, summary.p95Millis, 0.001f)
    assertEquals(99f, summary.p99Millis, 0.001f)
  }

  @Test fun growsPastItsInitialCapacity() {
    val stats = FrameDurationStats()
    repeat(10_000) { stats.add(ms(8.0)) }
    stats.add(ms(40.0))
    assertEquals(10_001, stats.summary()!!.frames)
    assertEquals(8f, stats.summary()!!.p99Millis, 0.001f)
  }

  @Test fun noFramesIsMissingNotZero() {
    assertNull(FrameDurationStats().summary())
    assertEquals("UI p95 – · p99 –", uiFrameLabel(null))
  }

  @Test fun labelShowsBothPercentilesInMilliseconds() {
    assertEquals("UI p95 9.1 · p99 12.4 ms", uiFrameLabel(FrameDurationStats.Summary(600, 9.06f, 12.44f)))
  }
}
