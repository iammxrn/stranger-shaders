import QuartzCore
import SwiftUI

/// The glitch is the only layerEffect (it needs sample offsets) and is enabled
/// only while the transition runs. The grade is a colorEffect with no time in
/// its arguments, so a static layer rasterizes once and stays cached. The
/// grade comes after the glitch: luma is taken from the already split RGB
/// channels, as in the original combined shader.
///
/// The modifier owns its clock: the timeline ticks only while the model
/// reports a transition, so outside those 3 seconds nothing under it is
/// re-evaluated.
struct UpsideDownEffects: ViewModifier {
  let model: GradingModel
  /// Screen size in points. Every layer normalizes `position` by it, so the
  /// glitch bands are the same fraction of the screen on each layer.
  let canvasSize: CGSize

  func body(content: Content) -> some View {
    TimelineView(.animation(paused: !model.isTransitioning)) { _ in
      let time = CACurrentMediaTime()

      content
        .layerEffect(
          model.glitchShader(size: canvasSize, time: time),
          maxSampleOffset: maxSampleOffset,
          isEnabled: model.upsideDown.glitchAmount(at: time) > 0
        )
        .colorEffect(
          model.upsideDownGradeShader(size: canvasSize, at: time),
          isEnabled: model.upsideDown.gradeAmount(at: time) > 0
        )
    }
  }

  /// The furthest the glitch samples: a band shift of at most 0.1 of the
  /// width plus 0.008 for the RGB split, and one texel of bilinear filtering.
  /// SwiftUI pads the offscreen layer by this amount, so it stays tight.
  private var maxSampleOffset: CGSize {
    CGSize(width: canvasSize.width * 0.11, height: 0)
  }
}

extension View {
  func upsideDownEffects(model: GradingModel, canvasSize: CGSize) -> some View {
    modifier(UpsideDownEffects(model: model, canvasSize: canvasSize))
  }
}
