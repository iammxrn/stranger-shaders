package io.github.iammxrn.strangershaders.bench

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Launch options of the UI-update benchmark (`--ez uiBench true`). */
internal data class UiBenchConfig(
  val route: String,
  val effect: Boolean,
  val continuous: Boolean,
  val warmupStates: Long = 600,
  val windowStates: Long = 3_600,
  val tailStates: Long = 180,
  val fault: UiBenchFault = UiBenchFault.None,
  val tag: String = "",
  /** False only for the instrumentation-overhead check: same animation, no records kept. */
  val record: Boolean = true,
)

/**
 * Buffered instrumentation of the UI-update benchmark. Off unless [configure] ran.
 *
 * It records what each thread knows and nothing it can only guess:
 * - main thread: the animation state applied at each display frame and the vsync id of that frame,
 *   and every draw of the animated card part (the real UI, not an overlay);
 * - whole-scene route: which state each UI-texture recording and HardwareRenderer submission
 *   carried, and on the GL thread the BufferQueue timestamp of every UI and decoder buffer latched
 *   and which buffers each GL frame sampled;
 * - own-GL video route: the decoder buffer each GL frame sampled.
 *
 * Presentation is not observed here. SurfaceFlinger's per-buffer events and FrameTimeline in a
 * Perfetto trace supply it, and the host joins them with these tables. All times are
 * [System.nanoTime] (CLOCK_MONOTONIC), the clock of Choreographer and of SurfaceTexture timestamps.
 *
 * Nothing is logged or written until the whole run is over; then one text file is written from a
 * background thread.
 */
internal object UiBench {
  @Volatile
  var config: UiBenchConfig? = null
    private set

  val enabled: Boolean get() = config != null

  private const val Capacity = 12_000

  /** One row per animation frame callback (main thread). */
  val frames = LongTable("frames", listOf("k", "frameNanos", "applyNanos"), Capacity)

  /** One row per Choreographer vsync callback (main thread): the frame's vsync id and deadlines. */
  val vsyncs = LongTable("vsyncs", listOf("frameNanos", "vsyncId", "expectedPresentNanos", "deadlineNanos"), Capacity)

  /** One row per draw of the animated card part (main thread). inHost=1: drawn into the UI texture recording. */
  val draws = LongTable("draws", listOf("k", "drawNanos", "frameNanos", "inHost"), Capacity)

  /** Whole-scene: one row per recording of the UI host into its private RenderNode. */
  val uiRecords = LongTable("uiRecords", listOf("k", "nanos", "submittedNow"), Capacity)

  /** Whole-scene: HardwareRenderer.syncAndDraw calls HWUI accepted, with the state they carry. */
  val uiSubmits = LongTable("uiSubmits", listOf("seq", "k", "startNanos", "endNanos", "syncResult"), Capacity)

  /** Whole-scene: HWUI frame-commit callbacks. */
  val uiCommits = LongTable("uiCommits", listOf("seq", "nanos"), Capacity)

  /** GL thread: every buffer taken from a SurfaceTexture. stream 1 = UI, 2 = decoder. */
  val latches = LongTable("latches", listOf("stream", "bufferTimestamp", "latchNanos", "glFrame"), Capacity * 2)

  /**
   * GL thread: every onDrawFrame, each followed by GLSurfaceView's eglSwapBuffers. uiBuffer and
   * videoBuffer are the timestamps of the buffers bound for this frame (0 = none yet). full=0 marks
   * an early return that still swaps.
   */
  val glFrames = LongTable(
      "glFrames",
      listOf("glFrame", "startNanos", "endNanos", "uiBuffer", "videoBuffer", "full"),
      Capacity,
  )

  /** Executions of the application's AGSL effect code, to show which route runs it. */
  val agslGlitchLayers = AtomicLong()
  val agslGradeLayers = AtomicLong()
  val agslSnowDraws = AtomicLong()
  val meshVineDraws = AtomicLong()

