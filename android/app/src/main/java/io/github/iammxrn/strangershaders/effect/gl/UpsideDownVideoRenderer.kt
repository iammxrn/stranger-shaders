package io.github.iammxrn.strangershaders.effect.gl

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import io.github.iammxrn.strangershaders.bench.UiBench
import io.github.iammxrn.strangershaders.effect.UpsideDownEffectState
import io.github.iammxrn.strangershaders.perf.VideoPipelineStats
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Draws decoder output ourselves instead of letting anyone else do it.
 *
 * The decoder writes into a [SurfaceTexture], which is an external (OES) GL texture. Every vsync we
 * take whatever the newest frame is, run the shader over it and present. Because the loop is driven
 * by the display and not by the decoder, the effect keeps animating at full display rate even when
 * playback is paused. The base [UpsideDownGlEffect] sample does not request cached-frame redraw.
 *
 * @param onSurfaceTextureReady called on the GL thread once the texture exists; the caller must hop
 * to the main thread before handing the resulting Surface to the player.
 */
class UpsideDownVideoRenderer(
  private val state: UpsideDownEffectState,
  private val onSurfaceTextureReady: (SurfaceTexture) -> Unit,
) : GLSurfaceView.Renderer {

  @Volatile
  var videoWidth: Int = 0

  @Volatile
  var videoHeight: Int = 0

  /** Set to drive rendering from decoder frames instead of vsync. */
  @Volatile
  var onFrameAvailable: (() -> Unit)? = null

  @Volatile private var stopped = false
  private var program = 0
  private var textureId = 0
  private var quadBuffer = 0
  private var surfaceTexture: SurfaceTexture? = null

  // Resolved once at link time. glGetUniformLocation is a string lookup into the driver; doing ten
  // of them per frame is pure waste on a thread that has 16 ms to spend.
  private var aFramePosition = 0
  private var uTexSampler = 0
  private var uTexMatrix = 0
  private var uCropScale = 0
  private var uResolution = 0
  private var uGlitchIntensity = 0
  private var uUpsideDownAmount = 0
  private var uTime = 0
  private var uDarknessIntensity = 0
  private var uRealRandom = 0
  private var uSlices = 0

  private val textureMatrix = FloatArray(MatrixSize)
  private val cropScale = FloatArray(2)

  private var viewWidth = 1
  private var viewHeight = 1

  @Synchronized
  override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
    if (stopped) return
    // The old EGL context owns its numeric GL names. Never delete those names in the new context.
    surfaceTexture?.release()
    surfaceTexture = null
    program = linkProgram(UPSIDE_DOWN_VERTEX_SHADER, UPSIDE_DOWN_FRAGMENT_SHADER_OES)
    textureId = createExternalTexture()
    quadBuffer = createQuadBuffer()
    resolveLocations()

    val texture = SurfaceTexture(textureId)
    texture.setOnFrameAvailableListener { onFrameAvailable?.invoke() }
    surfaceTexture = texture
    onSurfaceTextureReady(texture)
  }

  override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
    viewWidth = width
    viewHeight = height
    GLES20.glViewport(0, 0, width, height)
  }

  @Synchronized
  override fun onDrawFrame(unused: GL10?) {
    // UI-update benchmark: GLSurfaceView swaps after every call, early returns included.
    val benchFrame = UiBench.glFrameStart()
    val benchStart = System.nanoTime()
    val texture = surfaceTexture
    if (stopped || texture == null) {
      UiBench.glFrameEnd(benchFrame, benchStart, 0L, benchVideoTimestamp, full = false)
      return
    }

    // No-op when the decoder has produced nothing new: the previous frame stays bound, and the
    // shader still re-runs over it. That is precisely why a paused video keeps glitching here.
    texture.updateTexImage()
    if (UiBench.enabled) {
      // The bound buffer's timestamp; unchanged when updateTexImage found no new decoder frame.
      val timestamp = texture.timestamp
      if (timestamp != benchVideoTimestamp) UiBench.onLatched(stream = 2, bufferTimestamp = timestamp, glFrame = benchFrame)
      benchVideoTimestamp = timestamp
    }
    texture.getTransformMatrix(textureMatrix)

    val snapshot = state.glSnapshot
    updateCropScale()

    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    GLES20.glUseProgram(program)

    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
    GLES20.glUniform1i(uTexSampler, 0)
    GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, textureMatrix, 0)

    GLES20.glUniform2f(uCropScale, cropScale[0], cropScale[1])
    GLES20.glUniform2f(uResolution, viewWidth.toFloat(), viewHeight.toFloat())
    GLES20.glUniform1f(uGlitchIntensity, snapshot.glitchIntensity)
    GLES20.glUniform1f(uUpsideDownAmount, snapshot.upsideDownAmount)
    GLES20.glUniform1f(uTime, snapshot.timeSeconds)
    GLES20.glUniform1f(uDarknessIntensity, snapshot.darknessIntensity)
    GLES20.glUniform1f(uRealRandom, snapshot.realRandom)
    GLES20.glUniform1f(uSlices, Slices)

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadBuffer)
    GLES20.glEnableVertexAttribArray(aFramePosition)
    GLES20.glVertexAttribPointer(
        aFramePosition,
        CoordinatesPerVertex,
        GLES20.GL_FLOAT,
        false,
        /* stride= */ 0,
        /* offset= */ 0,
    )

    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VertexCount)

    GLES20.glDisableVertexAttribArray(aFramePosition)
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

    VideoPipelineStats.onFrameRendered()
    UiBench.glFrameEnd(benchFrame, benchStart, 0L, benchVideoTimestamp, full = true)
  }

  private var benchVideoTimestamp = 0L

  /** Stops new work before the GL view is detached; publishing effect state does not request a draw. */
  fun stopRendering() {
    stopped = true
    onFrameAvailable = null
  }

  /** CPU resource release also works when a paused view no longer has a current EGL context. */
  @Synchronized
  fun releaseSurfaceTexture() {
    surfaceTexture?.setOnFrameAvailableListener(null)
    surfaceTexture?.release()
    surfaceTexture = null
  }

  /** Runs before GL thread exit. Context teardown frees names if no context is current. */
  @Synchronized
  fun release() {
    stopRendering()
    releaseSurfaceTexture()
    if (EGL14.eglGetCurrentContext() != EGL14.EGL_NO_CONTEXT) {
      if (quadBuffer != 0) GLES20.glDeleteBuffers(1, intArrayOf(quadBuffer), 0)
      if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
      if (program != 0) GLES20.glDeleteProgram(program)
    }
    quadBuffer = 0
    textureId = 0
    program = 0
  }

  private fun resolveLocations() {
    aFramePosition = GLES20.glGetAttribLocation(program, "aFramePosition")
    uTexSampler = GLES20.glGetUniformLocation(program, "uTexSampler")
    uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
    uCropScale = GLES20.glGetUniformLocation(program, "uCropScale")
    uResolution = GLES20.glGetUniformLocation(program, "uResolution")
    uGlitchIntensity = GLES20.glGetUniformLocation(program, "uGlitchIntensity")
    uUpsideDownAmount = GLES20.glGetUniformLocation(program, "uUpsideDownAmount")
    uTime = GLES20.glGetUniformLocation(program, "uTime")
    uDarknessIntensity = GLES20.glGetUniformLocation(program, "uDarknessIntensity")
    uRealRandom = GLES20.glGetUniformLocation(program, "uRealRandom")
    uSlices = GLES20.glGetUniformLocation(program, "uSlices")
  }

  /** Equivalent of RESIZE_MODE_ZOOM: shrink the sampled region on the axis that overflows. */
  private fun updateCropScale() {
    if (videoWidth <= 0 || videoHeight <= 0) {
      cropScale[0] = 1f
      cropScale[1] = 1f
      return
    }

    val videoAspect = videoWidth.toFloat() / videoHeight
    val viewAspect = viewWidth.toFloat() / viewHeight

    if (videoAspect > viewAspect) {
      cropScale[0] = viewAspect / videoAspect
      cropScale[1] = 1f
    } else {
      cropScale[0] = 1f
      cropScale[1] = videoAspect / viewAspect
    }
  }
}

