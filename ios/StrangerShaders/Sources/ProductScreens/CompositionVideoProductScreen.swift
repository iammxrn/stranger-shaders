import AVKit
import SwiftUI

/// The system VideoPlayer again, but the item carries an AVVideoComposition
/// with our compositor, so the frames are graded before the system layer shows
/// them. The interface still gets the SwiftUI shaders; the media slot must not,
/// a platform view under a shader modifier is the placeholder of screen 2.
struct CompositionVideoProductScreen: View {
  @Environment(GradingModel.self) private var model
  @State private var engine = PlayerEngine(url: MediaAssets.cardVideoURL)

  var body: some View {
    DemoScene {
      VideoPlayer(player: engine.player)
        .aspectRatio(contentMode: .fill)
        .allowsHitTesting(false)
        .overlay {
          // The pause test of the talk: a tap on the video toggles playback
          // without waking the system player controls.
          Color.clear
            .contentShape(Rectangle())
            .onTapGesture {
              engine.togglePlayback()
            }
        }
    }
    .task {
      UpsideDownVideoCompositor.effect.update(model.upsideDown)
      await engine.installUpsideDownComposition()
      engine.play()
    }
    .onChange(of: model.upsideDown.toggledAt) {
      UpsideDownVideoCompositor.effect.update(model.upsideDown)
    }
    .onDisappear { engine.pause() }
  }
}

#Preview("AVVideoComposition") {
  NavigationStack {
    CompositionVideoProductScreen()
      .toolbar(.hidden, for: .navigationBar)
  }
  .environment(GradingModel())
}