  @Volatile var glRenderMode = -1
  @Volatile var dumped = false
    private set

  private val tables = listOf(frames, vsyncs, draws, uiRecords, uiSubmits, uiCommits, latches, glFrames)

  // Main thread state.
  private var clock: UiBenchClock? = null
  private var periodNanos = 0.0
  private var refreshRate = 0f
  private var lastFrameNanos = 0L
  private var lastStallPeriod = -1L

  /** The state most recently drawn by the animated card part; main thread. */
  var lastDrawnK = -1L
    private set

  /** True while the whole-scene host records its children. */
  var inHostRecording = false

  private var glFrameIndex = 0L
  private var submitSeq = 0L

  fun configure(value: UiBenchConfig?) {
    config = value
    tables.forEach {
      it.clear()
      it.active = value?.record ?: true
    }
    listOf(agslGlitchLayers, agslGradeLayers, agslSnowDraws, meshVineDraws).forEach { it.set(0) }
    clock = null
    lastDrawnK = -1
    glFrameIndex = 0
    submitSeq = 0
    lastStallPeriod = -1
    dumped = false
    glRenderMode = -1
  }

  // ---------------------------------------------------------------- main thread: animation

  private val vsyncCallback = object : Choreographer.VsyncCallback {
    override fun onVsync(data: Choreographer.FrameData) {
      if (!enabled || dumped) return
      val timeline = data.preferredFrameTimeline
      vsyncs.add(data.frameTimeNanos, timeline.vsyncId, timeline.expectedPresentationTimeNanos, timeline.deadlineNanos)
      Choreographer.getInstance().postVsyncCallback(this)
    }
  }

  /** Starts the per-frame vsync-id record; call once from the main thread. */
  fun start(displayRefreshRate: Float) {
    if (!enabled || clock != null) return
    refreshRate = displayRefreshRate
    periodNanos = 1e9 / displayRefreshRate
    clock = UiBenchClock(periodNanos)
    if (config?.record != false) Choreographer.getInstance().postVsyncCallback(vsyncCallback)
  }

  /** The animation frame callback; returns the state to draw. */
  fun onFrame(frameNanos: Long, context: Context): Long {
    val clock = clock ?: return 0
    val k = clock.onFrame(frameNanos)
    lastFrameNanos = frameNanos
    frames.add(k, frameNanos, System.nanoTime())
    val config = config ?: return k
    if (config.fault == UiBenchFault.UiStall) {
      val period = UiBenchFaults.stallPeriod(elapsedMs(frameNanos))
      if (period > lastStallPeriod && period >= 0) {
        lastStallPeriod = period
        SystemClock.sleep(UiBenchFaults.StallMs)
      }
    }
    if (!dumped && k >= config.warmupStates + config.windowStates + config.tailStates) dump(context)
    return k
  }

  /** Called by the draw of the animated card part with the state it is drawing. */
  fun onDraw(k: Long) {
    if (!enabled || dumped) return
    lastDrawnK = k
    draws.add(k, System.nanoTime(), lastFrameNanos, if (inHostRecording) 1 else 0)
  }

  fun elapsedMs(nanos: Long): Long {
    val start = clock?.startNanos ?: return -1
    return (nanos - start) / 1_000_000
  }

  // ---------------------------------------------------------------- whole-scene UI texture (main)

  fun onUiRecorded(submittedNow: Boolean) {
    if (!enabled || dumped) return
    uiRecords.add(lastDrawnK, System.nanoTime(), if (submittedNow) 1 else 0)
  }

  /** Validation fault: the UI texture receives no new frame while this is true. */
  fun uiSubmissionFrozen(): Boolean =
      config?.fault == UiBenchFault.UiFreeze && clock != null && UiBenchFaults.frozen(elapsedMs(System.nanoTime()))

  fun nextSubmitSeq(): Long = ++submitSeq

