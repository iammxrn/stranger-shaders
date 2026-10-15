package io.github.iammxrn.strangershaders.teaching

import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import io.github.iammxrn.strangershaders.effect.snow.SNOW_SHADER
import io.github.iammxrn.strangershaders.screens.DemoScaffold

/** Teaching-only isolation of the exact production snow, without a card, video or vines. */
enum class SnowLesson { One, Three;
    companion object {
        fun fromExtra(value: String?): SnowLesson? = entries.firstOrNull { it.name.equals(value, true) }
    }
}

fun snowLessonSource(lesson: SnowLesson): String = when (lesson) {
    SnowLesson.Three -> SNOW_SHADER
    // Keep the first layer and its original weight. Nothing in snowLayer itself changes.
    SnowLesson.One -> SNOW_SHADER.lines().filterNot { it.trimStart().startsWith("flakes +=") }.joinToString("\n")
}

@Composable
fun SnowLessonScreen(lesson: SnowLesson, onBack: () -> Unit) {
    val capture = LocalCaptureOptions.current
    val shader = remember(lesson) { RuntimeShader(snowLessonSource(lesson)) }
    val brush = remember(shader) { ShaderBrush(shader) }
    var time by remember(lesson) { mutableFloatStateOf(4f) }
    LaunchedEffect(lesson, capture.fixedEffect) {
        if (!capture.fixedEffect) {
            var start = 0L
            while (true) withFrameNanos { now ->
                if (start == 0L) start = now
                time = 4f + (now - start) / 1_000_000_000f
            }
        }
    }
    DemoScaffold(title = "Snow: ${lesson.name.lowercase()} layer(s)", onBack = onBack) {
        Canvas(Modifier.fillMaxSize().background(Color.Black)) {
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("time", time)
            drawRect(brush)
        }
    }
}
