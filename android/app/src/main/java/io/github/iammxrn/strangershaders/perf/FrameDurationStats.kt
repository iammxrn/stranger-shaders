package io.github.iammxrn.strangershaders.perf

/**
 * Every UI frame duration since a screen opened, for nearest-rank percentiles.
 *
 * Fed from the FrameMetrics thread and read by the HUD on the main thread, hence synchronized. At
 * 120 Hz ten minutes are 72 000 longs, small enough to keep them all and stay exact.
 */
internal class FrameDurationStats {

  data class Summary(val frames: Int, val p95Millis: Float, val p99Millis: Float)

  private var durations = LongArray(InitialCapacity)
  private var size = 0

  @Synchronized
  fun add(nanos: Long) {
    if (size == durations.size) durations = durations.copyOf(size * 2)
    durations[size++] = nanos
  }

  @Synchronized
  fun summary(): Summary? {
    if (size == 0) return null
    val sorted = durations.copyOf(size).apply { sort() }
    return Summary(size, sorted.percentileMillis(95), sorted.percentileMillis(99))
  }

  private fun LongArray.percentileMillis(p: Int): Float {
    val rank = Math.ceil(p / 100.0 * size).toInt().coerceIn(1, size)
    return this[rank - 1] / NanosPerMilli
  }

  private companion object {
    const val InitialCapacity = 1024
    const val NanosPerMilli = 1_000_000f
  }
}

internal fun uiFrameLabel(summary: FrameDurationStats.Summary?): String =
    if (summary == null) "UI p95 – · p99 –"
    else "UI p95 %.1f · p99 %.1f ms".format(summary.p95Millis, summary.p99Millis)
