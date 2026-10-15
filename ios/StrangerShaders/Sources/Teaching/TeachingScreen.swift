import SwiftUI

/// The iOS twin of TeachingScreen.kt in the Android sample: the first-shader
/// lesson of the talk wires `redTint` to the whole product card with one
/// modifier. Kept apart from the product screens, whose shaders are the ported
/// production effect.
struct TeachingScreen: View {
  var body: some View {
    let shader = ShaderLibrary.redTint()
    DemoScene {
      Image("sample-cover")
        .resizable()
        .scaledToFill()
    }
    .colorEffect(shader)
  }
}

#Preview("Teaching: red card") {
  NavigationStack {
    TeachingScreen()
      .toolbar(.hidden, for: .navigationBar)
  }
  .environment(GradingModel())
}
