package io.github.iammxrn.strangershaders.effect.mesh

import org.junit.Assert.assertEquals
import org.junit.Test

class VinesCycleTest {
  private fun cycle(delaySeconds: Double = 3.0) = VinesCycle(pauseSeconds = { delaySeconds })

  @Test fun firstGrowthStartsFourSecondsAfterTheFirstFrame() {
    val cycle = cycle()
    assertEquals(0f, cycle.progress(100.0))
    assertEquals(0f, cycle.progress(103.9))
    assertEquals(0f, cycle.progress(104.0))
    assertEquals(0.5f, cycle.progress(107.0), 1e-6f)
  }

  @Test fun growthEasesInAndOutOverSixSeconds() {
    val cycle = cycle()
    cycle.progress(0.0)
    cycle.progress(4.0)
    assertEquals(0.5f, cycle.progress(7.0), 1e-6f)
    assertEquals(4 * 0.25f * 0.25f * 0.25f, cycle.progress(5.5), 1e-6f)
    assertEquals(1f, cycle.progress(10.0), 1e-6f)
  }

  @Test fun retractMirrorsGrowthAndThenPausesWithANewSeed() {
    val cycle = cycle(delaySeconds = 3.0)
    cycle.progress(0.0)
    cycle.progress(4.0)
    assertEquals(1f, cycle.progress(10.0), 1e-6f)
    assertEquals(VinesCycle.BaseSeed, cycle.seed)
    assertEquals(0.5f, cycle.progress(13.0), 1e-6f)
    assertEquals(0f, cycle.progress(16.0), 1e-6f)
    assertEquals(VinesCycle.BaseSeed + 137u, cycle.seed)
    assertEquals(0f, cycle.progress(18.9))
    assertEquals(0f, cycle.progress(19.0))
    assertEquals(0.5f, cycle.progress(22.0), 1e-6f)
  }

  @Test fun seedAdvancesByOneStepPerCompletedCycle() {
    val cycle = cycle(delaySeconds = 2.0)
    var t = 0.0
    cycle.progress(t)
    repeat(3) {
      t += 4.0; cycle.progress(t)
      t += 6.0; cycle.progress(t)
      t += 6.0; cycle.progress(t)
      t += 2.0; cycle.progress(t)
      t -= 4.0
    }
    assertEquals(VinesCycle.BaseSeed + 3u * 137u, cycle.seed)
  }

  @Test fun pauseLengthComesFromTheInjectedRandomRange() {
    var requested: ClosedFloatingPointRange<Double>? = null
    val cycle = VinesCycle(pauseSeconds = { requested = it; 2.5 })
    cycle.progress(0.0)
    cycle.progress(4.0)
    cycle.progress(10.0)
    cycle.progress(16.0)
    assertEquals(2.0..4.0, requested)
    assertEquals(0f, cycle.progress(18.4))
    assertEquals(0f, cycle.progress(18.5))
    assertEquals(0.5f, cycle.progress(21.5), 1e-6f)
  }
}
