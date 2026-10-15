package io.github.iammxrn.strangershaders.player

import androidx.annotation.RawRes
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.iammxrn.strangershaders.R

enum class DemoClip(@RawRes val resource: Int) {
  Sample(R.raw.sample_motion);

  companion object {
    fun fromExtra(name: String?): DemoClip = entries.firstOrNull { it.name.equals(name, true) } ?: Sample
  }
}

val LocalDemoClip = staticCompositionLocalOf { DemoClip.Sample }
