package io.github.iammxrn.strangershaders.bench

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * The benchmark's continuous UI animation. Null (the default) leaves the card untouched.
 *
 * One state per display refresh: the frame callback picks the state due at that vsync
 * ([UiBenchClock]) and the card's bottom chrome is drawn displaced by [uiBenchOffsetPx] of it. The
 * state is read in the draw phase only, so each change re-records the card's drawing, exactly the
 * work a real moving UI causes, and the draw itself reports which state it drew.
 */
val LocalUiBench = staticCompositionLocalOf<UiBenchMotion?> { null }

@Stable
class UiBenchMotion {
  var state by mutableLongStateOf(0L)
    internal set
}

/** Runs the animation clock for as long as the card is on screen. One per screen. */
@Composable
fun UiBenchDriver(motion: UiBenchMotion) {
  val context = LocalContext.current
  val view = LocalView.current
  LaunchedEffect(motion) {
    UiBench.start(view.display?.refreshRate ?: 60f)
    while (true) {
      withFrameNanos { frameNanos -> motion.state = UiBench.onFrame(frameNanos, context) }
    }
  }
}

/** Draws the content displaced by the current state and records that state as drawn. */
fun Modifier.uiBenchMotion(motion: UiBenchMotion?): Modifier {
  if (motion == null) return this
  return drawWithContent {
    val k = motion.state
    UiBench.onDraw(k)
    translate(left = uiBenchOffsetPx(k)) { this@drawWithContent.drawContent() }
  }
}
