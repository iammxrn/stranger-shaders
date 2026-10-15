package io.github.iammxrn.strangershaders.wholescene

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import io.github.iammxrn.strangershaders.bench.UiBench
import io.github.iammxrn.strangershaders.effect.EffectSnapshot
import io.github.iammxrn.strangershaders.effect.gl.UPSIDE_DOWN_FRAGMENT_SHADER_2D
import io.github.iammxrn.strangershaders.effect.gl.UPSIDE_DOWN_VERTEX_SHADER
import io.github.iammxrn.strangershaders.effect.mesh.MainWidthPx
import io.github.iammxrn.strangershaders.effect.mesh.VineVertexStrideBytes
import io.github.iammxrn.strangershaders.effect.mesh.VinesCount
import io.github.iammxrn.strangershaders.effect.mesh.packVineVertices
import io.github.iammxrn.strangershaders.effect.mesh.walkVineCenterline

/**
 * One external-texture stream (decoder or UI) and how far GL has consumed it.
 *
 * [available] counts SurfaceTexture frame-available callbacks, [latched] the buffers GL has taken
 * with updateTexImage. Equal values mean nothing is waiting in the BufferQueue.
 */
internal class ExternalStream(val surfaceTexture: SurfaceTexture) {
  val available = AtomicLong()
  val latched = AtomicLong()

  val isDrained: Boolean get() = latched.get() >= available.get()

  /** GL thread. Takes every queued buffer, so the texture holds the newest. Returns how many. */
  fun latchAll(onBuffer: ((timestamp: Long) -> Unit)? = null): Long {
    val target = available.get()
    var taken = 0L
    while (latched.get() + taken < target) {
      surfaceTexture.updateTexImage()
      onBuffer?.invoke(surfaceTexture.timestamp)
      taken++
    }
    if (taken > 0) latched.addAndGet(taken)
    return taken
  }
}

/**
 * The whole-scene renderer: our GL thread owns the final picture of the product screen.
 *
 * Per frame, all from one [SceneSnapshot]:
 *  1. Composite: decoder texture (centre crop) under the UI texture, premultiplied.
 *  2. Grade and glitch: the unchanged Media3-route GLSL over the composite. Skipped while idle.
 *  3. Vines: the mesh-route geometry, ported to a GL triangle strip, drawn over the graded scene.
 *  4. Snow: the grid snow, ported to GLSL, drawn last.
 *
 * This is the layer order of the TextureView + AGSL reference: glitch(grade(video + UI)), then
 * vines, then snow. Vines and snow are not graded or glitched there either.
 *
 * @param mainHandler receives frame-available callbacks; every hand-off to the player, the UI host
 * and the scheduler happens on the main thread.
 */
