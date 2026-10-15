import AVFoundation
import CoreVideo
import os
import QuartzCore

/// The AVVideoComposition route: the same videoGradeFragment, but inside
/// AVFoundation's composition pipeline. AVFoundation instantiates the compositor
/// itself and asks it for one output frame per `frameDuration` while the item
/// plays, so the effect state arrives through a shared box, and a paused or
/// stalled item keeps its last composed frame whatever the toggle does.
final nonisolated class UpsideDownVideoCompositor: NSObject, AVVideoCompositing {
  static let effect = UpsideDownEffectBox()

  private let renderer = VideoRenderer()
  private let queue = DispatchQueue(label: "io.github.iammxrn.strangershaders.compositor")

  private static let pixelBufferAttributes: [String: any Sendable] = [
    kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
    kCVPixelBufferMetalCompatibilityKey as String: true
  ]

  var sourcePixelBufferAttributes: [String: any Sendable]? {
    Self.pixelBufferAttributes
  }

  var requiredPixelBufferAttributesForRenderContext: [String: any Sendable] {
    Self.pixelBufferAttributes
  }

  func renderContextChanged(_ newRenderContext: AVVideoCompositionRenderContext) {}

  func startRequest(_ request: AVAsynchronousVideoCompositionRequest) {
    queue.async { [renderer] in
      guard let trackID = request.sourceTrackIDs.first,
            let source = request.sourceFrame(byTrackID: trackID.int32Value),
            let destination = request.renderContext.newPixelBuffer()
      else {
        request.finish(with: CompositorError.missingFrame)
        return
      }
      // The output inherits the BT.709 tagging of the decoded frame.
      CVBufferPropagateAttachments(source, destination)
      let uniforms = Self.effect.current.uniforms(at: CACurrentMediaTime())
      renderer.render(source, into: destination, uniforms: uniforms) {
        request.finish(withComposedVideoFrame: destination)
      }
    }
  }

  func cancelAllPendingVideoCompositionRequests() {}

  /// One instruction over the whole clip; `frameDuration` follows the clip's
  /// frame rate, so the compositor runs at the video's pace, not the display's.
  static func composition(for asset: AVAsset) async throws -> AVVideoComposition {
    guard let track = try await asset.loadTracks(withMediaType: .video).first else {
      throw CompositorError.noVideoTrack
    }
    let (naturalSize, transform, frameRate) = try await track.load(.naturalSize, .preferredTransform, .nominalFrameRate)
    let duration = try await asset.load(.duration)
    let renderSize = naturalSize.applying(transform)

    var configuration = AVVideoComposition.Configuration()
    configuration.customVideoCompositorClass = UpsideDownVideoCompositor.self
    configuration.renderSize = CGSize(width: abs(renderSize.width), height: abs(renderSize.height))
    configuration.frameDuration = CMTime(value: 1, timescale: CMTimeScale(max(frameRate.rounded(), 1)))
    configuration.instructions = [
      UpsideDownCompositionInstruction(timeRange: CMTimeRange(start: .zero, duration: duration), trackID: track.trackID)
    ]
    return AVVideoComposition(configuration: configuration)
  }

  enum CompositorError: Error {
    case missingFrame
    case noVideoTrack
  }
}

final nonisolated class UpsideDownCompositionInstruction: NSObject, AVVideoCompositionInstructionProtocol {
  let timeRange: CMTimeRange
  let enablePostProcessing = false
  /// The grade changes with time, so AVFoundation must not reuse a frame it has
  /// already composed for this instruction.
  let containsTweening = true
  nonisolated(unsafe) let requiredSourceTrackIDs: [NSValue]?
  let passthroughTrackID = kCMPersistentTrackID_Invalid

  init(timeRange: CMTimeRange, trackID: CMPersistentTrackID) {
    self.timeRange = timeRange
    self.requiredSourceTrackIDs = [NSNumber(value: trackID)]
  }
}

/// The compositor is created by AVFoundation and rendered off the main thread,
/// so the toggle state reaches it through this lock instead of the model.
final nonisolated class UpsideDownEffectBox: Sendable {
  private let lock = OSAllocatedUnfairLock(initialState: UpsideDownEffect())

  var current: UpsideDownEffect {
    lock.withLock { $0 }
  }

  func update(_ effect: UpsideDownEffect) {
    lock.withLock { $0 = effect }
  }
}
