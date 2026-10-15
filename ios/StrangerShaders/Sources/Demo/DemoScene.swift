import QuartzCore
import SwiftUI

struct MediaOptions: OptionSet {
  let rawValue: Int
  static let supportsLayerEffect = Self(rawValue: 1 << 0)
}

struct DemoScene<Media: View>: View {
  @Environment(GradingModel.self) private var model
  @Environment(\.dismiss) private var dismiss
  @State private var size: CGSize = .zero
  @State private var insets = EdgeInsets()
  @State private var count = 0

  private let media: Media
  private let mediaOptions: MediaOptions

  init(mediaOptions: MediaOptions = [], @ViewBuilder media: () -> Media) {
    self.mediaOptions = mediaOptions
    self.media = media()
  }

  var body: some View {
    Color.clear
      .overlay { mediaLayer }
      .overlay { controls }
      .overlay {
        TimelineView(.animation(paused: !model.isTransitioning)) { _ in
          if model.upsideDown.gradeAmount(at: CACurrentMediaTime()) > 0 {
            UpsideDownOverlayView().allowsHitTesting(false)
          }
        }
      }
      .onGeometryChange(for: Geometry.self) { proxy in
        Geometry(size: proxy.size, insets: proxy.safeAreaInsets)
      } action: {
        size = $0.size
        insets = $0.insets
      }
      .ignoresSafeArea()
      .navigationBarBackButtonHidden()
  }

  @ViewBuilder
  private var mediaLayer: some View {
    let clipped = Color.clear.overlay { media }.clipped()
    if mediaOptions.contains(.supportsLayerEffect) {
      clipped.upsideDownEffects(model: model, canvasSize: size)
    } else {
      clipped
    }
  }

  private var controls: some View {
    VStack(spacing: 20) {
      HStack {
        action("Back") { dismiss() }
        Spacer()
        action(model.upsideDown.isOn ? "Effect ON" : "Effect OFF") { model.toggleUpsideDown() }
      }
      Text("Shader playground")
        .font(.system(size: 28, weight: .semibold))
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color.black.opacity(0.6))
      Spacer()
      VStack(alignment: .leading, spacing: 16) {
        Text("Same effect. Different video paths.").font(.system(size: 20, weight: .medium))
        Text("Tap the video to pause, then toggle the effect.").font(.system(size: 16))
        action("UI updates: \(count)") { count += 1 }
      }
      .frame(maxWidth: .infinity, alignment: .leading)
      .padding(20)
      .background(Color.black.opacity(0.7))
    }
    .padding(.horizontal, 24)
    .padding(.top, max(insets.top, 40))
    .padding(.bottom, max(insets.bottom, 24))
    .foregroundStyle(.white)
    // The full-screen frame keeps UI and video shader coordinates aligned.
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .upsideDownEffects(model: model, canvasSize: size)
    .allowsHitTesting(true)
  }

  private func action(_ title: String, perform: @escaping () -> Void) -> some View {
    Button(action: perform) {
      Text(title).font(.system(size: 16, weight: .semibold))
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(Color.indigo, in: RoundedRectangle(cornerRadius: 12))
    }
    .buttonStyle(.plain)
  }
}

private nonisolated struct Geometry: Equatable {
  let size: CGSize
  let insets: EdgeInsets
}
