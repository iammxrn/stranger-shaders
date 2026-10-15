package io.github.iammxrn.strangershaders.wholescene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiFrameGateTest {
  private var drained = true
  private val gate = UiFrameGate { drained }

  private fun ready() {
    gate.onSurface(true)
    gate.onStarted(true)
  }

  @Test fun nothingIsSubmittedWithoutSurfaceOrWhileStopped() {
    gate.onRecorded()
    assertNull(gate.takeSubmission())
    gate.onSurface(true)
    assertNull("stopped renderer", gate.takeSubmission())
    gate.onStarted(true)
    assertNotNull(gate.takeSubmission())
  }

  @Test fun atMostOneSubmissionInFlightAndChangesCollapse() {
    ready()
    val first = checkNotNull(gate.takeSubmission())
    repeat(5) { gate.onRecorded() }
    assertNull("previous frame not committed yet", gate.takeSubmission())
    gate.onCommitted(first)
    assertNotNull("the newest recording follows once", gate.takeSubmission())
    assertNull("five changes became one frame", gate.run { onCommitted(generation); takeSubmission() })
  }

  @Test fun anUnlatchedBufferBlocksTheNextSubmission() {
    ready()
    gate.onCommitted(checkNotNull(gate.takeSubmission()))
    gate.onRecorded()
    drained = false
    assertNull("GL has not latched the queued UI buffer", gate.takeSubmission())
    drained = true
    assertNotNull(gate.takeSubmission())
  }

  @Test fun commitsOfAnOldSurfaceAreIgnored() {
    ready()
    val old = checkNotNull(gate.takeSubmission())
    gate.onSurface(true)
    gate.onCommitted(old)
    val fresh = checkNotNull(gate.takeSubmission())
    assertTrue(fresh != old)
    gate.onRecorded()
    gate.onCommitted(old)
    assertNull("a stale commit must not release the new in-flight frame", gate.takeSubmission())
  }

  @Test fun aRefusedFrameStaysDirtyAndALostSurfaceWaitsForANewOne() {
    ready()
    val generation = checkNotNull(gate.takeSubmission())
    gate.onSubmitFailed(generation, surfaceLost = false)
    assertTrue(gate.isDirty)
    assertNotNull("refused frames are retried", gate.takeSubmission())
    gate.onSubmitFailed(gate.generation, surfaceLost = true)
    assertNull(gate.takeSubmission())
    gate.onSurface(true)
    assertNotNull(gate.takeSubmission())
  }

  @Test fun aLateCommitAfterAFailureDoesNotWedgeTheGate() {
    ready()
    val generation = checkNotNull(gate.takeSubmission())
    gate.onSubmitFailed(generation, surfaceLost = false)
    gate.onCommitted(generation)
    assertNotNull(gate.takeSubmission())
    gate.onCommitted(generation)
    gate.onRecorded()
    assertNotNull(gate.takeSubmission())
  }

  @Test fun restartingRedrawsEvenWithoutAChange() {
    ready()
    gate.onCommitted(checkNotNull(gate.takeSubmission()))
    gate.onStarted(false)
    gate.onStarted(true)
    assertEquals(true, gate.isDirty)
    assertNotNull(gate.takeSubmission())
  }
}
