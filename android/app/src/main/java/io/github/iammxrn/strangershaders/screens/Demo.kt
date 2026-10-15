package io.github.iammxrn.strangershaders.screens

/** The demos on the home list, in the order the talk walks through them. */
enum class Demo(
  val title: String,
  val subtitle: String,
  val verdict: Verdict,
  /** False for routes that show a still poster; the device smoke test then skips its motion check. */
  val hasVideo: Boolean = true,
) {
  NoVideoAgsl(
      title = "1. No video + AGSL",
      subtitle = "RuntimeShader to RenderEffect on a graphicsLayer. The baseline.",
      verdict = Verdict.Works,
      hasVideo = false,
  ),
  SurfaceViewAgsl(
      title = "2. SurfaceView + AGSL",
      subtitle = "Same shader, video in a SurfaceView underneath. The failure demo.",
      verdict = Verdict.Broken,
  ),
  TextureViewAgsl(
      title = "3. TextureView + AGSL",
      subtitle = "One word changed: surface_type. The simple path, at a price.",
      verdict = Verdict.Works,
  ),
  SurfaceViewPipeline(
      title = "4. SurfaceView + setVideoEffects",
      subtitle = "Shader inside the media3 video pipeline. The correct path.",
      verdict = Verdict.Works,
  ),
  SurfaceViewRawGl(
      title = "5. SurfaceView + own GL renderer",
      subtitle = "SurfaceTexture, own EGL and render loop. Full control.",
      verdict = Verdict.Works,
  ),
  ;

  enum class Verdict { Works, Broken }
}
