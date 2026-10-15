import QuartzCore
import SwiftUI

nonisolated struct GradingUniforms: Equatable {
  var exposure: Float
  var temperature: Float
  var contrast: Float
  var saturation: Float
  var lutAmount: Float
  var upsideDownAmount: Float
  var glitchAmount: Float
  var time: Float
}

/// The Upside Down toggle with its glitch transition: the glitch builds up
/// from barely visible to full tearing, then the grade snaps to the target
/// state and the glitch dies out in a fraction of a second.
nonisolated struct UpsideDownEffect {
  private(set) var isOn = false
  private(set) var toggledAt: TimeInterval = -100

  let duration: TimeInterval = 3
  private let snapPoint = 0.8

  mutating func toggle() {
    isOn.toggle()
    toggledAt = CACurrentMediaTime()
  }

  func progress(at time: TimeInterval) -> Double {
    min(max((time - toggledAt) / duration, 0), 1)
  }

  func gradeAmount(at time: TimeInterval) -> Double {
    let target: Double = isOn ? 1 : 0
    return progress(at: time) >= snapPoint ? target : 1 - target
  }

  func glitchAmount(at time: TimeInterval) -> Double {
    let progress = progress(at: time)
    guard progress > 0, progress < 1 else { return 0 }
    if progress < snapPoint {
      return pow(progress / snapPoint, 2.2)
    }
    return max(0, 1 - (progress - snapPoint) / 0.12)
  }

  /// Uniforms for the video shaders with neutral grading. Only the glitch bands
  /// read the clock; `time` stays zero otherwise so the redraw-on-change check
  /// in the display link tick stays quiet instead of redrawing every frame.
  func uniforms(at time: TimeInterval) -> GradingUniforms {
    let glitchAmount = Float(glitchAmount(at: time))
    return GradingUniforms(
      exposure: 0,
      temperature: 0,
      contrast: 1,
      saturation: 1,
      lutAmount: 1,
      upsideDownAmount: Float(gradeAmount(at: time)),
      glitchAmount: glitchAmount,
      time: glitchAmount > 0 ? Float(time.truncatingRemainder(dividingBy: 3600)) : 0
    )
  }
}

@Observable
final class GradingModel {
  var exposure: Double = 0
  var temperature: Double = 0
  var contrast: Double = 1
  var saturation: Double = 1
  var lutAmount: Double = 1
  var selectedLUT: LUTPreset?

  private(set) var upsideDown = UpsideDownEffect()

  /// True for the transition's duration after a toggle. The SwiftUI
  /// timelines pause on it, so outside the transition nothing ticks.
  private(set) var isTransitioning = false
  private var transitionTask: Task<Void, Never>?

  let luts: [LUTPreset]

  init() {
    self.luts = LUTPreset.bundled()
  }

  func toggleUpsideDown() {
    upsideDown.toggle()
    isTransitioning = true
    transitionTask?.cancel()
    transitionTask = Task {
      do {
        try await Task.sleep(for: .seconds(upsideDown.duration))
      } catch {
        return
      }
      isTransitioning = false
    }
  }

  func uniforms(at time: TimeInterval) -> GradingUniforms {
    var uniforms = upsideDown.uniforms(at: time)
    uniforms.exposure = Float(exposure)
    uniforms.temperature = Float(temperature)
    uniforms.contrast = Float(contrast)
    uniforms.saturation = Float(saturation)
    uniforms.lutAmount = Float(lutAmount)
    return uniforms
  }

  func upsideDownGradeShader(size: CGSize, at time: TimeInterval) -> Shader {
    ShaderLibrary.upsideDownGrade(
      .float2(size),
      .float(Float(upsideDown.gradeAmount(at: time)))
    )
  }

  func glitchShader(size: CGSize, time: TimeInterval) -> Shader {
    ShaderLibrary.glitchLayer(
      .float2(size),
      .float(Float(time.truncatingRemainder(dividingBy: 3600))),
      .float(Float(upsideDown.glitchAmount(at: time)))
    )
  }
}
