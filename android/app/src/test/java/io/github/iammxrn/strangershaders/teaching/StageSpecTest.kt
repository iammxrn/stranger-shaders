package io.github.iammxrn.strangershaders.teaching

import org.junit.Assert.*
import org.junit.Test

class StageSpecTest {
    @Test fun fixedCaptureOverridesAnimationElapsedTime() {
        assertEquals(6f, stageSpec(TeachingStage.AnimatedParticles, 90f, fixedTime = true).time)
        assertEquals(stageSpec(TeachingStage.AnimatedParticles, 0f, fixedTime = true),
            stageSpec(TeachingStage.AnimatedParticles, 90f, fixedTime = true))
    }

    @Test fun dimmingPreservesStageScope() {
        assertEquals(0.7f, stageSpec(TeachingStage.ImageDim).dim)
        assertFalse(stageSpec(TeachingStage.ImageDim).wholeUi)
        assertTrue(stageSpec(TeachingStage.UiDim).wholeUi)
    }
    @Test fun fixedParticlesIgnoreElapsedTime() {
        val first = stageSpec(TeachingStage.Particles, 0f)
        assertEquals(16, first.particles)
        assertEquals(4f, first.time)
        assertEquals(first, stageSpec(TeachingStage.Particles, 40f))
    }
    @Test fun animatedParticlesAdvanceOnlyTime() {
        val start = stageSpec(TeachingStage.AnimatedParticles, 0f)
        val later = stageSpec(TeachingStage.AnimatedParticles, 2f)
        assertEquals(4f, start.time)
        assertEquals(6f, later.time)
        assertEquals(start.copy(time = later.time), later)
    }
    @Test fun gradeCannotAccidentallyEnableParticlesOrGlitch() {
        val spec = stageSpec(TeachingStage.Grade)
        assertEquals(1f, spec.grade)
        assertEquals(0, spec.particles)
        assertEquals(0f, spec.glitch)
    }
    @Test fun fullIncludesAllEffectComponents() {
        assertTrue(stageSpec(TeachingStage.Full).vines)
        assertEquals(16, stageSpec(TeachingStage.Full).particles)
        assertTrue(stageSpec(TeachingStage.Glitch).glitch > 0f)
    }
}
