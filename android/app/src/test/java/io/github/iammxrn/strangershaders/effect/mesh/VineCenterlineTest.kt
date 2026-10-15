package io.github.iammxrn.strangershaders.effect.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class VineCenterlineTest {
  private val width = 1080f
  private val height = 2424f

  @Test fun eachVineStartsOnItsOwnEdgeAndGrowsInward() {
    val left = walkVineCenterline(vine = 0, seed = 1337u, screenWidth = width, screenHeight = height)
    val right = walkVineCenterline(vine = 1, seed = 1337u, screenWidth = width, screenHeight = height)
    val top = walkVineCenterline(vine = 2, seed = 1337u, screenWidth = width, screenHeight = height)
    val bottom = walkVineCenterline(vine = 3, seed = 1337u, screenWidth = width, screenHeight = height)

    assertEquals(0f, left.x(0))
    assertEquals(4f, left.x(1))
    assertEquals(width, right.x(0))
    assertEquals(width - 4f, right.x(1))
    assertEquals(0f, top.y(0))
    assertEquals(4f, top.y(1))
    assertEquals(height, bottom.y(0))
    assertEquals(height - 4f, bottom.y(1))
  }

  @Test fun startPointWandersAlongTheEdgeWithinTenToNinetyPercent() {
    for (seed in listOf(1337u, 1474u, 1611u, 42u)) {
      val left = walkVineCenterline(vine = 0, seed = seed, screenWidth = width, screenHeight = height)
      assertTrue("seed $seed y=${left.y(0)}", left.y(0) in height * 0.1f..height * 0.9f)
      val top = walkVineCenterline(vine = 2, seed = seed, screenWidth = width, screenHeight = height)
      assertTrue("seed $seed x=${top.x(0)}", top.x(0) in width * 0.1f..width * 0.9f)
    }
  }

  @Test fun firstThreeStepsLeaveTheEdgeStraight() {
    val left = walkVineCenterline(vine = 0, seed = 1337u, screenWidth = width, screenHeight = height)
    for (i in 1..3) assertEquals(left.y(0), left.y(i), 1e-4f)
  }

  @Test fun everyStepIsFourPixelsLong() {
    val vine = walkVineCenterline(vine = 0, seed = 1337u, screenWidth = width, screenHeight = height)
    assertTrue(vine.count > 100)
    for (i in 1 until vine.count) {
      val step = hypot(vine.x(i) - vine.x(i - 1), vine.y(i) - vine.y(i - 1))
      assertEquals("step $i", 4f, step, 1e-3f)
    }
  }

  @Test fun walkStaysInsideTheScreenAndUnderHalfTheDiagonal() {
    val maxLength = vinesMaxLength(width, height)
    val maxPoints = 1 + (maxLength / VineStepPx).toInt() + 1
    for (vine in 0 until VinesCount) {
      for (seed in listOf(1337u, 1474u, 7u)) {
        val line = walkVineCenterline(vine, seed, width, height)
        assertTrue("vine $vine seed $seed count ${line.count}", line.count in 2..maxPoints)
        assertTrue(line.count <= VineMaxPoints)
        for (i in 0 until line.count) {
          assertTrue(line.x(i) in 0f..width)
          assertTrue(line.y(i) in 0f..height)
        }
      }
    }
  }

  @Test fun sameSeedIsDeterministicAndDifferentSeedsDiffer() {
    val a = walkVineCenterline(vine = 0, seed = 1337u, screenWidth = width, screenHeight = height)
    val b = walkVineCenterline(vine = 0, seed = 1337u, screenWidth = width, screenHeight = height)
    val c = walkVineCenterline(vine = 0, seed = 1474u, screenWidth = width, screenHeight = height)
    assertEquals(a.count, b.count)
    assertArrayEquals(a.points.copyOf(a.count * 2), b.points.copyOf(b.count * 2), 0f)
    val differs = (0 until minOf(a.count, c.count)).any { abs(a.y(it) - c.y(it)) > 1e-3f }
    assertTrue(differs)
  }

  @Test fun walkActuallyBendsAfterTheStraightRoot() {
    val line = walkVineCenterline(vine = 0, seed = 1337u, screenWidth = width, screenHeight = height)
    val bends = (4 until line.count).any { abs(line.y(it) - line.y(0)) > 1f }
    assertTrue(bends)
    assertFalse(line.count == VineMaxPoints)
  }

  @Test fun maxLengthIsHalfTheDiagonal() {
    assertEquals(hypot(width, height) * 0.53f, vinesMaxLength(width, height), 1e-2f)
  }
}
