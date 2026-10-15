import SwiftUI

struct MobiusShowcaseView: View {
  @State private var model = GradingModel()

  var body: some View {
    NavigationStack {
      List(ShowcaseItem.allCases) { item in
        NavigationLink(value: item) {
          Label(item.title, systemImage: item.systemImage)
        }
      }
      .navigationTitle("Mobius")
      .navigationDestination(for: ShowcaseItem.self) { item in
        screen(for: item)
          .toolbar(.hidden, for: .navigationBar)
      }
    }
    .environment(model)
    .preferredColorScheme(.dark)
  }

  @ViewBuilder
  private func screen(for item: ShowcaseItem) -> some View {
    switch item {
    case .staticCover:
      StaticCoverProductScreen()
    case .videoPlayer:
      NativeVideoProductScreen()
    case .videoComposition:
      CompositionVideoProductScreen()
    case .metalPlayer:
      MetalPlayerProductScreen()
    }
  }
}

/// Cover, VideoPlayer, AVVideoComposition, then Metal: each screen shows what
/// the previous one cannot do, so the order is the argument of the talk.
private enum ShowcaseItem: CaseIterable, Identifiable {
  case staticCover
  case videoPlayer
  case videoComposition
  case metalPlayer

  var id: Self {
    self
  }

  var title: String {
    switch self {
    case .staticCover: "1 · Обложка"
    case .videoPlayer: "2 · VideoPlayer"
    case .videoComposition: "3 · AVVideoComposition"
    case .metalPlayer: "4 · Metal"
    }
  }

  var systemImage: String {
    switch self {
    case .staticCover: "photo"
    case .videoPlayer: "play.rectangle"
    case .videoComposition: "film.stack"
    case .metalPlayer: "cpu"
    }
  }
}

#Preview {
  MobiusShowcaseView()
}
