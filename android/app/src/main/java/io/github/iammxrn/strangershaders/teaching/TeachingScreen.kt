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
import androidx.compose.ui.res.painterResource
import io.github.iammxrn.strangershaders.R
import io.github.iammxrn.strangershaders.card.DemoCard
import io.github.iammxrn.strangershaders.effect.EffectSnapshot
import io.github.iammxrn.strangershaders.effect.UpsideDownEffectState
import io.github.iammxrn.strangershaders.effect.agsl.upsideDownAgsl
import io.github.iammxrn.strangershaders.screens.DemoScaffold

/** Kept separate from faithfully copied production shader sources. Alpha stays premultiplied. */
const val IDENTITY_SHADER = """
    uniform shader image;
    half4 main(float2 coord) {
        return image.eval(coord);
    }
"""
const val DIM_SHADER = """
    uniform shader image;
    half4 main(float2 coord) {
        half4 c = image.eval(coord);
        return half4(c.rgb * 0.7, c.a);
    }
"""

const val RED_SHADER = """
    uniform shader image;
    half4 main(float2 coord) {
        half4 c = image.eval(coord);
        return half4(c.r, c.g * 0.15, c.b * 0.15, c.a);
    }
"""

@Composable
private fun Modifier.teachingShader(source: String): Modifier {
    val shader = remember(source) { RuntimeShader(source) }
    val effect = remember(shader) {
        android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "image")
            .asComposeRenderEffect()
    }
    return graphicsLayer { renderEffect = effect }
}

@Composable
fun TeachingScreen(stage: TeachingStage, onBack: () -> Unit) {
    val fixedTime = LocalCaptureOptions.current.fixedEffect
    var elapsed by remember(stage) { mutableFloatStateOf(0f) }
    LaunchedEffect(stage, fixedTime) {
        if (stage == TeachingStage.AnimatedParticles && !fixedTime) {
            var start = 0L
            while (true) withFrameNanos {
                if (start == 0L) start = it
                elapsed = (it - start) / 1_000_000_000f
            }
        }
    }
    val spec = stageSpec(stage, elapsed, fixedTime)
    val state = remember(stage) { UpsideDownEffectState(initiallyActive = spec.grade > 0f) }
    SideEffect {
        state.publish(EffectSnapshot(
            glitchIntensity = spec.glitch,
            upsideDownAmount = spec.grade,
            timeSeconds = spec.time,
            realRandom = .42f,
        ))
    }
    var rootModifier = Modifier.fillMaxSize()
    if (spec.wholeUi) rootModifier = rootModifier.teachingShader(if (stage == TeachingStage.UiRed) RED_SHADER else DIM_SHADER)
    if (spec.grade > 0f) rootModifier = rootModifier.upsideDownAgsl(state, spec.particles, spec.vines)
    DemoScaffold(title = "Android · ${stage.name}", onBack = onBack) {
        Box(rootModifier) {
            DemoCard(
                isUpsideDownActive = spec.grade > 0f,
                background = {
                    Image(
                        painterResource(R.drawable.sample_cover),
                        contentDescription = "Bundled test pattern",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize().then(
                            when (stage) {
                                TeachingStage.Input -> Modifier.teachingShader(IDENTITY_SHADER)
                                TeachingStage.ImageDim -> Modifier.teachingShader(DIM_SHADER)
                                TeachingStage.ImageRed -> Modifier.teachingShader(RED_SHADER)
                                else -> Modifier
                            }
                        ),
                    )
                },
            )
        }
    }
}
