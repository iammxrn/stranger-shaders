package io.github.iammxrn.strangershaders.pausesync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/** One sweep of the line across the screen. */
internal const val SweepPeriodMs = 2_000L

/** The screen plays the clip for [PlayMs], pauses it for [PauseMs], and repeats. */
internal const val PlayMs = 2_000L
internal const val PauseMs = 1_000L

/** Horizontal position of the line, 0 at the left edge and 1 at the right, from one shared clock. */
internal fun sweepLineX(elapsedMs: Long): Float =
    (elapsedMs % SweepPeriodMs).toFloat() / SweepPeriodMs

internal fun isPausedAt(elapsedMs: Long): Boolean =
    elapsedMs % (PlayMs + PauseMs) >= PlayMs

/**
 * The one clock both shaders read. The UI reads [lineX] as Compose state; the video shader runs on
 * Media3's GL thread and reads [videoLineX] when it draws. Publishing a value does not make either
 * side draw: the UI redraws because the state changed, the video only when Media3 processes a frame.
 */
internal class SweepClock {
  var lineX by mutableFloatStateOf(0f)
    private set

  @Volatile
  var videoLineX: Float = 0f
    private set

  fun publish(elapsedMs: Long) {
    val x = sweepLineX(elapsedMs)
    lineX = x
    videoLineX = x
  }
}
