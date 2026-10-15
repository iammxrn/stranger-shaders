package io.github.iammxrn.strangershaders

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.iammxrn.strangershaders.teaching.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.iammxrn.strangershaders.bench.LocalUiBench
import io.github.iammxrn.strangershaders.bench.UiBench
import io.github.iammxrn.strangershaders.bench.UiBenchConfig
import io.github.iammxrn.strangershaders.bench.UiBenchFault
import io.github.iammxrn.strangershaders.bench.UiBenchMotion
import io.github.iammxrn.strangershaders.pausesync.PauseSyncDemo
import io.github.iammxrn.strangershaders.pausesync.PauseSyncScreen
import io.github.iammxrn.strangershaders.player.DemoClip
import io.github.iammxrn.strangershaders.player.LocalDemoClip
import io.github.iammxrn.strangershaders.screens.Demo
import io.github.iammxrn.strangershaders.screens.Experiment
import io.github.iammxrn.strangershaders.screens.HomeScreen
import io.github.iammxrn.strangershaders.screens.NoVideoAgslScreen
import io.github.iammxrn.strangershaders.screens.PipelineDemoScreen
import io.github.iammxrn.strangershaders.screens.RawGlDemoScreen
import io.github.iammxrn.strangershaders.screens.SurfaceViewAgslScreen
import io.github.iammxrn.strangershaders.screens.TextureViewAgslScreen
import io.github.iammxrn.strangershaders.wholescene.WholeSceneGlScreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme

/**
 * A demo can be opened straight from adb, which is what makes the measurement script deterministic
 * on any screen size:
 *
 * ```
 * adb shell am start -n io.github.iammxrn.strangershaders/.MainActivity \
 *     --es demo SurfaceViewPipeline --ez effect true
 * ```
 *
 * Experiments are separate from the five routes: `--es experiment WholeSceneGl`.
 */
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val capture = CaptureOptions(
        enabled = intent.getBooleanExtra("capture", false),
        paused = intent.getBooleanExtra("paused", false),
        fixedEffect = intent.getBooleanExtra("fixed", false),
        videoPositionMs = intent.getLongExtra("positionMs", 2000L),
        transitionAfterMs = captureTransitionDelayMs(intent.getBooleanExtra("capture", false), intent.getLongExtra("transitionAfterMs", 0L)),
        sourceBuild = intent.getBooleanExtra("sourceBuild", false),
    )
    if (capture.enabled) {
      // The hide is asynchronous; captured layouts therefore ignore system-bar insets, see CaptureOptions.kt.
      WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
      }
    }
    // `--ez uiBench true`: the UI-update benchmark's continuous card animation and its buffered
    // instrumentation, see bench/UiBench.kt. Off by default; every other launch is unchanged.
    val benchMotion = if (intent.getBooleanExtra(EXTRA_UI_BENCH, false)) {
      UiBench.configure(
          UiBenchConfig(
              route = intent.getStringExtra(EXTRA_EXPERIMENT) ?: intent.getStringExtra(EXTRA_DEMO) ?: "none",
              effect = intent.getBooleanExtra(EXTRA_EFFECT, false),
              continuous = intent.getBooleanExtra(EXTRA_CONTINUOUS, true),
              fault = UiBenchFault.fromExtra(intent.getStringExtra(EXTRA_UI_BENCH_FAULT)),
              tag = intent.getStringExtra(EXTRA_UI_BENCH_TAG).orEmpty(),
              warmupStates = intent.getLongExtra(EXTRA_UI_BENCH_WARMUP, 600L).coerceIn(0L, 36_000L),
              windowStates = intent.getLongExtra(EXTRA_UI_BENCH_WINDOW, 3_600L).coerceIn(60L, 36_000L),
              record = intent.getBooleanExtra(EXTRA_UI_BENCH_RECORD, true),
          )
      )
      UiBenchMotion()
    } else {
      null
    }
    setContent {
      CompositionLocalProvider(
          LocalUiBench provides benchMotion,
          LocalCaptureOptions provides capture,
          LocalDemoClip provides DemoClip.fromExtra(intent.getStringExtra(EXTRA_CLIP)),
      ) {
      MaterialTheme(colorScheme = darkColorScheme()) {
        SampleApp(
            initialSnowLesson = SnowLesson.fromExtra(intent.getStringExtra("snowLesson")),
            initialLesson = LessonStep.entries.firstOrNull { it.id.equals(intent.getStringExtra("lesson"), true) },
            initialStage = TeachingStage.entries.firstOrNull { it.name.equals(intent.getStringExtra("stage"), true) },
            initialDemo = intent.demoExtra(),
            initialPauseSync = PauseSyncDemo.fromExtra(intent.getStringExtra(EXTRA_PAUSE_SYNC)),
            initialExperiment = Experiment.fromExtra(intent.getStringExtra(EXTRA_EXPERIMENT)),
            startActive = intent.getBooleanExtra(EXTRA_EFFECT, false),
            renderContinuously = intent.getBooleanExtra(EXTRA_CONTINUOUS, true),
        )
      }
      }
    }
  }
}

