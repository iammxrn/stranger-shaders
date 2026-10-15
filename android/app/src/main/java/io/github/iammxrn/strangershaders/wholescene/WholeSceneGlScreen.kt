package io.github.iammxrn.strangershaders.wholescene

import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import io.github.iammxrn.strangershaders.card.DemoCard
import io.github.iammxrn.strangershaders.effect.UpsideDownEffectState
import io.github.iammxrn.strangershaders.bench.benchVinesCycle
import io.github.iammxrn.strangershaders.effect.rememberUpsideDownEffectState
import io.github.iammxrn.strangershaders.player.rememberDemoPlayer
import io.github.iammxrn.strangershaders.player.rememberVideoSize
import io.github.iammxrn.strangershaders.screens.DemoScaffold
import io.github.iammxrn.strangershaders.screens.Experiment
import io.github.iammxrn.strangershaders.teaching.LocalCaptureOptions

/**
 * Experiment: our GL renderer owns the final picture of the whole product screen.
 *
 * The product card is the same composable as on every route, with nothing shaded inside it and no
 * background: video is not a View here. [WholeSceneGl] renders the card into a texture, puts it over
 * the decoder texture and applies grade, glitch, vines and snow in GL. No AGSL effect of this app
 * runs on this route. Play/pause, title and HUD stay outside the effect, as on the five routes.
 */
@Composable
fun WholeSceneGlScreen(
  modifier: Modifier = Modifier,
  startActive: Boolean = false,
  onBack: () -> Unit = {},
) {
  val effect = rememberUpsideDownEffectState(initiallyActive = startActive)
  val player = rememberDemoPlayer()
  val logStats = LocalActivity.current?.intent?.getBooleanExtra(ExtraSceneStats, false) == true

  DisposableEffect(Unit) {
    WholeSceneStats.reset()
    onDispose { if (logStats) Log.i(StatsTag, "dispose " + WholeSceneStats.summary()) }
  }
  if (logStats) LaunchedEffect(Unit) {
    while (true) {
      delay(StatsPeriodMs)
      Log.i(StatsTag, WholeSceneStats.summary())
    }
  }

  DemoScaffold(
      title = Experiment.WholeSceneGl.title,
      modifier = modifier,
      player = player,
      onBack = onBack,
  ) {
    WholeSceneGl(player = player, effect = effect, modifier = Modifier.fillMaxSize()) {
      DemoCard(
          isUpsideDownActive = effect.isActive,
          onUpsideDownClick = effect::toggle,
      )
    }
  }
}

/**
 * Hosts [content] in a [WholeSceneContainer] and feeds the GL renderer its frame parameters.
 *
 * Effect and vine values reach GL through [SceneSnapshotPublisher], never through state read while
 * the UI draws: an advancing effect clock therefore changes uniforms and requests a GL frame, but
 * does not invalidate or re-rasterise an unchanged card.
 */
@Composable
internal fun WholeSceneGl(
  player: ExoPlayer,
  effect: UpsideDownEffectState,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val capture = LocalCaptureOptions.current
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val videoSize = rememberVideoSize(player)
  val parentComposition = rememberCompositionContext()
  val currentContent by rememberUpdatedState(content)
  var container by remember { mutableStateOf<WholeSceneContainer?>(null) }

  AndroidView(
      modifier = modifier,
      factory = { context ->
        WholeSceneContainer(context, player, parentComposition) { currentContent() }
            .also { container = it }
      },
      onReset = null,
      onRelease = { it.release() },
  )

  val scene = container ?: return

  SideEffect { scene.setVideoSize(videoSize.width, videoSize.height) }

  DisposableEffect(lifecycle, scene) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> scene.onResume()
        Lifecycle.Event.ON_PAUSE -> scene.onPause()
        else -> Unit
      }
    }
    lifecycle.addObserver(observer)
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) scene.onResume()
    onDispose {
      lifecycle.removeObserver(observer)
      scene.release()
    }
  }

  // The same EffectSnapshot the AGSL routes read, copied to the GL side whenever it changes.
  LaunchedEffect(scene, effect) {
    snapshotFlow { effect.snapshot }.collect(scene.publisher::publishEffect)
  }

  // Vines follow the mesh route: a fresh cycle each time the scene enters the Upside Down, grown to
  // the base seed in a fixed capture, otherwise animated by display frame time.
  val inUpsideDown by remember(effect) { derivedStateOf { effect.snapshot.upsideDownAmount > 0f } }
  LaunchedEffect(scene, inUpsideDown) {
    if (!inUpsideDown) {
      scene.publisher.publishVines(VinesFrame(progress = 0f))
      return@LaunchedEffect
    }
    if (capture.fixedEffect) {
      scene.publisher.publishVines(VinesFrame(progress = 1f))
      return@LaunchedEffect
    }
    val cycle = benchVinesCycle()
    while (true) {
      withFrameNanos { frameNanos ->
        val progress = cycle.progress(frameNanos / NanosPerSecond)
        scene.publisher.publishVines(VinesFrame(progress, cycle.seed))
      }
    }
  }
}

private const val NanosPerSecond = 1_000_000_000.0
private const val ExtraSceneStats = "sceneStats"
private const val StatsTag = "WholeSceneGl"
private const val StatsPeriodMs = 1000L
