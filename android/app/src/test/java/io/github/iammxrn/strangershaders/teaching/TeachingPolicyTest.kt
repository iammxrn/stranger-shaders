package io.github.iammxrn.strangershaders.teaching

import org.junit.Assert.*
import org.junit.Test

class TeachingPolicyTest {
    @Test fun redStagesPreserveAttachmentScope() {
        assertFalse(stageSpec(TeachingStage.valueOf("ImageRed")).wholeUi)
        assertTrue(stageSpec(TeachingStage.valueOf("UiRed")).wholeUi)
    }
    @Test fun capturedLayoutIgnoresSystemBarInsetsOnlyInCaptureMode() {
        // Capture mode hides the bars asynchronously; inset padding would shift the card mid-recording.
        assertTrue(captureLayoutIgnoresSystemBars(true))
        assertFalse(captureLayoutIgnoresSystemBars(false))
    }
    @Test fun automaticTransitionRequiresCaptureMode() {
        assertEquals(0L, captureTransitionDelayMs(false, 4000L))
        assertEquals(4000L, captureTransitionDelayMs(true, 4000L))
        assertEquals(0L, captureTransitionDelayMs(true, -10L))
    }
}
