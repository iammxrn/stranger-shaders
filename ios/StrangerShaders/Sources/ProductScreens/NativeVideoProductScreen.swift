import AVKit
import SwiftUI

struct NativeVideoProductScreen: View {
  @State private var engine = PlayerEngine(url: MediaAssets.cardVideoURL)

  var body: some View {
    DemoScene(mediaOptions: .supportsLayerEffect) {
      VideoPlayer(player: engine.player)
        .aspectRatio(contentMode: .fill)
        .allowsHitTesting(false)
    }
    .onAppear { engine.play() }
    .onDisappear { engine.pause() }
  }
}

#Preview("VideoPlayer") {
  NavigationStack {
    NativeVideoProductScreen()
      .toolbar(.hidden, for: .navigationBar)
  }
  .environment(GradingModel())
}
