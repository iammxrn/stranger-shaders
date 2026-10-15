# Stranger Shaders

Android and iOS examples from the Mobius talk «Очень странные шейдеры» by Maxim Kachinkin and Roman Mogutnov.

Try the same colour grade, particles and glitch across a still image, native video views and a custom video renderer. Pause the video, change the effect and compare which layers redraw.

## Run

### Android

Open `android/` in Android Studio. Requires JDK 17, Android SDK 36 and an Android 13 or newer device or emulator. Mesh-vine examples require Android 14; older versions use their Canvas fallback where supported.

```sh
cd android
# Set ANDROID_HOME to your SDK, or add sdk.dir to an untracked local.properties.
./gradlew :app:assembleDebug :app:testDebugUnitTest
./gradlew :app:installDebug
```

The home screen contains the image lessons, native SurfaceView and TextureView paths, a Media3 effect, a custom GL renderer, pause-redraw examples and the whole-scene GL experiment. Use the effect button and tap the video to compare playing and paused frames. The app also contains opt-in rendering instrumentation; no measurement data is bundled.

### iOS

Open `ios/StrangerShaders.xcodeproj` in Xcode 27, select the `StrangerShaders` scheme and an iOS 26.4 or newer simulator. For a physical device, select your own signing team.

```sh
xcodebuild -project ios/StrangerShaders.xcodeproj \
  -scheme StrangerShaders -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

The four screens show a static image, SwiftUI `VideoPlayer`, `AVVideoComposition` with a custom compositor, and `AVPlayerItemVideoOutput` feeding a Metal view. In the composition and Metal screens, tap the video to pause it, then toggle the effect.

The native `VideoPlayer` screen deliberately applies a SwiftUI shader to a platform-backed view. The placeholder or missing video is the limitation being demonstrated; its exact appearance depends on the iOS version and build configuration.

## Read the code

| Topic | Android | iOS |
| --- | --- | --- |
| Small shader lessons | `app/src/main/assets/teaching/` | `StrangerShaders/Sources/Teaching/` |
| UI effects | `effect/agsl/` | `Sources/UpsideDown/UpsideDownEffects.swift` |
| Video rendering | `effect/gl/`, `player/` | `Sources/VideoPipeline/` |
| Whole-scene composition | `wholescene/` | UI and video remain separate passes |
| Demonstration UI | `card/DemoCard.kt` | `Sources/Demo/DemoScene.swift` |

Android Kotlin paths are relative to `android/app/src/main/java/io/github/iammxrn/strangershaders/`. iOS `Sources/` paths are relative to `ios/StrangerShaders/`.

## Scope

This public edition uses a small demo panel, system fonts and a locally generated moving test pattern. It does not include the product card, menu data, fonts, branding, original media, capture archives or performance traces shown in the talk. The rendering routes and educational shader implementations are retained. These applications are examples, not a media player framework. No backend or account is needed at runtime; dependency downloads are needed on the first Android build.

The composition example does not request a refresh of a paused frame. That is a property of this implementation, not a blanket limitation of AVVideoComposition. The custom Metal view redraws when the frame, effect, LUT or drawable size changes. Shared uniforms align the effect model; separate UI and video passes do not guarantee an atomic presentation.

Results from the conference recordings do not describe the workload of this simplified UI. No energy or frame-rate ranking is claimed for the public samples. Protected content, HDR and production playback integration are outside their scope.

## Media and dependencies

`tools/generate-media.sh` reproduces the bundled 720 × 1280, 30 fps, six-second test pattern with FFmpeg. The image is its first frame. No external footage, product imagery, custom fonts or LUT presets are bundled. You can replace the bundled files with your own media. The iOS pipeline still supports loading `.cube` LUTs and uses an identity LUT by default.

Build dependencies are declared in `android/` and downloaded from Google Maven, Maven Central and the Gradle Plugin Portal. The iOS sample uses only Apple system frameworks. Third-party components retain their own licenses; the Gradle wrapper is Apache-2.0. See `THIRD-PARTY-NOTICES.md`.

## License

The sample source is available under the MIT license in `LICENSE`. Third-party components retain their own licenses.
