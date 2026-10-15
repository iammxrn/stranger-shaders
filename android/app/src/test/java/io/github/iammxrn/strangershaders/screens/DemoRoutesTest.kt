package io.github.iammxrn.strangershaders.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoRoutesTest {
  @Test fun fiveOriginalVideoRoutesKeepTheirNamesAndOrder() {
    assertEquals(
        listOf("NoVideoAgsl", "SurfaceViewAgsl", "TextureViewAgsl", "SurfaceViewPipeline", "SurfaceViewRawGl"),
        Demo.entries.map { it.name },
    )
  }

  @Test fun onlyTheNoVideoRoutesRunWithoutAPlayer() {
    assertFalse(Demo.NoVideoAgsl.hasVideo)
    for (demo in Demo.entries) {
      if (demo.name.startsWith("NoVideo")) assertFalse(demo.name, demo.hasVideo) else assertTrue(demo.name, demo.hasVideo)
    }
  }
}
