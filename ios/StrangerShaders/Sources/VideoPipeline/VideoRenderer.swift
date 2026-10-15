import CoreVideo
import Metal
import QuartzCore

/// Wraps decoded CVPixelBuffers into Metal textures (zero-copy via
/// CVMetalTextureCache) and draws a graded fullscreen quad, into a CAMetalLayer
/// for the Metal screen or into another pixel buffer for the compositor. Each
/// instance owns its command queue and is used from one thread at a time.
final nonisolated class VideoRenderer: @unchecked Sendable {
  let device: MTLDevice
  private let commandQueue: MTLCommandQueue
  private let pipelineState: MTLRenderPipelineState
  private let textureCache: CVMetalTextureCache
  private let identityLUT: MTLTexture

  private var videoTexture: MTLTexture?
  private var videoTextureRef: CVMetalTexture?
  private var lutTexturesByURL: [URL: MTLTexture] = [:]
  private var activeLUT: MTLTexture
  private(set) var activeLUTURL: URL?

  init() {
    guard let device = MTLCreateSystemDefaultDevice(),
          let commandQueue = device.makeCommandQueue(),
          let library = device.makeDefaultLibrary(),
          let vertexFunction = library.makeFunction(name: "videoQuadVertex"),
          let fragmentFunction = library.makeFunction(name: "videoGradeFragment")
    else {
      preconditionFailure("Metal setup failed")
    }

    let pipelineDescriptor = MTLRenderPipelineDescriptor()
    pipelineDescriptor.vertexFunction = vertexFunction
    pipelineDescriptor.fragmentFunction = fragmentFunction
    pipelineDescriptor.colorAttachments[0].pixelFormat = .bgra8Unorm

    var cache: CVMetalTextureCache?
    CVMetalTextureCacheCreate(kCFAllocatorDefault, nil, device, nil, &cache)

    guard let pipelineState = try? device.makeRenderPipelineState(descriptor: pipelineDescriptor),
          let cache,
          let identityLUT = CubeLUT.identity(device: device)
    else {
      preconditionFailure("Metal pipeline setup failed")
    }

    self.device = device
    self.commandQueue = commandQueue
    self.pipelineState = pipelineState
    self.textureCache = cache
    self.identityLUT = identityLUT
    self.activeLUT = identityLUT
  }

  func enqueue(_ pixelBuffer: CVPixelBuffer) {
    guard let textureRef = makeTexture(from: pixelBuffer), let texture = CVMetalTextureGetTexture(textureRef) else { return }
    videoTexture = texture
    videoTextureRef = textureRef
  }

  func selectLUT(url: URL?) {
    activeLUTURL = url
    guard let url else {
      activeLUT = identityLUT
      return
    }
    if let cached = lutTexturesByURL[url] {
      activeLUT = cached
      return
    }
    guard let lut = try? CubeLUT.parse(contentsOf: url),
          let texture = lut.makeTexture(device: device)
    else {
      assertionFailure("Bundled LUT failed to load: \(url.lastPathComponent)")
      activeLUT = identityLUT
      return
    }
    lutTexturesByURL[url] = texture
    activeLUT = texture
  }

  func draw(to layer: CAMetalLayer, uniforms: GradingUniforms, fillsBounds: Bool = false) {
    let drawableSize = layer.drawableSize
    guard drawableSize.width > 0, drawableSize.height > 0,
          let drawable = layer.nextDrawable(),
          let commandBuffer = commandQueue.makeCommandBuffer() else { return }

    encode(into: drawable.texture, uniforms: uniforms, fillsBounds: fillsBounds, commandBuffer: commandBuffer)
    commandBuffer.present(drawable)
    commandBuffer.commit()
  }

  /// The AVVideoComposition route: grade `source` into `destination`, both
  /// IOSurface backed pixel buffers from AVFoundation, and report when the
  /// GPU has finished so the request can be completed.
  func render(
    _ source: CVPixelBuffer,
    into destination: CVPixelBuffer,
    uniforms: GradingUniforms,
    completion: @escaping @Sendable () -> Void
  ) {
    enqueue(source)
    guard let targetRef = makeTexture(from: destination),
          let target = CVMetalTextureGetTexture(targetRef),
          let commandBuffer = commandQueue.makeCommandBuffer()
    else {
      completion()
      return
    }

    encode(into: target, uniforms: uniforms, fillsBounds: false, commandBuffer: commandBuffer)
    nonisolated(unsafe) let inFlightTargetRef = targetRef
    commandBuffer.addCompletedHandler { _ in
      _ = inFlightTargetRef
      completion()
    }
    commandBuffer.commit()
  }

  private func makeTexture(from pixelBuffer: CVPixelBuffer) -> CVMetalTexture? {
    var textureRef: CVMetalTexture?
    CVMetalTextureCacheCreateTextureFromImage(
      kCFAllocatorDefault,
      textureCache,
      pixelBuffer,
      nil,
      .bgra8Unorm,
      CVPixelBufferGetWidth(pixelBuffer),
      CVPixelBufferGetHeight(pixelBuffer),
      0,
      &textureRef
    )
    return textureRef
  }

  private func encode(
    into target: MTLTexture,
    uniforms: GradingUniforms,
    fillsBounds: Bool,
    commandBuffer: MTLCommandBuffer
  ) {
    let targetSize = CGSize(width: target.width, height: target.height)
    let passDescriptor = MTLRenderPassDescriptor()
    passDescriptor.colorAttachments[0].texture = target
    passDescriptor.colorAttachments[0].loadAction = .clear
    passDescriptor.colorAttachments[0].storeAction = .store
    passDescriptor.colorAttachments[0].clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 1)

    guard let encoder = commandBuffer.makeRenderCommandEncoder(descriptor: passDescriptor) else { return }

    if let videoTexture {
      let widthRatio = targetSize.width / CGFloat(videoTexture.width)
      let heightRatio = targetSize.height / CGFloat(videoTexture.height)
      let scale = fillsBounds ? max(widthRatio, heightRatio) : min(widthRatio, heightRatio)
      var fitScale = SIMD2<Float>(
        Float(CGFloat(videoTexture.width) * scale / targetSize.width),
        Float(CGFloat(videoTexture.height) * scale / targetSize.height)
      )
      var uniforms = uniforms
      encoder.setRenderPipelineState(pipelineState)
      encoder.setVertexBytes(&fitScale, length: MemoryLayout<SIMD2<Float>>.size, index: 0)
      encoder.setFragmentBytes(&uniforms, length: MemoryLayout<GradingUniforms>.size, index: 0)
      encoder.setFragmentTexture(videoTexture, index: 0)
      encoder.setFragmentTexture(activeLUT, index: 1)
      encoder.drawPrimitives(type: .triangleStrip, vertexStart: 0, vertexCount: 4)
    }
    encoder.endEncoding()

    // Keep the CVMetalTexture (and its IOSurface) alive until the GPU is done reading.
    nonisolated(unsafe) let inFlightTextureRef = videoTextureRef
    commandBuffer.addCompletedHandler { _ in
      _ = inFlightTextureRef
    }
  }
}