private fun createExternalTexture(): Int {
  val ids = IntArray(1)
  GLES20.glGenTextures(1, ids, 0)
  val id = ids[0]

  GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id)
  GLES20.glTexParameteri(
      GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
      GLES20.GL_TEXTURE_MIN_FILTER,
      GLES20.GL_LINEAR,
  )
  GLES20.glTexParameteri(
      GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
      GLES20.GL_TEXTURE_MAG_FILTER,
      GLES20.GL_LINEAR,
  )
  GLES20.glTexParameteri(
      GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
      GLES20.GL_TEXTURE_WRAP_S,
      GLES20.GL_CLAMP_TO_EDGE,
  )
  GLES20.glTexParameteri(
      GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
      GLES20.GL_TEXTURE_WRAP_T,
      GLES20.GL_CLAMP_TO_EDGE,
  )

  return id
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
  check(status[0] == GLES20.GL_TRUE) {
    "Program link failed: " + GLES20.glGetProgramInfoLog(program)
  }

  return program
}

private fun compileShader(type: Int, source: String): Int {
  val shader = GLES20.glCreateShader(type)
  GLES20.glShaderSource(shader, source)
  GLES20.glCompileShader(shader)

  val status = IntArray(1)
  GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
  check(status[0] == GLES20.GL_TRUE) {
    "Shader compile failed: " + GLES20.glGetShaderInfoLog(shader)
  }

  return shader
}

private fun createQuadBuffer(): Int {
  val ids = IntArray(1)
  GLES20.glGenBuffers(1, ids, 0)

  val vertices = createFullScreenQuad()
  GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
  GLES20.glBufferData(
      GLES20.GL_ARRAY_BUFFER,
      vertices.capacity() * Float.SIZE_BYTES,
      vertices,
      GLES20.GL_STATIC_DRAW,
  )
  GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

  return ids[0]
}

private fun createFullScreenQuad(): FloatBuffer {
  val vertices = floatArrayOf(
      -1f, -1f,
      1f, -1f,
      -1f, 1f,
      1f, 1f,
  )

  return ByteBuffer
      .allocateDirect(vertices.size * Float.SIZE_BYTES)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()
      .apply {
        put(vertices)
        position(0)
      }
}

private const val MatrixSize = 16
private const val CoordinatesPerVertex = 2
private const val VertexCount = 4
private const val Slices = 70f
