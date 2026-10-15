import MetalKit
import QuartzCore
import SwiftUI

/// Everything of the Upside Down that lives above the UI, in one Metal layer:
/// the vines of the production overlay and the snow. One render pass per
/// frame, one full-screen surface for the render server to composite. It is
/// a platform view, so it never sits under a shader modifier.
struct UpsideDownOverlayView: UIViewRepresentable {
  func makeCoordinator() -> UpsideDownOverlayRenderer {
    UpsideDownOverlayRenderer()
  }

  func makeUIView(context: Context) -> MTKView {
    let view = MTKView(frame: .zero, device: context.coordinator.device)
    view.isOpaque = false
    view.clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 0)
    view.colorPixelFormat = .bgra8Unorm
    view.preferredFramesPerSecond = 120
    view.isUserInteractionEnabled = false
    view.delegate = context.coordinator
    return view
  }

  func updateUIView(_ uiView: MTKView, context: Context) {}
}

struct VinesUniforms: Equatable {
  var screenSize: SIMD2<Float>
  var progress: Float
  var mainWidth: Float
  var seed: UInt32
}

struct SnowUniforms {
  var size: SIMD2<Float>
  var time: Float
}

final class UpsideDownOverlayRenderer: NSObject, MTKViewDelegate {
  let device: MTLDevice
  private let commandQueue: MTLCommandQueue
  private let centerlinePipeline: MTLComputePipelineState
  private let vinesPipeline: MTLRenderPipelineState
  private let snowPipeline: MTLRenderPipelineState
  private let linesBuffer: MTLBuffer
  private let pointCountsBuffer: MTLBuffer

  private var vines = VinesUniforms(screenSize: .zero, progress: 0, mainWidth: 80, seed: 1337)
  private var cycle = VinesCycle()
  /// Seed and screen size the centerlines in `linesBuffer` were walked for.
  private var walkedSeed: UInt32?
  private var walkedScreenSize: SIMD2<Float> = .zero

  private static let vinesCount = 4
  private static let maxPointsPerVine = 1024

  override init() {
    guard let device = MTLCreateSystemDefaultDevice(),
          let commandQueue = device.makeCommandQueue(),
          let library = device.makeDefaultLibrary(),
          let centerline = library.makeFunction(name: "vinesCenterline"),
          let vinesVertex = library.makeFunction(name: "vinesVertex"),
          let vinesFragment = library.makeFunction(name: "vinesFragment"),
          let quadVertex = library.makeFunction(name: "overlayQuadVertex"),
          let snowFragment = library.makeFunction(name: "snowFragment")
    else {
      preconditionFailure("Metal setup failed")
    }

    // The vines blend with straight alpha, the snow shader hands over
    // premultiplied coverage like its colorEffect predecessor did.
    let vinesDescriptor = Self.blendedPipelineDescriptor(
      vertex: vinesVertex,
      fragment: vinesFragment,
      sourceFactor: .sourceAlpha
    )
    let snowDescriptor = Self.blendedPipelineDescriptor(
      vertex: quadVertex,
      fragment: snowFragment,
      sourceFactor: .one
    )

    guard let centerlinePipeline = try? device.makeComputePipelineState(function: centerline),
          let vinesPipeline = try? device.makeRenderPipelineState(descriptor: vinesDescriptor),
          let snowPipeline = try? device.makeRenderPipelineState(descriptor: snowDescriptor),
          let linesBuffer = device.makeBuffer(
            length: Self.vinesCount * Self.maxPointsPerVine * MemoryLayout<SIMD2<Float>>.stride,
            options: .storageModePrivate
          ),
          let pointCountsBuffer = device.makeBuffer(
            length: Self.vinesCount * MemoryLayout<UInt32>.stride,
            options: .storageModePrivate
          )
    else {
      preconditionFailure("Overlay pipeline setup failed")
    }

    self.device = device
    self.commandQueue = commandQueue
    self.centerlinePipeline = centerlinePipeline
    self.vinesPipeline = vinesPipeline
    self.snowPipeline = snowPipeline
    self.linesBuffer = linesBuffer
    self.pointCountsBuffer = pointCountsBuffer
  }

  private static func blendedPipelineDescriptor(
    vertex: MTLFunction,
    fragment: MTLFunction,
    sourceFactor: MTLBlendFactor
  ) -> MTLRenderPipelineDescriptor {
    let descriptor = MTLRenderPipelineDescriptor()
    descriptor.vertexFunction = vertex
    descriptor.fragmentFunction = fragment
    let attachment = descriptor.colorAttachments[0]
    attachment?.pixelFormat = .bgra8Unorm
    attachment?.isBlendingEnabled = true
    attachment?.rgbBlendOperation = .add
    attachment?.alphaBlendOperation = .add
    attachment?.sourceRGBBlendFactor = sourceFactor
    attachment?.sourceAlphaBlendFactor = sourceFactor
    attachment?.destinationRGBBlendFactor = .oneMinusSourceAlpha
    attachment?.destinationAlphaBlendFactor = .oneMinusSourceAlpha
    return descriptor
  }

