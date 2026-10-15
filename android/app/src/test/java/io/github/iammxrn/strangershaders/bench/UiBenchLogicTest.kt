package io.github.iammxrn.strangershaders.bench

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiBenchLogicTest {
  private val period = 1e9 / 60

  @Test fun theClockAdvancesOneStatePerRefresh() {
    val clock = UiBenchClock(period)
    val start = 1_000_000_000L
    assertEquals(0L, clock.onFrame(start))
    for (i in 1..10) assertEquals(i.toLong(), clock.onFrame(start + Math.round(i * period)))
  }

  @Test fun aLateFrameSkipsTheStatesOfTheRefreshesItMissed() {
    val clock = UiBenchClock(period)
    val start = 5_000_000L
    clock.onFrame(start)
    clock.onFrame(start + Math.round(period))
    // Three refreshes later: states 2 and 3 were never drawn.
    assertEquals(4L, clock.onFrame(start + Math.round(4 * period)))
  }

  @Test fun countingVsyncsStaysOnTheGridWhenTheNominalPeriodIsSlightlyOff() {
    // The real refresh is 16.6672 ms, the nominal one 16.6667 ms: 0.5 us per frame of drift.
    val clock = UiBenchClock(period)
    val real = 16_667_200.0
    var k = 0L
    for (i in 0..20_000) k = clock.onFrame(Math.round(i * real))
    assertEquals(20_000L, k)
  }

  @Test fun aRepeatedFrameTimeKeepsTheState() {
    val clock = UiBenchClock(period)
    clock.onFrame(100)
    val k = clock.onFrame(100 + Math.round(period))
    assertEquals(k, clock.onFrame(100 + Math.round(period)))
  }

  @Test fun neighbouringStatesAlwaysDifferByOneStep() {
    for (k in 0L until 1_000L) {
      assertEquals(2f, Math.abs(uiBenchOffsetPx(k + 1) - uiBenchOffsetPx(k)), 0f)
    }
    assertEquals(0f, uiBenchOffsetPx(0), 0f)
    assertEquals(60f, uiBenchOffsetPx(30), 0f)
    assertEquals(-60f, uiBenchOffsetPx(90), 0f)
    assertEquals(0f, uiBenchOffsetPx(120), 0f)
  }

  @Test fun aTableKeepsRowsInOrderAndCountsWhatDidNotFit() {
    val table = LongTable("t", listOf("a", "b"), capacity = 2)
    table.add(1, 2)
    table.add(3, 4)
    table.add(5, 6)
    assertEquals(2, table.size)
    assertEquals(1, table.overflow)
    val text = StringBuilder().also(table::appendTo).toString()
    assertEquals("# table t 2 1\na,b\n1,2\n3,4\n", text)
  }

  @Test fun faultsFireOnlyAfterTheFirstPeriod() {
    assertFalse(UiBenchFaults.frozen(100))
    assertTrue(UiBenchFaults.frozen(3_000))
    assertTrue(UiBenchFaults.frozen(3_499))
    assertFalse(UiBenchFaults.frozen(3_500))
    assertEquals(-1L, UiBenchFaults.stallPeriod(2_999))
    assertEquals(1L, UiBenchFaults.stallPeriod(3_000))
    assertEquals(UiBenchFault.None, UiBenchFault.fromExtra(null))
    assertEquals(UiBenchFault.GlStall, UiBenchFault.fromExtra("glstall"))
  }

  /** The benchmark must not leave the AGSL effect path on the whole-scene route, nor change shader bodies. */
  @Test fun benchmarkCodeReferencesNoShaderSource() {
    val sources = File("src/main/java/io/github/iammxrn/strangershaders/bench").listFiles().orEmpty()
    assertTrue(sources.isNotEmpty())
    val forbidden = listOf("RuntimeShader", "RenderEffect", "SHADER", "drawMesh", "upsideDownAgsl")
    val hits = sources.flatMap { file -> forbidden.filter { it in file.readText() }.map { "${file.name}: $it" } }
    assertEquals(emptyList<String>(), hits)
  }
}
