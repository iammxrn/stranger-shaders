package io.github.iammxrn.strangershaders.effect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlin.random.Random
import kotlinx.coroutines.delay
import io.github.iammxrn.strangershaders.teaching.LocalCaptureOptions

/**
 * Every parameter the effect needs, in one immutable snapshot.
 *
 * Both the AGSL modifiers (UI) and the GLSL programs (video) read the same snapshot, so the whole
 * screen glitches as one picture instead of two effects drifting apart.
 */
data class EffectSnapshot(
  val glitchIntensity: Float = 0f,
  val upsideDownAmount: Float = 0f,
  val timeSeconds: Float = 0f,
  val darknessIntensity: Float = DefaultDarkness,
  val realRandom: Float = 0f,
) {
  val isIdle: Boolean get() = glitchIntensity <= 0f && upsideDownAmount <= 0f

  companion object {
    const val DefaultDarkness = 0.4f
  }
}

/**
 * Drives the glitch -> upside-down timeline and publishes it twice: as Compose state for the AGSL
 * modifiers, and as a plain volatile field for GL render threads, which cannot read the Compose
 * snapshot system.
 */
@Stable
class UpsideDownEffectState(initiallyActive: Boolean = false) {

  var isActive: Boolean by mutableStateOf(initiallyActive)
    private set

  /** Read by composables. Writing it triggers recomposition/redraw of the shader layers. */
  var snapshot: EffectSnapshot by mutableStateOf(EffectSnapshot())
    private set

  /** Read by GL render threads. Kept in lockstep with [snapshot]. */
  @Volatile
  var glSnapshot: EffectSnapshot = EffectSnapshot()
    private set

  fun toggle() {
    isActive = !isActive
  }

  internal fun publish(value: EffectSnapshot) {
    snapshot = value
    glSnapshot = value
  }
}

@Composable
fun rememberUpsideDownEffectState(initiallyActive: Boolean = false): UpsideDownEffectState {
  val capture = LocalCaptureOptions.current
  val state = remember { UpsideDownEffectState(initiallyActive) }

  // The capture harness triggers the same state transition as the visible button.
  // It never substitutes frames or bypasses the real effect clock.
  LaunchedEffect(state, capture.transitionAfterMs) {
    if (capture.enabled && capture.transitionAfterMs > 0L) {
      delay(capture.transitionAfterMs)
      if (!state.isActive) state.toggle()
    }
  }

  LaunchedEffect(state.isActive) {
    if (!state.isActive) {
      state.publish(EffectSnapshot())
      return@LaunchedEffect
    }

    if (capture.fixedEffect) {
      state.publish(EffectSnapshot(upsideDownAmount = 1f, timeSeconds = 4f, realRandom = .42f))
      return@LaunchedEffect
    }
    var startNanos = 0L
    while (true) {
      withFrameNanos { frameNanos ->
        if (startNanos == 0L) startNanos = frameNanos
        val elapsedMillis = (frameNanos - startNanos) / NanosPerMilli
        state.publish(snapshotAt(elapsedMillis))
      }
    }
  }

  return state
}

private fun snapshotAt(elapsedMillis: Long): EffectSnapshot {
  return EffectSnapshot(
      glitchIntensity = glitchIntensityAt(elapsedMillis),
      upsideDownAmount = if (elapsedMillis >= UpsideDownStartMillis) 1f else 0f,
      timeSeconds = elapsedMillis / MillisPerSecond,
      realRandom = Random.nextFloat(),
  )
}

/**
 * Two decaying bursts, matching the production timeline in ShaderGlitchModifier minus its 1500 ms
 * lead-in, which only made the live demo look unresponsive.
 */
private fun glitchIntensityAt(elapsedMillis: Long): Float {
  return when {
    elapsedMillis < FirstBurstMillis ->
      1f - elapsedMillis / FirstBurstMillis.toFloat()

    elapsedMillis < SecondBurstStartMillis -> 0f

    elapsedMillis < SecondBurstStartMillis + SecondBurstMillis ->
      1f - (elapsedMillis - SecondBurstStartMillis) / SecondBurstMillis.toFloat()

    else -> 0f
  }
}

private const val FirstBurstMillis = 200L
private const val SecondBurstStartMillis = 500L
private const val SecondBurstMillis = 500L
private const val UpsideDownStartMillis = 1100L
private const val NanosPerMilli = 1_000_000L
private const val MillisPerSecond = 1000f
