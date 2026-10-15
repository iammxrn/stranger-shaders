import QuartzCore
import SwiftUI
import UIKit

final class MetalLayerView: UIView {
  override class var layerClass: AnyClass {
    CAMetalLayer.self
  }

  var metalLayer: CAMetalLayer {
    layer as! CAMetalLayer
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    let scale = window?.screen.scale ?? traitCollection.displayScale
    metalLayer.contentsScale = scale
    metalLayer.drawableSize = CGSize(width: bounds.width * scale, height: bounds.height * scale)
  }
}

struct MetalVideoPlayerView: UIViewRepresentable {
  let engine: PlayerEngine
  let model: GradingModel
  var fillsBounds = false

  func makeCoordinator() -> Coordinator {
    Coordinator(engine: engine, model: model, fillsBounds: fillsBounds)
  }

  func makeUIView(context: Context) -> MetalLayerView {
    let view = MetalLayerView()
    view.metalLayer.device = context.coordinator.renderer.device
    view.metalLayer.pixelFormat = .bgra8Unorm
    view.metalLayer.framebufferOnly = true
    // The BGRA frames from AVPlayerItemVideoOutput keep the video's BT.709
    // encoding; without an explicit colorspace CA treats them as display-native
    // (P3), which blows out highlights and oversaturates.
    view.metalLayer.colorspace = CGColorSpace(name: CGColorSpace.itur_709)
    context.coordinator.attach(to: view)
    return view
  }

  func updateUIView(_ uiView: MetalLayerView, context: Context) {}

  static func dismantleUIView(_ uiView: MetalLayerView, coordinator: Coordinator) {
    coordinator.detach()
  }

  @MainActor
  final class Coordinator: NSObject {
    let renderer = VideoRenderer()
    private let engine: PlayerEngine
    private let model: GradingModel
    private let fillsBounds: Bool
    private weak var view: MetalLayerView?
    private var displayLink: CADisplayLink?
    private var lastDrawnUniforms: GradingUniforms?
    private var lastDrawnDrawableSize: CGSize = .zero

    init(engine: PlayerEngine, model: GradingModel, fillsBounds: Bool) {
      self.engine = engine
      self.model = model
      self.fillsBounds = fillsBounds
    }

    func attach(to view: MetalLayerView) {
      self.view = view
      let link = CADisplayLink(target: self, selector: #selector(tick(_:)))
      link.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 120, preferred: 120)
      link.add(to: .main, forMode: .common)
      displayLink = link
    }

    func detach() {
      displayLink?.invalidate()
      displayLink = nil
    }

    @objc private func tick(_ link: CADisplayLink) {
      guard let view else { return }
      var needsRedraw = false

      if let pixelBuffer = engine.newPixelBuffer(forHostTime: link.targetTimestamp) {
        renderer.enqueue(pixelBuffer)
        needsRedraw = true
      }
      if model.selectedLUT?.url != renderer.activeLUTURL {
        renderer.selectLUT(url: model.selectedLUT?.url)
        needsRedraw = true
      }
      let uniforms = model.uniforms(at: CACurrentMediaTime())
      if uniforms != lastDrawnUniforms || view.metalLayer.drawableSize != lastDrawnDrawableSize {
        needsRedraw = true
      }
      guard needsRedraw else { return }

      renderer.draw(to: view.metalLayer, uniforms: uniforms, fillsBounds: fillsBounds)
      lastDrawnUniforms = uniforms
      lastDrawnDrawableSize = view.metalLayer.drawableSize
    }
  }
}