internal class WholeSceneRenderer(
  private val snapshots: () -> SceneSnapshot,
  private val mainHandler: Handler,
  private val onVideoStream: (ExternalStream) -> Unit,
  private val onUiStream: (ExternalStream) -> Unit,
  private val onVideoFrameAvailable: () -> Unit,
  private val onUiFrameAvailable: () -> Unit,
  private val onUiLatched: (ExternalStream) -> Unit,
) : GLSurfaceView.Renderer {

  @Volatile var videoWidth = 0
  @Volatile var videoHeight = 0
  @Volatile private var stopped = false

  private var video: ExternalStream? = null
  private var ui: ExternalStream? = null
  private var hasVideoFrame = false
  private var hasUiFrame = false

  private var quadBuffer = 0
  private var videoTexture = 0
  private var uiTexture = 0
  private var sceneTexture = 0
  private var sceneFramebuffer = 0
  private val composite = CompositeProgram()
  private val grade = GradeProgram()
  private val snow = SnowProgram()
  private val vines = VinesProgram()

  private val videoMatrix = FloatArray(16)
  private val uiMatrix = FloatArray(16)
  private var width = 1
  private var height = 1

  @Synchronized
  override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
    if (stopped) return
    // A new EGL context: the names of the old one are gone with it, the CPU-side objects are not.
    releaseStreams()
    forgetGlNames()

    quadBuffer = createQuadBuffer()
    videoTexture = createExternalTexture(GLES20.GL_LINEAR)
    // One UI texel per screen pixel; nearest keeps text and edges exactly as HWUI rasterised them.
    uiTexture = createExternalTexture(GLES20.GL_NEAREST)
    composite.link()
    grade.link()
    snow.link()
    vines.link()

    video = openStream(videoTexture, onVideoFrameAvailable).also(onVideoStream)
    ui = openStream(uiTexture, onUiFrameAvailable).also(onUiStream)
  }

  override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
    if (stopped) return
    this.width = maxOf(width, 1)
    this.height = maxOf(height, 1)
    GLES20.glViewport(0, 0, this.width, this.height)
    createSceneTarget(this.width, this.height)
  }

  @Synchronized
  override fun onDrawFrame(unused: GL10?) {
    // GLSurfaceView swaps after every onDrawFrame, early returns included, so each call is a frame.
    val benchFrame = UiBench.glFrameStart()
    val benchStart = System.nanoTime()
    if (stopped || video == null || ui == null) {
      UiBench.glFrameEnd(benchFrame, benchStart, uiBufferTimestamp, videoBufferTimestamp, full = false)
      return
    }
    val videoStream = video ?: return
    val uiStream = ui ?: return

    // The one read of the frame's parameters. Every pass below uses this object.
    val scene = snapshots()

    val videoTaken = videoStream.latchAll(onVideoBuffer(benchFrame))
    if (videoTaken > 0) {
      hasVideoFrame = true
      videoStream.surfaceTexture.getTransformMatrix(videoMatrix)
      WholeSceneStats.videoLatches.addAndGet(videoTaken)
    }
    val uiTaken = uiStream.latchAll(onUiBuffer(benchFrame))
    if (uiTaken > 0) {
      hasUiFrame = true
      uiStream.surfaceTexture.getTransformMatrix(uiMatrix)
      WholeSceneStats.uiLatches.addAndGet(uiTaken)
      mainHandler.post { onUiLatched(uiStream) }
    }
    UiBench.maybeStallGl(benchStart)

    val effect = scene.effect
    GLES20.glDisable(GLES20.GL_BLEND)
    if (effect.isIdle) {
      // Nothing to grade: composite straight into the window surface.
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
      drawComposite()
    } else {
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, sceneFramebuffer)
      drawComposite()
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
      drawGrade(effect)
    }

    if (scene.drawsOverlays) {
      GLES20.glEnable(GLES20.GL_BLEND)
      // Both overlays write premultiplied colour, as Canvas source-over expects.
      GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
      if (scene.drawsVines) drawVines(scene.vines)
      drawSnow(effect.timeSeconds)
      GLES20.glDisable(GLES20.GL_BLEND)
    }

    WholeSceneStats.glDraws.incrementAndGet()
    UiBench.glFrameEnd(benchFrame, benchStart, uiBufferTimestamp, videoBufferTimestamp, full = true)
  }

  // UI-update benchmark: BufferQueue timestamps of the buffers currently bound, GL thread only.
  private var uiBufferTimestamp = 0L
  private var videoBufferTimestamp = 0L
  private var benchLatchFrame = 0L
  private val benchUiLatch: (Long) -> Unit = { timestamp ->
    uiBufferTimestamp = timestamp
    UiBench.onLatched(stream = 1, bufferTimestamp = timestamp, glFrame = benchLatchFrame)
  }
  private val benchVideoLatch: (Long) -> Unit = { timestamp ->
    videoBufferTimestamp = timestamp
    UiBench.onLatched(stream = 2, bufferTimestamp = timestamp, glFrame = benchLatchFrame)
  }

  private fun onUiBuffer(frame: Long): ((Long) -> Unit)? {
    if (!UiBench.enabled) return null
    benchLatchFrame = frame
    return benchUiLatch
  }

  private fun onVideoBuffer(frame: Long): ((Long) -> Unit)? {
    if (!UiBench.enabled) return null
    benchLatchFrame = frame
    return benchVideoLatch
  }

  /** Stops new GL work before the view is detached. */
  fun stopRendering() {
    stopped = true
  }

  /** CPU resources; safe without a current context. */
  @Synchronized
  fun releaseStreams() {
    for (stream in listOfNotNull(video, ui)) {
      stream.surfaceTexture.setOnFrameAvailableListener(null)
      stream.surfaceTexture.release()
      WholeSceneStats.liveSurfaceTextures.decrementAndGet()
    }
    video = null
    ui = null
    hasVideoFrame = false
    hasUiFrame = false
    uiBufferTimestamp = 0L
    videoBufferTimestamp = 0L
  }

  /** Runs on the GL thread before it exits. Context teardown frees the names if none is current. */
  @Synchronized
  fun release() {
    stopRendering()
    releaseStreams()
    if (EGL14.eglGetCurrentContext() != EGL14.EGL_NO_CONTEXT) {
      composite.delete()
      grade.delete()
      snow.delete()
      vines.delete()
      deleteSceneTarget()
      deleteBuffer(quadBuffer)
      deleteTexture(videoTexture)
      deleteTexture(uiTexture)
    }
    forgetGlNames()
  }

  private fun forgetGlNames() {
    quadBuffer = 0
    videoTexture = 0
    uiTexture = 0
    sceneTexture = 0
    sceneFramebuffer = 0
    composite.forget()
    grade.forget()
    snow.forget()
    vines.forget()
  }

  private fun openStream(texture: Int, onAvailable: () -> Unit): ExternalStream {
    val stream = ExternalStream(SurfaceTexture(texture))
    WholeSceneStats.liveSurfaceTextures.incrementAndGet()
    stream.surfaceTexture.setOnFrameAvailableListener(
        {
          stream.available.incrementAndGet()
          onAvailable()
        },
        mainHandler,
    )
    return stream
  }

  private fun createSceneTarget(width: Int, height: Int) {
    deleteSceneTarget()
    val ids = IntArray(1)
    GLES20.glGenTextures(1, ids, 0)
    sceneTexture = ids[0]
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTexture)
    GLES20.glTexImage2D(
        GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
    )
    setSamplerParameters(GLES20.GL_TEXTURE_2D, GLES20.GL_LINEAR)
    GLES20.glGenFramebuffers(1, ids, 0)
    sceneFramebuffer = ids[0]
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, sceneFramebuffer)
    GLES20.glFramebufferTexture2D(
        GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, sceneTexture, 0,
    )
    val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    check(status == GLES20.GL_FRAMEBUFFER_COMPLETE) { "Scene framebuffer incomplete: $status" }
  }

  private fun deleteSceneTarget() {
    if (sceneFramebuffer != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(sceneFramebuffer), 0)
    deleteTexture(sceneTexture)
    sceneFramebuffer = 0
    sceneTexture = 0
  }

  private fun drawComposite() {
    val p = composite
    GLES20.glUseProgram(p.program)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTexture)
    GLES20.glUniform1i(p.uVideo, 0)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, uiTexture)
    GLES20.glUniform1i(p.uUi, 1)
    GLES20.glUniformMatrix4fv(p.uVideoMatrix, 1, false, videoMatrix, 0)
    GLES20.glUniformMatrix4fv(p.uUiMatrix, 1, false, uiMatrix, 0)
    val crop = centreCropScale(videoWidth, videoHeight, width, height)
    GLES20.glUniform2f(p.uCropScale, crop.first, crop.second)
    GLES20.glUniform1f(p.uHasVideo, if (hasVideoFrame) 1f else 0f)
    GLES20.glUniform1f(p.uHasUi, if (hasUiFrame) 1f else 0f)
    drawQuad(p.aFramePosition)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
  }

  private fun drawGrade(effect: EffectSnapshot) {
    val p = grade
    GLES20.glUseProgram(p.program)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTexture)
    GLES20.glUniform1i(p.uTexSampler, 0)
    GLES20.glUniform2f(p.uResolution, width.toFloat(), height.toFloat())
    GLES20.glUniform1f(p.uGlitchIntensity, effect.glitchIntensity)
    GLES20.glUniform1f(p.uUpsideDownAmount, effect.upsideDownAmount)
    GLES20.glUniform1f(p.uTime, effect.timeSeconds)
    GLES20.glUniform1f(p.uDarknessIntensity, effect.darknessIntensity)
    GLES20.glUniform1f(p.uRealRandom, effect.realRandom)
    GLES20.glUniform1f(p.uSlices, GlitchSlices)
    drawQuad(p.aFramePosition)
  }

  private fun drawSnow(timeSeconds: Float) {
    val p = snow
    GLES20.glUseProgram(p.program)
    GLES20.glUniform2f(p.uSize, width.toFloat(), height.toFloat())
    GLES20.glUniform1f(p.uTime, timeSeconds)
    drawQuad(p.aFramePosition)
  }

  private fun drawVines(frame: VinesFrame) {
    val p = vines
    p.ensureGeometry(frame.seed, width, height)
    GLES20.glUseProgram(p.program)
    GLES20.glUniform2f(p.uScreenSize, width.toFloat(), height.toFloat())
    GLES20.glUniform1f(p.uProgress, frame.progress)
    GLES20.glUniform1f(p.uMainWidth, MainWidthPx)
    for (vine in 0 until VinesCount) {
      val buffer = p.buffers[vine]
      val points = p.pointCounts[vine]
      if (buffer == 0 || points == 0) continue
      GLES20.glUniform1f(p.uPointCount, points.toFloat())
      GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer)
      p.bindAttributes()
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, points * 2)
      p.unbindAttributes()
    }
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
  }

  private fun drawQuad(attribute: Int) {
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadBuffer)
    GLES20.glEnableVertexAttribArray(attribute)
    GLES20.glVertexAttribPointer(attribute, 2, GLES20.GL_FLOAT, false, 0, 0)
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    GLES20.glDisableVertexAttribArray(attribute)
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
  }

  private class CompositeProgram {
    var program = 0
    var aFramePosition = 0
    var uVideo = 0
    var uVideoMatrix = 0
    var uCropScale = 0
    var uHasVideo = 0
    var uUi = 0
    var uUiMatrix = 0
    var uHasUi = 0

    fun link() {
      program = linkProgram(UPSIDE_DOWN_VERTEX_SHADER, COMPOSITE_FRAGMENT_SHADER)
      aFramePosition = GLES20.glGetAttribLocation(program, "aFramePosition")
      uVideo = GLES20.glGetUniformLocation(program, "uVideo")
      uVideoMatrix = GLES20.glGetUniformLocation(program, "uVideoMatrix")
      uCropScale = GLES20.glGetUniformLocation(program, "uCropScale")
      uHasVideo = GLES20.glGetUniformLocation(program, "uHasVideo")
      uUi = GLES20.glGetUniformLocation(program, "uUi")
      uUiMatrix = GLES20.glGetUniformLocation(program, "uUiMatrix")
      uHasUi = GLES20.glGetUniformLocation(program, "uHasUi")
    }

    fun delete() = deleteProgram(program)
    fun forget() { program = 0 }
  }

  /** The Media3 route's fragment shader, byte for byte; only its input is the composite now. */
  private class GradeProgram {
    var program = 0
    var aFramePosition = 0
    var uTexSampler = 0
    var uResolution = 0
    var uGlitchIntensity = 0
    var uUpsideDownAmount = 0
    var uTime = 0
    var uDarknessIntensity = 0
    var uRealRandom = 0
    var uSlices = 0

    fun link() {
      program = linkProgram(UPSIDE_DOWN_VERTEX_SHADER, UPSIDE_DOWN_FRAGMENT_SHADER_2D)
      aFramePosition = GLES20.glGetAttribLocation(program, "aFramePosition")
      uTexSampler = GLES20.glGetUniformLocation(program, "uTexSampler")
      uResolution = GLES20.glGetUniformLocation(program, "uResolution")
      uGlitchIntensity = GLES20.glGetUniformLocation(program, "uGlitchIntensity")
      uUpsideDownAmount = GLES20.glGetUniformLocation(program, "uUpsideDownAmount")
      uTime = GLES20.glGetUniformLocation(program, "uTime")
      uDarknessIntensity = GLES20.glGetUniformLocation(program, "uDarknessIntensity")
      uRealRandom = GLES20.glGetUniformLocation(program, "uRealRandom")
      uSlices = GLES20.glGetUniformLocation(program, "uSlices")
    }

    fun delete() = deleteProgram(program)
    fun forget() { program = 0 }
  }

  private class SnowProgram {
    var program = 0
    var aFramePosition = 0
    var uSize = 0
    var uTime = 0

    fun link() {
      program = linkProgram(UPSIDE_DOWN_VERTEX_SHADER, SNOW_FRAGMENT_SHADER)
      aFramePosition = GLES20.glGetAttribLocation(program, "aFramePosition")
      uSize = GLES20.glGetUniformLocation(program, "size")
      uTime = GLES20.glGetUniformLocation(program, "time")
    }

    fun delete() = deleteProgram(program)
    fun forget() { program = 0 }
  }

  /** Geometry is rebuilt on the GL thread only when the seed or the size changes. */
  private class VinesProgram {
    var program = 0
    var aPosition = 0
    var aPrev = 0
    var aNext = 0
    var aMeta = 0
    var uScreenSize = 0
    var uProgress = 0
    var uMainWidth = 0
    var uPointCount = 0
    val buffers = IntArray(VinesCount)
    val pointCounts = IntArray(VinesCount)
    private var builtSeed: UInt? = null
    private var builtWidth = 0
    private var builtHeight = 0

    fun link() {
      program = linkProgram(VINES_GL_VERTEX_SHADER, VINES_GL_FRAGMENT_SHADER)
      aPosition = GLES20.glGetAttribLocation(program, "position")
      aPrev = GLES20.glGetAttribLocation(program, "prev")
      aNext = GLES20.glGetAttribLocation(program, "next")
      aMeta = GLES20.glGetAttribLocation(program, "meta")
      uScreenSize = GLES20.glGetUniformLocation(program, "screenSize")
      uProgress = GLES20.glGetUniformLocation(program, "progress")
      uMainWidth = GLES20.glGetUniformLocation(program, "mainWidth")
      uPointCount = GLES20.glGetUniformLocation(program, "pointCount")
    }

    fun ensureGeometry(seed: UInt, width: Int, height: Int) {
      if (builtSeed == seed && builtWidth == width && builtHeight == height) return
      deleteBuffers()
      for (vine in 0 until VinesCount) {
        val line = walkVineCenterline(vine, seed, width.toFloat(), height.toFloat())
        val vertices = packVineVertices(line)
        val data = ByteBuffer.allocateDirect(vertices.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        data.asFloatBuffer().put(vertices)
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.capacity(), data, GLES20.GL_STATIC_DRAW)
        buffers[vine] = ids[0]
        pointCounts[vine] = line.count
      }
      GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
      builtSeed = seed
      builtWidth = width
      builtHeight = height
    }

    fun bindAttributes() {
      attribute(aPosition, 0)
      attribute(aPrev, 8)
      attribute(aNext, 16)
      attribute(aMeta, 24)
    }

    fun unbindAttributes() {
      for (location in intArrayOf(aPosition, aPrev, aNext, aMeta)) GLES20.glDisableVertexAttribArray(location)
    }

    private fun attribute(location: Int, offset: Int) {
      GLES20.glEnableVertexAttribArray(location)
      GLES20.glVertexAttribPointer(location, 2, GLES20.GL_FLOAT, false, VineVertexStrideBytes, offset)
    }

    private fun deleteBuffers() {
      for (vine in 0 until VinesCount) {
        deleteBuffer(buffers[vine])
        buffers[vine] = 0
        pointCounts[vine] = 0
      }
      builtSeed = null
    }

    fun delete() {
      deleteBuffers()
      deleteProgram(program)
    }

    fun forget() {
      program = 0
      buffers.fill(0)
      pointCounts.fill(0)
      builtSeed = null
    }
  }
}