  func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {
    vines.screenSize = SIMD2(Float(size.width), Float(size.height))
  }

  func draw(in view: MTKView) {
    guard vines.screenSize.x > 0, vines.screenSize.y > 0,
          let commandBuffer = commandQueue.makeCommandBuffer()
    else { return }

    let time = CACurrentMediaTime()
    vines.progress = cycle.progress(at: time)
    vines.seed = cycle.seed
    var snow = SnowUniforms(size: vines.screenSize, time: Float(time.truncatingRemainder(dividingBy: 3600)))

    if walkedSeed != vines.seed || walkedScreenSize != vines.screenSize {
      walkCenterlines(in: commandBuffer)
    }

    guard let drawable = view.currentDrawable,
          let passDescriptor = view.currentRenderPassDescriptor,
          let encoder = commandBuffer.makeRenderCommandEncoder(descriptor: passDescriptor)
    else { return }

    encoder.setRenderPipelineState(vinesPipeline)
    encoder.setVertexBytes(&vines, length: MemoryLayout<VinesUniforms>.stride, index: 0)
    encoder.setVertexBuffer(linesBuffer, offset: 0, index: 1)
    encoder.setVertexBuffer(pointCountsBuffer, offset: 0, index: 2)
    let verticesPerVine = Self.maxPointsPerVine * 2
    for vine in 0..<Self.vinesCount {
      encoder.drawPrimitives(type: .triangleStrip, vertexStart: vine * verticesPerVine, vertexCount: verticesPerVine)
    }

    encoder.setRenderPipelineState(snowPipeline)
    encoder.setFragmentBytes(&snow, length: MemoryLayout<SnowUniforms>.stride, index: 0)
    encoder.drawPrimitives(type: .triangleStrip, vertexStart: 0, vertexCount: 4)
    encoder.endEncoding()

    commandBuffer.present(drawable)
    commandBuffer.commit()
  }

  /// One threadgroup of one thread per vine: each centerline is a sequential
  /// walk, so there is nothing to spread across threads. Runs when the seed
  /// or the screen size changes, a few times a minute.
  private func walkCenterlines(in commandBuffer: MTLCommandBuffer) {
    guard let encoder = commandBuffer.makeComputeCommandEncoder() else { return }
    encoder.setComputePipelineState(centerlinePipeline)
    encoder.setBytes(&vines, length: MemoryLayout<VinesUniforms>.stride, index: 0)
    encoder.setBuffer(linesBuffer, offset: 0, index: 1)
    encoder.setBuffer(pointCountsBuffer, offset: 0, index: 2)
    encoder.dispatchThreadgroups(
      MTLSize(width: Self.vinesCount, height: 1, depth: 1),
      threadsPerThreadgroup: MTLSize(width: 1, height: 1, depth: 1)
    )
    encoder.endEncoding()
    walkedSeed = vines.seed
    walkedScreenSize = vines.screenSize
  }
}

/// Grow for 6 seconds, retract for 6, pause for 2 to 4 with a new seed, then
/// again. The first growth starts 4 seconds after the overlay appears.
private struct VinesCycle {
  private enum Phase {
    case grow
    case retract
    case delay(until: TimeInterval)
  }

  private let cycleDuration: TimeInterval = 6
  private var phase: Phase?
  private var phaseStart: TimeInterval = 0
  private var completedCycles = 0

  private(set) var seed: UInt32 = 1337

  mutating func progress(at time: TimeInterval) -> Float {
    guard let phase else {
      phase = .delay(until: time + 4)
      return 0
    }

    let elapsed = time - phaseStart
    switch phase {
    case .grow:
      let progress = easeInOutCubic(min(elapsed / cycleDuration, 1))
      if progress >= 1 {
        self.phase = .retract
        phaseStart = time
      }
      return Float(progress)

    case .retract:
      let progress = easeInOutCubic(max(1 - elapsed / cycleDuration, 0))
      if progress <= 0 {
        completedCycles += 1
        seed = UInt32(1337 + completedCycles * 137)
        self.phase = .delay(until: time + .random(in: 2...4))
      }
      return Float(progress)

    case let .delay(until):
      if time >= until {
        self.phase = .grow
        phaseStart = time
      }
      return 0
    }
  }

  private func easeInOutCubic(_ t: Double) -> Double {
    if t < 0.5 {
      return 4 * t * t * t
    }
    let f = 2 * t - 2
    return 1 + f * f * f / 2
  }
}
