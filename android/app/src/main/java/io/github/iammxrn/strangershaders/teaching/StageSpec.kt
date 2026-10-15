package io.github.iammxrn.strangershaders.teaching

/** Public intent names. Stable names keep the presentation capture harness reproducible. */
enum class TeachingStage { Input, ImageDim, UiDim, Grade, Particles, AnimatedParticles, Glitch, Full, ImageRed, UiRed }

data class StageSpec(
    val dim: Float = 1f,
    val wholeUi: Boolean = false,
    val grade: Float = 0f,
    val particles: Int = 0,
    val time: Float = 0f,
    val glitch: Float = 0f,
    val vines: Boolean = false,
)

fun stageSpec(stage: TeachingStage, elapsedSeconds: Float = 0f, fixedTime: Boolean = false): StageSpec = when (stage) {
    TeachingStage.Input -> StageSpec()
    TeachingStage.ImageDim -> StageSpec(dim = .7f)
    TeachingStage.UiDim -> StageSpec(dim = .7f, wholeUi = true)
    TeachingStage.ImageRed -> StageSpec()
    TeachingStage.UiRed -> StageSpec(wholeUi = true)
    TeachingStage.Grade -> StageSpec(grade = 1f)
    TeachingStage.Particles -> StageSpec(grade = 1f, particles = 16, time = 4f)
    TeachingStage.AnimatedParticles -> StageSpec(grade = 1f, particles = 16, time = 4f + if (fixedTime) 2f else elapsedSeconds.coerceAtLeast(0f))
    TeachingStage.Glitch -> StageSpec(grade = 1f, particles = 16, time = 4f, glitch = .55f)
    TeachingStage.Full -> StageSpec(grade = 1f, particles = 16, time = 4f, vines = true)
}