/** RESIZE_MODE_ZOOM as a sampling scale, the same rule as the own-renderer route. */
internal fun centreCropScale(videoWidth: Int, videoHeight: Int, viewWidth: Int, viewHeight: Int): Pair<Float, Float> {
  if (videoWidth <= 0 || videoHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return 1f to 1f
  val videoAspect = videoWidth.toFloat() / videoHeight
  val viewAspect = viewWidth.toFloat() / viewHeight
  return if (videoAspect > viewAspect) (viewAspect / videoAspect) to 1f else 1f to (videoAspect / viewAspect)
}

private const val GlitchSlices = 70f

private fun createExternalTexture(filter: Int): Int {
  val ids = IntArray(1)
  GLES20.glGenTextures(1, ids, 0)
  GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
  setSamplerParameters(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, filter)
  return ids[0]
}

private fun setSamplerParameters(target: Int, filter: Int) {
  GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, filter)
  GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, filter)
  GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
  GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
}

private fun createQuadBuffer(): Int {
  val vertices = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
  val data = ByteBuffer.allocateDirect(vertices.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
  data.asFloatBuffer().put(vertices)
  val ids = IntArray(1)
  GLES20.glGenBuffers(1, ids, 0)
  GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
  GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.capacity(), data, GLES20.GL_STATIC_DRAW)
  GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
  return ids[0]
}