  fun onUiSubmitted(seq: Long, startNanos: Long, endNanos: Long, syncResult: Int) {
    if (!enabled || dumped) return
    uiSubmits.add(seq, lastDrawnK, startNanos, endNanos, syncResult.toLong())
  }

  fun onUiCommitted(seq: Long) {
    if (!enabled || dumped) return
    uiCommits.add(seq, System.nanoTime())
  }

  // ---------------------------------------------------------------- GL thread

  /** Starts a GL frame record; returns its index. */
  fun glFrameStart(): Long = glFrameIndex++

  fun onLatched(stream: Int, bufferTimestamp: Long, glFrame: Long) {
    if (!enabled || dumped) return
    latches.add(stream.toLong(), bufferTimestamp, System.nanoTime(), glFrame)
  }

  fun glFrameEnd(glFrame: Long, startNanos: Long, uiBuffer: Long, videoBuffer: Long, full: Boolean) {
    if (!enabled || dumped) return
    glFrames.add(glFrame, startNanos, System.nanoTime(), uiBuffer, videoBuffer, if (full) 1 else 0)
  }

  private var lastGlStallPeriod = -1L

  /** Validation fault on the whole-scene GL thread. */
  fun maybeStallGl(nowNanos: Long) {
    if (config?.fault != UiBenchFault.GlStall || clock == null) return
    val period = UiBenchFaults.stallPeriod(elapsedMs(nowNanos))
    if (period > lastGlStallPeriod && period >= 0) {
      lastGlStallPeriod = period
      SystemClock.sleep(UiBenchFaults.StallMs)
    }
  }

  // ---------------------------------------------------------------- dump

  private fun dump(context: Context) {
    dumped = true
    val config = config ?: return
    val start = clock?.startNanos ?: 0L
    val period = periodNanos
    val out = StringBuilder(1 shl 20)
    fun meta(key: String, value: Any?) = out.append("meta ").append(key).append('=').append(value).append('\n')
    meta("format", "uibench-1")
    meta("route", config.route)
    meta("effect", config.effect)
    meta("continuous", config.continuous)
    meta("fault", config.fault.name)
    meta("tag", config.tag)
    meta("record", config.record)
    meta("warmupStates", config.warmupStates)
    meta("windowStates", config.windowStates)
    meta("tailStates", config.tailStates)
    meta("startNanos", start)
    meta("nominalPeriodNanos", period)
    meta("displayRefreshRate", refreshRate)
    meta("glRenderMode", glRenderMode)
    meta("agslGlitchLayers", agslGlitchLayers.get())
    meta("agslGradeLayers", agslGradeLayers.get())
    meta("agslSnowDraws", agslSnowDraws.get())
    meta("meshVineDraws", meshVineDraws.get())
    meta("package", context.packageName)
    meta("fingerprint", Build.FINGERPRINT)
    meta("dumpNanos", System.nanoTime())
    meta("elapsedRealtimeNanos", SystemClock.elapsedRealtimeNanos())
    tables.forEach { it.appendTo(out) }
    val dir = context.getExternalFilesDir(null)
    Thread {
      val file = File(dir, "uibench.txt")
      val partial = File(dir, "uibench.txt.part")
      partial.writeText(out.toString())
      partial.renameTo(file)
      Log.i(LogTag, "done ${file.absolutePath} frames=${frames.size} draws=${draws.size} glFrames=${glFrames.size}")
    }.apply { name = "UiBenchDump" }.start()
  }

  const val LogTag = "UiBench"
}

/**
 * The vine cycle of the effect routes. Its pause between cycles is random (2 to 4 s); under the
 * benchmark it is the midpoint, 3 s, so every route and run shows the same vine timeline: a 15 s
 * cycle, four of them in a 60 s window.
 */
internal fun benchVinesCycle(): io.github.iammxrn.strangershaders.effect.mesh.VinesCycle =
    if (UiBench.enabled) io.github.iammxrn.strangershaders.effect.mesh.VinesCycle { 3.0 }
    else io.github.iammxrn.strangershaders.effect.mesh.VinesCycle()