private fun Intent?.demoExtra(): Demo? {
  val name = this?.getStringExtra(EXTRA_DEMO) ?: return null
  return Demo.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

@Composable
private fun SampleApp(
  initialSnowLesson: SnowLesson? = null,
  initialLesson: LessonStep? = null,
  initialStage: TeachingStage? = null,
  initialDemo: Demo? = null,
  initialPauseSync: PauseSyncDemo? = null,
  initialExperiment: Experiment? = null,
  startActive: Boolean = false,
  renderContinuously: Boolean = true,
) {
  var lessonName by rememberSaveable { mutableStateOf(initialLesson?.id) }
  val lesson = LessonStep.entries.firstOrNull { it.id == lessonName }
  var currentName by rememberSaveable { mutableStateOf(initialDemo?.name) }
  var stageName by rememberSaveable { mutableStateOf(initialStage?.name) }
  val current = Demo.entries.firstOrNull { it.name == currentName }
  val stage = TeachingStage.entries.firstOrNull { it.name == stageName }
  var pauseSyncName by rememberSaveable { mutableStateOf(initialPauseSync?.name) }
  val pauseSync = PauseSyncDemo.fromExtra(pauseSyncName)
  var snowLessonName by rememberSaveable { mutableStateOf(initialSnowLesson?.name) }
  val snowLesson = SnowLesson.fromExtra(snowLessonName)
  var experimentName by rememberSaveable { mutableStateOf(initialExperiment?.name) }
  val experiment = Experiment.fromExtra(experimentName)
  val goHome = { currentName = null; stageName = null; lessonName = null; pauseSyncName = null; snowLessonName = null; experimentName = null }

  BackHandler(enabled = current != null || stage != null || lesson != null || pauseSync != null || snowLesson != null || experiment != null, onBack = goHome)

  when (experiment) {
    Experiment.WholeSceneGl -> {
      WholeSceneGlScreen(startActive = startActive, onBack = goHome)
      return
    }
    null -> Unit
  }

  if (snowLesson != null) {
    SnowLessonScreen(lesson = snowLesson, onBack = goHome)
    return
  }

  if (pauseSync != null) {
    PauseSyncScreen(demo = pauseSync)
    return
  }

  if (lesson != null) {
    LessonScreen(step = lesson, onBack = goHome)
    return
  }
  if (stage != null) {
    TeachingScreen(stage = stage, onBack = goHome)
    return
  }
  when (current) {
    null -> HomeScreen(
        onLessonClick = { lessonName = it.id },
        onDemoClick = { currentName = it.name },
        onStageClick = { stageName = it.name },
        onPauseSyncClick = { pauseSyncName = it.name },
        onExperimentClick = { experimentName = it.name },
    )
    Demo.NoVideoAgsl -> NoVideoAgslScreen(startActive = startActive, onBack = goHome)
    Demo.SurfaceViewAgsl -> SurfaceViewAgslScreen(startActive = startActive, onBack = goHome)
    Demo.TextureViewAgsl -> TextureViewAgslScreen(startActive = startActive, onBack = goHome)
    Demo.SurfaceViewPipeline -> PipelineDemoScreen(startActive = startActive, onBack = goHome)
    Demo.SurfaceViewRawGl -> RawGlDemoScreen(
        startActive = startActive,
        renderContinuously = renderContinuously,
        onBack = goHome,
    )
  }
}

private const val EXTRA_DEMO = "demo"
private const val EXTRA_EFFECT = "effect"
private const val EXTRA_CONTINUOUS = "continuous"
private const val EXTRA_CLIP = "clip"
private const val EXTRA_UI_BENCH = "uiBench"
private const val EXTRA_UI_BENCH_FAULT = "uiBenchFault"
private const val EXTRA_UI_BENCH_TAG = "uiBenchTag"
private const val EXTRA_UI_BENCH_WARMUP = "uiBenchWarmupStates"
private const val EXTRA_UI_BENCH_WINDOW = "uiBenchWindowStates"
private const val EXTRA_UI_BENCH_RECORD = "uiBenchRecord"
private const val EXTRA_PAUSE_SYNC = "pauseSync"
private const val EXTRA_EXPERIMENT = "experiment"
