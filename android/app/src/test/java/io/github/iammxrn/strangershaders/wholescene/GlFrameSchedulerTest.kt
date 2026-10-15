package io.github.iammxrn.strangershaders.wholescene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlFrameSchedulerTest {
  private class FakeFrames : FrameSource {
    var posted: ((Long) -> Unit)? = null
    var posts = 0
    var cancels = 0
    override fun postFrame(callback: (Long) -> Unit) { posted = callback; posts++ }
    override fun cancel() { posted = null; cancels++ }
    fun vsync() { val callback = checkNotNull(posted); posted = null; callback(0L) }
  }

  @Test fun requestsWithinOneVsyncBecomeOneRenderWithAllReasons() {
    val frames = FakeFrames()
    val renders = mutableListOf<Int>()
    val scheduler = GlFrameScheduler(frames) { renders += it }
    scheduler.request(RenderReason.Video)
    scheduler.request(RenderReason.Ui)
    scheduler.request(RenderReason.Effect)
    scheduler.request(RenderReason.Effect)
    assertEquals("one display callback per interval", 1, frames.posts)
    frames.vsync()
    assertEquals(listOf(RenderReason.Video or RenderReason.Ui or RenderReason.Effect), renders)
  }

  @Test fun withoutRequestsNothingIsPolled() {
    val frames = FakeFrames()
    var renders = 0
    val scheduler = GlFrameScheduler(frames) { renders++ }
    scheduler.request(RenderReason.Video)
    frames.vsync()
    assertEquals(1, renders)
    assertFalse("an idle scheduler must not re-arm itself", scheduler.isScheduled)
    assertEquals(1, frames.posts)
  }

  @Test fun aRequestAfterTheCallbackSchedulesTheNextVsync() {
    val frames = FakeFrames()
    var renders = 0
    val scheduler = GlFrameScheduler(frames) { renders++ }
    scheduler.request(RenderReason.Effect)
    frames.vsync()
    scheduler.request(RenderReason.Effect)
    assertTrue(scheduler.isScheduled)
    frames.vsync()
    assertEquals(2, renders)
    assertEquals(2, frames.posts)
  }

  @Test fun closeCancelsPendingWorkAndRejectsLateRequests() {
    val frames = FakeFrames()
    var renders = 0
    val scheduler = GlFrameScheduler(frames) { renders++ }
    scheduler.request(RenderReason.Video)
    val stale = checkNotNull(frames.posted)
    scheduler.close()
    assertEquals(1, frames.cancels)
    stale(0L) // A callback that was already dispatched still must not render.
    scheduler.request(RenderReason.Ui)
    assertEquals(0, renders)
    assertEquals("no frame callback after close", 1, frames.posts)
  }
}
