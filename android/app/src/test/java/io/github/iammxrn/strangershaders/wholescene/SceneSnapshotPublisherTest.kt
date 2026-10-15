package io.github.iammxrn.strangershaders.wholescene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.iammxrn.strangershaders.effect.EffectSnapshot

class SceneSnapshotPublisherTest {
  private val requests = mutableListOf<Int>()
  private val publisher = SceneSnapshotPublisher { requests += it }

  @Test fun unchangedValuesRequestNoFrame() {
    publisher.publishEffect(EffectSnapshot())
    publisher.publishVines(VinesFrame())
    assertEquals(emptyList<Int>(), requests)
  }

  @Test fun everyEffectChangeRequestsAFrameAndKeepsVines() {
    publisher.publishEffect(EffectSnapshot(upsideDownAmount = 1f, timeSeconds = 1f))
    publisher.publishVines(VinesFrame(progress = .5f, seed = 7u))
    val before = publisher.current
    publisher.publishEffect(EffectSnapshot(upsideDownAmount = 1f, timeSeconds = 2f))
    assertEquals(listOf(RenderReason.Effect, RenderReason.Vines, RenderReason.Effect), requests)
    assertEquals(before.vines, publisher.current.vines)
    assertEquals(2f, publisher.current.effect.timeSeconds)
  }

  @Test fun vineGrowthUnderAnIdleEffectIsInvisibleAndRequestsNothing() {
    publisher.publishVines(VinesFrame(progress = .3f))
    assertEquals(emptyList<Int>(), requests)
    assertFalse(publisher.current.drawsVines)
  }

  @Test fun aRenderThreadReadIsOneImmutableObject() {
    publisher.publishEffect(EffectSnapshot(upsideDownAmount = 1f, timeSeconds = 4f))
    val read = publisher.current
    publisher.publishEffect(EffectSnapshot(upsideDownAmount = 1f, timeSeconds = 5f))
    assertEquals("an earlier read never changes under the reader", 4f, read.effect.timeSeconds)
    assertSame(publisher.current, publisher.current)
  }

  @Test fun overlaysFollowTheReferenceVisibilityRules() {
    assertFalse(SceneSnapshot(EffectSnapshot(glitchIntensity = 1f), VinesFrame(1f)).drawsOverlays)
    assertTrue(SceneSnapshot(EffectSnapshot(upsideDownAmount = 1f), VinesFrame(0f)).drawsOverlays)
    assertFalse(SceneSnapshot(EffectSnapshot(upsideDownAmount = 1f), VinesFrame(0f)).drawsVines)
    assertTrue(SceneSnapshot(EffectSnapshot(upsideDownAmount = 1f), VinesFrame(.1f)).drawsVines)
  }

  @Test fun centreCropMatchesTheOwnRendererRule() {
    assertEquals(1f to 1f, centreCropScale(0, 0, 1080, 2400))
    val (x, y) = centreCropScale(1086, 1852, 1080, 2400)
    assertEquals((1080f / 2400f) / (1086f / 1852f), x, 1e-6f)
    assertEquals(1f, y)
    val (wx, wy) = centreCropScale(1920, 1080, 1080, 1080)
    assertEquals(1080f / 1920f, wx, 1e-6f)
    assertEquals(1f, wy)
  }
}
