package io.github.iammxrn.strangershaders.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import io.github.iammxrn.strangershaders.teaching.TeachingStage
import io.github.iammxrn.strangershaders.teaching.LessonStep
import androidx.compose.material3.TextButton
import io.github.iammxrn.strangershaders.pausesync.PauseSyncDemo
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.iammxrn.strangershaders.effect.agsl.isAgslSupported

@Composable
fun HomeScreen(
  modifier: Modifier = Modifier,
  onDemoClick: (Demo) -> Unit = {},
  onStageClick: (TeachingStage) -> Unit = {},
  onLessonClick: (LessonStep) -> Unit = {},
  onPauseSyncClick: (PauseSyncDemo) -> Unit = {},
  onExperimentClick: (Experiment) -> Unit = {},
) {
  Column(
      modifier = modifier
          .fillMaxSize()
          .background(HomeBackground)
          .verticalScroll(rememberScrollState())
          .statusBarsPadding()
          .navigationBarsPadding()
          .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
        text = "Stranger Shaders",
        color = Color.White,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = "One effect, five ways to render it",
        color = Color.White.copy(alpha = SubtitleAlpha),
        fontSize = 14.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    if (!isAgslSupported) {
      Text(
          text = "AGSL needs Android 13 (API 33). Shaders are disabled on this device.",
          color = WarningText,
          fontSize = 13.sp,
          modifier = Modifier
              .fillMaxWidth()
              .background(WarningBackground, RoundedCornerShape(12.dp))
              .padding(12.dp),
      )
    }

    Text("Image exercises", color = Color.White, fontSize = 18.sp)
    TeachingStage.entries.forEach { stage ->
      TextButton(onClick = { onStageClick(stage) }) { Text(stage.name) }
    }
    Text("Cumulative shader lessons", color = Color.White, fontSize = 18.sp)
    LessonStep.entries.forEach { step ->
      TextButton(onClick = { onLessonClick(step) }) { Text(step.id) }
    }
    Text("Video paths", color = Color.White, fontSize = 18.sp)
    Demo.entries.forEach { demo ->
      DemoRow(demo = demo, onClick = { onDemoClick(demo) })
    }
    Text("Pause sync", color = Color.White, fontSize = 18.sp)
    PauseSyncDemo.entries.forEach { demo ->
      TextButton(onClick = { onPauseSyncClick(demo) }) { Text(demo.title) }
    }
    Text("Experiments", color = Color.White, fontSize = 18.sp)
    Experiment.entries.forEach { experiment ->
      TextButton(onClick = { onExperimentClick(experiment) }) { Text(experiment.title) }
    }
  }
}

@Composable
private fun DemoRow(
  demo: Demo,
  modifier: Modifier = Modifier,
  onClick: () -> Unit = {},
) {
  Row(
      modifier = modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(RowBackground)
          .clickable(onClick = onClick)
          .padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
        text = if (demo.verdict == Demo.Verdict.Works) "✅" else "❌",
        fontSize = 18.sp,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
          text = demo.title,
          color = Color.White,
          fontSize = 16.sp,
          fontWeight = FontWeight.Medium,
      )
      Text(
          text = demo.subtitle,
          color = Color.White.copy(alpha = RowSubtitleAlpha),
          fontSize = 13.sp,
      )
    }
  }
}

private val HomeBackground = Color(0xFF0D0B12)
private val RowBackground = Color(0xFF1A1720)
private val WarningText = Color(0xFFFFB4A9)
private val WarningBackground = Color(0xFF3A1512)
private const val SubtitleAlpha = 0.6f
private const val RowSubtitleAlpha = 0.55f
