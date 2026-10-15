import SwiftUI

struct MetalPlayerProductScreen: View {
  @Environment(GradingModel.self) private var model
  @State private var engine = PlayerEngine(url: MediaAssets.cardVideoURL)

  var body: some View {
    DemoScene {
      MetalVideoPlayerView(engine: engine, model: model, fillsBounds: true)
    }
    .onAppear { engine.play() }
    .onDisappear { engine.pause() }
  }
}

#Preview("Metal Player") {
  NavigationStack {
    MetalPlayerProductScreen()
      .toolbar(.hidden, for: .navigationBar)
  }
  .environment(GradingModel())
}