private fun linkProgram(vertexSource: String, fragmentSource: String): Int {
  val program = GLES20.glCreateProgram()
  val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
  val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
  GLES20.glAttachShader(program, vertex)
  GLES20.glAttachShader(program, fragment)
  GLES20.glLinkProgram(program)
  GLES20.glDeleteShader(vertex)
  GLES20.glDeleteShader(fragment)
  val status = IntArray(1)
  GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
  check(status[0] == GLES20.GL_TRUE) { "Program link failed: " + GLES20.glGetProgramInfoLog(program) }
  return program
}

private fun compileShader(type: Int, source: String): Int {
  val shader = GLES20.glCreateShader(type)
  GLES20.glShaderSource(shader, source)
  GLES20.glCompileShader(shader)
  val status = IntArray(1)
  GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
  check(status[0] == GLES20.GL_TRUE) { "Shader compile failed: " + GLES20.glGetShaderInfoLog(shader) }
  return shader
}

private fun deleteProgram(program: Int) {
  if (program != 0) GLES20.glDeleteProgram(program)
}

private fun deleteBuffer(buffer: Int) {
  if (buffer != 0) GLES20.glDeleteBuffers(1, intArrayOf(buffer), 0)
}

private fun deleteTexture(texture: Int) {
  if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
}
