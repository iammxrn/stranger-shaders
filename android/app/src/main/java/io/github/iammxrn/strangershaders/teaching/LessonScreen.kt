package io.github.iammxrn.strangershaders.teaching

import android.graphics.RuntimeShader
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import io.github.iammxrn.strangershaders.R
import io.github.iammxrn.strangershaders.card.DemoCard
import io.github.iammxrn.strangershaders.screens.DemoScaffold

enum class LessonStep(val id: String, val animated: Boolean = false) {
    GradeInput("grade-input"), GradeHue("grade-hue"), GradeSaturation("grade-saturation"), GradeDarkness("grade-darkness"),
    ParticlesBackground("particles-background"), ParticlesOne("particles-one"), ParticlesSoft("particles-soft"), ParticlesMany("particles-many"),
    TimeStatic("time-static"), TimeX("time-x", true), TimeXY("time-xy", true),
    GlitchInput("glitch-input"), GlitchStripes("glitch-stripes"), GlitchDisplaced("glitch-displaced"), GlitchSolid("glitch-solid");
}

/** All replay routes read the exact archived teaching sources. Capture builds embed one active source. */
@Composable
fun LessonScreen(step: LessonStep, onBack: () -> Unit) {
    val context = LocalContext.current
    val capture = LocalCaptureOptions.current
    val source = remember(step, capture.sourceBuild) {
        if (capture.sourceBuild) {
            check(GeneratedLessonBuild.id == step.id) { "Capture build does not match the requested lesson" }
            GeneratedLessonBuild.source
        } else context.assets.open("teaching/${step.id}.agsl").bufferedReader().use { it.readText() }
    }
    val shader = remember(source) {
        RuntimeShader(source).also {
            val sha = java.security.MessageDigest.getInstance("SHA-256")
                .digest(source.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
            android.util.Log.i("StrangerLesson", "compiled ${step.id} sourceSha256=$sha sourceBuild=${capture.sourceBuild}")
        }
    }
    var time by remember(step) { mutableFloatStateOf(2f) }
    LaunchedEffect(step, capture.fixedEffect) {
        if (step.animated && !capture.fixedEffect) {
            var start = 0L
            while (true) withFrameNanos {
                if (start == 0L) start = it
                time = 2f + (it - start) / 1_000_000_000f
            }
        }
    }
    DemoScaffold(title = "Android · ${step.id}", onBack = onBack) {
        Box(Modifier.fillMaxSize()) {
            DemoCard(
                background = {
                    Image(painterResource(R.drawable.sample_cover), contentDescription = "Bundled test pattern",
                        contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().graphicsLayer {
                            shader.setFloatUniform("imageSize", size.width, size.height)
                            shader.setFloatUniform("time", time)
                            // RenderEffect captures the shader builder's uniforms. Create it only
                            // after assigning this frame's size/time, as in the original modifiers.
                            renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "image")
                                .asComposeRenderEffect()
                        })
                },
            )
        }
    }
}
