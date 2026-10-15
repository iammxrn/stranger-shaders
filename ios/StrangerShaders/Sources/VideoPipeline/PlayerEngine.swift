import AVFoundation
import CoreVideo

enum MediaAssets {
  nonisolated static var cardVideoURL: URL {
    guard let url = Bundle.main.url(forResource: "SampleMotion", withExtension: "mp4") else {
      preconditionFailure("SampleMotion.mp4 is missing from the app bundle")
    }
    return url
  }
}

/// Owns AVPlayer (timing and audio) and the video output that pulls decoded
/// frames back into the process: the "official door" from the talk.
@MainActor
final class PlayerEngine {
  let player: AVPlayer
  private let item: AVPlayerItem
  private let videoOutput: AVPlayerItemVideoOutput
  private var loopObserver: (any NSObjectProtocol)?

  init(url: URL) {
    try? AVAudioSession.sharedInstance().setCategory(.playback)

    self.item = AVPlayerItem(url: url)
    self.videoOutput = AVPlayerItemVideoOutput(pixelBufferAttributes: [
      kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
      kCVPixelBufferMetalCompatibilityKey as String: true
    ])
    item.add(videoOutput)

    self.player = AVPlayer(playerItem: item)
    player.actionAtItemEnd = .none
    self.loopObserver = NotificationCenter.default.addObserver(
      forName: AVPlayerItem.didPlayToEndTimeNotification,
      object: item,
      queue: .main
    ) { [weak player] _ in
      player?.seek(to: .zero)
    }
  }

  deinit {
    if let loopObserver {
      NotificationCenter.default.removeObserver(loopObserver)
    }
  }

  var isPlaying: Bool {
    player.rate != 0
  }

  func play() {
    player.play()
  }

  func pause() {
    player.pause()
  }

  func togglePlayback() {
    isPlaying ? pause() : play()
  }

  /// The AVVideoComposition route: frames are graded inside AVFoundation and
  /// shown by the system layer, the item itself keeps playing as before.
  func installUpsideDownComposition() async {
    do {
      item.videoComposition = try await UpsideDownVideoCompositor.composition(for: item.asset)
    } catch {
      assertionFailure("Video composition setup failed: \(error)")
    }
  }

  func newPixelBuffer(forHostTime hostTime: CFTimeInterval) -> CVPixelBuffer? {
    let itemTime = videoOutput.itemTime(forHostTime: hostTime)
    guard videoOutput.hasNewPixelBuffer(forItemTime: itemTime) else { return nil }
    return videoOutput.copyPixelBuffer(forItemTime: itemTime, itemTimeForDisplay: nil)
  }
}
