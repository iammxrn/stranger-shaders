package io.github.iammxrn.strangershaders.teaching

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Explicit launch options, never changed by the capture harness through private app state. */
data class CaptureOptions(
    val enabled: Boolean = false,
    val paused: Boolean = false,
    val fixedEffect: Boolean = false,
    val videoPositionMs: Long = 2000L,
    val transitionAfterMs: Long = 0L,
    val sourceBuild: Boolean = false,
)
val LocalCaptureOptions = staticCompositionLocalOf { CaptureOptions() }

internal fun captureTransitionDelayMs(enabled: Boolean, requested: Long): Long =
    if (enabled) requested.coerceIn(0L, 60_000L) else 0L

/**
 * Capture mode hides the system bars, but the hide lands after the first frames and animates the
 * insets. Inset padding would then move the card while a screenrecord is already running, so the
 * captured layout ignores system bars from the first frame: the same geometry the hidden bars end in.
 */
internal fun captureLayoutIgnoresSystemBars(captureEnabled: Boolean): Boolean = captureEnabled

@Composable
fun Modifier.captureAwareStatusBarsPadding(): Modifier =
    if (captureLayoutIgnoresSystemBars(LocalCaptureOptions.current.enabled)) this else statusBarsPadding()

@Composable
fun Modifier.captureAwareNavigationBarsPadding(): Modifier =
    if (captureLayoutIgnoresSystemBars(LocalCaptureOptions.current.enabled)) this else navigationBarsPadding()
