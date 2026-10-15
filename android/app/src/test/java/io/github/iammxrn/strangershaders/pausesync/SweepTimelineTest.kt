package io.github.iammxrn.strangershaders.pausesync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SweepTimelineTest {
  @Test fun lineCrossesTheScreenOncePerPeriodFromLeftToRight() {
    assertEquals(0f, sweepLineX(0), 1e-6f)
    assertEquals(0.5f, sweepLineX(SweepPeriodMs / 2), 1e-6f)
    assertEquals(0f, sweepLineX(SweepPeriodMs), 1e-6f)
    var previous = -1f
    for (ms in 0 until SweepPeriodMs step 16) {
      val x = sweepLineX(ms)
      assertTrue(x in 0f..1f)
      assertTrue(x > previous)
      previous = x
    }
  }

  @Test fun videoPlaysFirstThenPausesThenRepeats() {
    assertFalse(isPausedAt(0))
    assertFalse(isPausedAt(PlayMs - 1))
    assertTrue(isPausedAt(PlayMs))
    assertTrue(isPausedAt(PlayMs + PauseMs - 1))
    assertFalse(isPausedAt(PlayMs + PauseMs))
    assertTrue(isPausedAt(2 * (PlayMs + PauseMs) - 1))
  }

  @Test fun theLineVisiblyMovesDuringOnePause() {
    // Recordings rely on the UI line travelling a large part of the screen while the video is paused.
    assertTrue(PauseMs.toFloat() / SweepPeriodMs >= 0.4f)
  }
}
