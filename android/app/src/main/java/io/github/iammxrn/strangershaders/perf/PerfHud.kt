package io.github.iammxrn.strangershaders.perf

import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Display refresh rate and UI frame time p95/p99 since this screen opened.
 *
 * Frame time is FrameMetrics TOTAL_DURATION of every HWUI frame of the window, the same durations
 * behind the Profile HWUI rendering bars and `dumpsys gfxinfo`. It covers the UI only: video that a
 * SurfaceView or GL surface draws outside HWUI is not in it. Late video frames need SurfaceFlinger,
 * which an app may not read; scripts/benchmark.sh reports them.
 */
@Composable
fun PerfHud(modifier: Modifier = Modifier) {
  val view = LocalView.current
  val window = LocalActivity.current?.window
  // Created with the screen, so every demo or teaching screen starts measuring from zero.
  val stats = remember { FrameDurationStats() }
  var text by remember { mutableStateOf("…") }

  if (window != null) DisposableEffect(window, stats) {
    val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
      stats.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
    }
    window.addOnFrameMetricsAvailableListener(listener, metricsHandler)
    onDispose { window.removeOnFrameMetricsAvailableListener(listener) }
  }

  // The panel is adaptive, so the rate is re-read too. Assigning an unchanged string does not
  // invalidate the Text, so an idle screen is not kept drawing by its own HUD.
  LaunchedEffect(view, stats) {
    while (true) {
      val display = view.display
      val rate = refreshRateLabel(
          appHz = display?.refreshRate ?: 0f,
          panelHz = display?.mode?.refreshRate ?: 0f,
      )
      text = "$rate · ${uiFrameLabel(stats.summary())}"
      delay(PollMillis)
    }
  }

  Text(
      text = text,
      color = HudGreen,
      fontSize = 12.sp,
      modifier = modifier
          .background(Color.Black.copy(alpha = ScrimAlpha), RoundedCornerShape(8.dp))
          .padding(horizontal = 8.dp, vertical = 4.dp),
  )
}

/**
 * [appHz] is the rate the app is paced at, which a frame-rate override can lower below the panel's
 * mode rate [panelHz]; both are named when they differ.
 */
internal fun refreshRateLabel(appHz: Float, panelHz: Float): String {
  if (!appHz.isValidRate()) return "refresh rate unknown"
  val app = Math.round(appHz)
  if (!panelHz.isValidRate()) return "$app Hz"
  val panel = Math.round(panelHz)
  return if (app == panel) "$app Hz" else "$app Hz (panel $panel Hz)"
}

private fun Float.isValidRate() = isFinite() && this > 0f

/** FrameMetrics callbacks run here, off the main thread they measure. */
private val metricsHandler by lazy {
  Handler(HandlerThread("FrameMetrics").apply { start() }.looper)
}

private val HudGreen = Color(0xFF7CFC9A)
private const val ScrimAlpha = 0.5f
private const val PollMillis = 500L
