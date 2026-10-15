# Third-party notices

## Gradle wrapper

The files `android/gradlew`, `android/gradlew.bat` and `android/gradle/wrapper/gradle-wrapper.jar` come from Gradle 8.14.3. Their copyright notices are retained. Gradle is distributed under Apache-2.0; the license text is in `licenses/Gradle-Apache-2.0.txt`.

## Build dependencies

AndroidX, Media3, Kotlin and other build dependencies are declared in the Gradle files. They are resolved from their public repositories and remain subject to the licenses distributed with each component. No dependency caches or third-party application binaries are checked in. Apple frameworks are supplied by the Xcode SDK and the operating system.

## Sample media

The MP4 and PNG test patterns are produced with FFmpeg's `testsrc2` filter by `tools/generate-media.sh`. FFmpeg itself is not bundled. No third-party footage, commercial fonts or production artwork is included.
