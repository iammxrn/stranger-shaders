package io.github.iammxrn.strangershaders.wholescene

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.HardwareRenderer
import android.graphics.RenderNode
import android.util.TypedValue
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import io.github.iammxrn.strangershaders.bench.UiBench

/**
 * Lays out, receives input for and exposes semantics of its children like any ViewGroup, but draws
 * them into an offscreen surface instead of the window.
 *
 * In a hardware-accelerated window every child view already records into its own RenderNode, and a
 * parent's draw only places those nodes into its canvas. This host gives the parent's draw a canvas
 * of a private RenderNode instead, so the window's display list holds nothing for the subtree, and a
 * dedicated [HardwareRenderer] renders the private node into [setTarget]'s Surface: the
 * SurfaceTexture of the whole-scene GL renderer. Rasterisation stays on HWUI's RenderThread and the
 * buffer reaches GL through a BufferQueue, never through the CPU.
 *
 * A child that changes only one nested RenderNode normally re-records that node without asking its
 * ancestors to redraw. [onDescendantInvalidated] turns every such change into a redraw of this host,
 * which is the only way to learn that the private node needs a new render.
 */
internal class UiTextureHost(
  context: Context,
  private val onUiSubmitted: () -> Unit = {},
) : FrameLayout(context) {

  private val content = RenderNode("WholeSceneUi")
  private val renderer = HardwareRenderer()
  private var surface: Surface? = null
  private var released = false
  private val gate = UiFrameGate { stream?.isDrained ?: true }
  private var stream: ExternalStream? = null

  init {
    // The window's draw of this host must stay empty: no background, no foreground.
    background = null
    renderer.setName("WholeSceneUi")
    renderer.isOpaque = false
    renderer.setContentRoot(content)
    applyLight()
    WholeSceneStats.liveUiRenderers.incrementAndGet()
  }

  /** Targets the UI stream of the current GL context; null detaches HWUI from the old one. */
  fun setTarget(target: Surface?, targetStream: ExternalStream?) {
    if (released) return
    surface = target
    stream = targetStream
    // HWUI sizes its buffers from the queue's default size: one texel per host pixel.
    if (width > 0 && height > 0) targetStream?.surfaceTexture?.setDefaultBufferSize(width, height)
    renderer.setSurface(target)
    gate.onSurface(target != null)
    invalidate()
  }

  fun setStarted(started: Boolean) {
    if (released) return
    if (started) renderer.start() else renderer.stop()
    gate.onStarted(started)
    if (started) invalidate()
  }

  /** GL latched UI buffers of [latchedStream]; the queue may have room for the newest recording. */
  fun onLatched(latchedStream: ExternalStream) {
    if (released || latchedStream !== stream) return
    submitIfAllowed()
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    content.setPosition(0, 0, w, h)
    stream?.surfaceTexture?.takeIf { w > 0 && h > 0 }?.setDefaultBufferSize(w, h)
    applyLight()
  }

  override fun onDescendantInvalidated(child: View, target: View) {
    super.onDescendantInvalidated(child, target)
    invalidate()
  }

  override fun dispatchDraw(canvas: Canvas) {
    // A software canvas (View.draw into a Bitmap) gets the plain view tree, as any other View.
    if (released || !canvas.isHardwareAccelerated) {
      super.dispatchDraw(canvas)
      return
    }
    val recording = content.beginRecording(width, height)
    UiBench.inHostRecording = true
    try {
      super.dispatchDraw(recording)
    } finally {
      UiBench.inHostRecording = false
      content.endRecording()
    }
    WholeSceneStats.uiRecords.incrementAndGet()
    gate.onRecorded()
    val submitted = submitIfAllowed()
    if (!submitted) WholeSceneStats.uiDeferred.incrementAndGet()
    UiBench.onUiRecorded(submitted)
  }

  private fun submitIfAllowed(): Boolean {
    // Validation-only fault of the UI-update benchmark; false in every other launch.
    if (UiBench.uiSubmissionFrozen()) {
      if (gate.isDirty) postInvalidateOnAnimation()
      return false
    }
    val generation = gate.takeSubmission() ?: return false
    val seq = UiBench.nextSubmitSeq()
    val start = System.nanoTime()
    val result = renderer.createRenderRequest()
        .setWaitForPresent(false)
        .setFrameCommitCallback(context.mainExecutor) {
          if (released) return@setFrameCommitCallback
          WholeSceneStats.uiCommits.incrementAndGet()
          UiBench.onUiCommitted(seq)
          gate.onCommitted(generation)
          submitIfAllowed()
        }
        .syncAndDraw()
    UiBench.onUiSubmitted(seq, start, System.nanoTime(), result)
    when {
      result and HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND != 0 ->
        gate.onSubmitFailed(generation, surfaceLost = true)
      result and (HardwareRenderer.SYNC_CONTEXT_IS_STOPPED or HardwareRenderer.SYNC_FRAME_DROPPED) != 0 -> {
        gate.onSubmitFailed(generation, surfaceLost = false)
        // A dropped frame is retried on the next display frame, not in a loop.
        if (result and HardwareRenderer.SYNC_FRAME_DROPPED != 0) postInvalidateOnAnimation()
      }
      else -> {
        WholeSceneStats.uiSubmits.incrementAndGet()
        onUiSubmitted()
        // Render-thread animations (none on this card) would need further frames.
        if (result and HardwareRenderer.SYNC_REDRAW_REQUESTED != 0) postInvalidateOnAnimation()
      }
    }
    return true
  }

  /** Producer side of the UI stream goes first: HWUI must stop writing before the texture dies. */
  fun release() {
    if (released) return
    released = true
    renderer.setSurface(null)
    renderer.destroy()
    content.discardDisplayList()
    surface = null
    stream = null
    WholeSceneStats.liveUiRenderers.decrementAndGet()
  }

  /**
   * Elevation shadows (the size knob has one) need the light the window's renderer would use. The
   * light geometry is not public API; these are the framework's default lightY, lightZ and
   * lightRadius, positioned for a surface at the window origin. Alphas come from the theme.
   */
  private fun applyLight() {
    if (released) return
    val density = resources.displayMetrics.density
    val displayWidth = resources.displayMetrics.widthPixels
    renderer.setLightSourceGeometry(
        displayWidth / 2f,
        frameworkDimension("light_y", 0f * density),
        frameworkDimension("light_z", 600f * density),
        frameworkDimension("light_radius", 800f * density),
    )
    renderer.setLightSourceAlpha(
        themeFloat(android.R.attr.ambientShadowAlpha, 0.039f),
        themeFloat(android.R.attr.spotShadowAlpha, 0.19f),
    )
  }

  private fun themeFloat(attribute: Int, fallback: Float): Float {
    val value = TypedValue()
    val resolved = context.theme.resolveAttribute(attribute, value, true)
    return if (resolved && value.type == TypedValue.TYPE_FLOAT) value.float else fallback
  }

  private fun frameworkDimension(name: String, fallback: Float): Float {
    val system = Resources.getSystem()
    @Suppress("DiscouragedApi")
    val id = system.getIdentifier(name, "dimen", "android")
    return if (id != 0) system.getDimension(id) else fallback
  }
}
