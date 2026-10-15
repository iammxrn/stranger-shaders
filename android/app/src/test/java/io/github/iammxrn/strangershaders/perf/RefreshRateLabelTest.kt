package io.github.iammxrn.strangershaders.perf

import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshRateLabelTest {
  @Test fun panelModeRatesAreRoundedToWholeHertz() {
    assertEquals("120 Hz", refreshRateLabel(appHz = 119.98f, panelHz = 120.0f))
    assertEquals("60 Hz", refreshRateLabel(appHz = 60.0f, panelHz = 60.02f))
  }

  @Test fun anAppFrameRateOverrideNamesThePanelRateToo() {
    assertEquals("60 Hz (panel 120 Hz)", refreshRateLabel(appHz = 60f, panelHz = 120f))
  }

  @Test fun missingRatesAreNotShownAsZero() {
    assertEquals("refresh rate unknown", refreshRateLabel(appHz = 0f, panelHz = 0f))
    assertEquals("refresh rate unknown", refreshRateLabel(appHz = Float.NaN, panelHz = 120f))
    assertEquals("120 Hz", refreshRateLabel(appHz = 120f, panelHz = 0f))
  }
}
