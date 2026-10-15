import SwiftUI

struct StaticCoverProductScreen: View {
  var body: some View {
    DemoScene(mediaOptions: .supportsLayerEffect) {
      Image("sample-cover")
        .resizable()
        .scaledToFill()
    }
  }
}

#Preview("Static Cover") {
  NavigationStack {
    StaticCoverProductScreen()
      .toolbar(.hidden, for: .navigationBar)
  }
  .environment(GradingModel())
}
