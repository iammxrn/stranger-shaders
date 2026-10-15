package io.github.iammxrn.strangershaders.bench

/**
 * Fixed-capacity table of longs, written by one thread and read once after the measurement.
 *
 * Rows are preallocated so recording a frame is a few array stores: no allocation, no logging, no
 * file I/O while the benchmark runs. A row that does not fit is counted in [overflow], never
 * written over an older one.
 */
internal class LongTable(val name: String, val columns: List<String>, val capacity: Int) {
  private val width = columns.size
  private val data = LongArray(capacity * width)

  @Volatile
  var size = 0
    private set

  @Volatile
  var overflow = 0
    private set

  /** False for the overhead check: the workload runs, nothing is stored. */
  @Volatile
  var active = true

  fun add(a: Long, b: Long = 0, c: Long = 0, d: Long = 0, e: Long = 0, f: Long = 0, g: Long = 0, h: Long = 0) {
    if (!active) return
    val row = size
    if (row >= capacity) {
      overflow++
      return
    }
    val base = row * width
    val values = width
    if (values > 0) data[base] = a
    if (values > 1) data[base + 1] = b
    if (values > 2) data[base + 2] = c
    if (values > 3) data[base + 3] = d
    if (values > 4) data[base + 4] = e
    if (values > 5) data[base + 5] = f
    if (values > 6) data[base + 6] = g
    if (values > 7) data[base + 7] = h
    // Publish the row only after its values are stored.
    size = row + 1
  }

  operator fun get(row: Int, column: Int): Long = data[row * width + column]

  fun clear() {
    size = 0
    overflow = 0
  }

  /** `# table name rows overflow`, a header line, then one comma-separated line per row. */
  fun appendTo(out: StringBuilder) {
    val rows = size
    out.append("# table ").append(name).append(' ').append(rows).append(' ').append(overflow).append('\n')
    out.append(columns.joinToString(",")).append('\n')
    for (row in 0 until rows) {
      for (column in 0 until width) {
        if (column > 0) out.append(',')
        out.append(get(row, column))
      }
      out.append('\n')
    }
  }

  init {
    require(width in 1..8) { "1..8 columns" }
  }
}

/**
 * The benchmark's animation clock: which UI state is due at a display frame.
 *
 * State k is scheduled at the k-th display refresh after the first animation frame. The frame
 * callback's time is a vsync timestamp, so k advances by the number of refresh periods since the
 * previous callback. Counting vsyncs between neighbouring frames, instead of dividing the elapsed
 * time since the start, keeps k on the real vsync grid even if the nominal period is slightly off.
 * A late frame therefore skips the states whose refreshes it missed; those states are never drawn.
 */
internal class UiBenchClock(private val periodNanos: Double) {
  var startNanos = 0L
    private set
  private var lastFrameNanos = 0L

  var state = -1L
    private set

  /** Returns the state due at [frameNanos], or the current state for a repeated frame time. */
  fun onFrame(frameNanos: Long): Long {
    if (state < 0) {
      startNanos = frameNanos
      lastFrameNanos = frameNanos
      state = 0
      return state
    }
    val steps = Math.round((frameNanos - lastFrameNanos) / periodNanos)
    if (steps <= 0) return state
    state += steps
    lastFrameNanos = frameNanos
    return state
  }
}

/**
 * Horizontal displacement of the animated card part for state [k], in pixels: a triangle wave of
 * [stepPx] per state between -[amplitudeSteps] * stepPx and +[amplitudeSteps] * stepPx. Neighbouring
 * states always differ by exactly one step, so every state is a visibly different picture.
 */
internal fun uiBenchOffsetPx(k: Long, stepPx: Float = 2f, amplitudeSteps: Int = 30): Float {
  val period = 4L * amplitudeSteps
  val phase = Math.floorMod(k, period)
  // 0 -> 0, rises to +A at period/4, falls to -A at 3/4, back to 0.
  val steps = when {
    phase <= amplitudeSteps -> phase
    phase <= 3L * amplitudeSteps -> 2L * amplitudeSteps - phase
    else -> phase - 4L * amplitudeSteps
  }
  return steps * stepPx
}

/** Validation-only faults. Measured runs use [None]; the dump records which one was active. */
internal enum class UiBenchFault {
  None,

  /** The main thread sleeps [UiBenchFaults.StallMs] inside the animation frame callback. */
  UiStall,

  /** The whole-scene GL thread sleeps [UiBenchFaults.StallMs] inside one draw. */
  GlStall,

  /** The whole-scene UI texture gets no new frame for [UiBenchFaults.FreezeMs] while GL keeps drawing. */
  UiFreeze,
  ;

  companion object {
    fun fromExtra(value: String?): UiBenchFault =
        entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: None
  }
}

/** When each fault fires, as a function of elapsed benchmark time, so it is the same on every route. */
internal object UiBenchFaults {
  const val PeriodMs = 3_000L
  const val StallMs = 50L
  const val FreezeMs = 500L

  /** True during the first [FreezeMs] of every [PeriodMs] after the first period. */
  fun frozen(elapsedMs: Long): Boolean = elapsedMs >= PeriodMs && Math.floorMod(elapsedMs, PeriodMs) < FreezeMs

  /** The index of the stall period [elapsedMs] falls in, from the second period on, else -1. */
  fun stallPeriod(elapsedMs: Long): Long = if (elapsedMs >= PeriodMs) elapsedMs / PeriodMs else -1
}
