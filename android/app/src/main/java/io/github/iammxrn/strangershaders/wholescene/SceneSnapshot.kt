package io.github.iammxrn.strangershaders.wholescene

import io.github.iammxrn.strangershaders.effect.EffectSnapshot
import io.github.iammxrn.strangershaders.effect.mesh.VinesCycle

/** Vine growth for one frame: what the mesh route keeps in Compose state, here a plain value. */
data class VinesFrame(
  val progress: Float = 0f,
  val seed: UInt = VinesCycle.BaseSeed,
)

/**
 * Everything one GL frame needs, published as a single immutable object. The render thread reads
 * the reference once per frame, so the composite, grade, vines and snow passes of that frame all
 * see the same clock and transition values.
 */
data class SceneSnapshot(
  val effect: EffectSnapshot = EffectSnapshot(),
  val vines: VinesFrame = VinesFrame(),
) {
  /** Vines and snow exist only while the scene is in the Upside Down, as on the reference route. */
  val drawsOverlays: Boolean get() = effect.upsideDownAmount > 0f
  val drawsVines: Boolean get() = drawsOverlays && vines.progress > 0f
}

/**
 * Main-thread writer, render-thread reader. A value that does not change publishes nothing and
 * requests nothing, so an idle effect does not keep the GL thread drawing.
 */
internal class SceneSnapshotPublisher(private val onChanged: (reason: Int) -> Unit) {
  @Volatile
  var current: SceneSnapshot = SceneSnapshot()
    private set

  fun publishEffect(effect: EffectSnapshot) {
    if (effect == current.effect) return
    current = current.copy(effect = effect)
    onChanged(RenderReason.Effect)
  }

  fun publishVines(vines: VinesFrame) {
    if (vines == current.vines) return
    current = current.copy(vines = vines)
    // Growth below an inactive effect is invisible; the next effect change draws it.
    if (current.drawsOverlays) onChanged(RenderReason.Vines)
  }
}

/** Why a GL frame was requested. Bits, so one coalesced frame can carry several reasons. */
internal object RenderReason {
  const val Video = 1
  const val Ui = 2
  const val Effect = 4
  const val Vines = 8
  const val Surface = 16
  const val Lifecycle = 32

  val all = listOf(Video, Ui, Effect, Vines, Surface, Lifecycle)

  fun name(reason: Int): String = when (reason) {
    Video -> "video"
    Ui -> "ui"
    Effect -> "effect"
    Vines -> "vines"
    Surface -> "surface"
    Lifecycle -> "lifecycle"
    else -> "unknown"
  }
}
