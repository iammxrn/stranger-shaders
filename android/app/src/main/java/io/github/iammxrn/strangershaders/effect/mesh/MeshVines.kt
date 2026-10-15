package io.github.iammxrn.strangershaders.effect.mesh

import android.graphics.BlendMode
import android.graphics.ColorSpace
import android.graphics.Mesh
import android.graphics.MeshSpecification
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import java.nio.ByteBuffer
import java.nio.ByteOrder
import io.github.iammxrn.strangershaders.bench.UiBench
import io.github.iammxrn.strangershaders.bench.benchVinesCycle
import io.github.iammxrn.strangershaders.effect.UpsideDownEffectState
import io.github.iammxrn.strangershaders.effect.agsl.upsideDownVines
import io.github.iammxrn.strangershaders.teaching.LocalCaptureOptions

/** Canvas.drawMesh with AGSL vertex shaders arrived in Android 14. */
val isMeshSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

/**
 * The iOS Metal vines overlay on Android: four ribbons drawn by [android.graphics.Canvas.drawMesh]
 * with an AGSL vertex shader, on top of whatever this modifier wraps.
 *
 * Put it outside the graded layer, like the iOS overlay sits above the SwiftUI shader modifiers:
 * `Modifier.meshVines(effect).upsideDownAgsl(effect, drawVines = false)`. The ribbons then keep the
 * shader's own output colour instead of being hue-shifted with the content underneath.
 *
 * Below API 34 the modifier is a no-op; [upsideDownRouteVines] falls back to the Canvas vines there.
 */
@Composable
fun Modifier.meshVines(state: UpsideDownEffectState): Modifier {
  if (!isMeshSupported) return this
  return if (state.snapshot.upsideDownAmount > 0f) meshVinesOverlay() else this
}

/**
 * The vines every demo route draws: [meshVines] from Android 14, and the original Canvas vines in
 * their pre-graded colour below it, so API 33 devices still show vines. Both sit outside the graded
 * layer: `Modifier.upsideDownSnow(effect).upsideDownRouteVines(effect).upsideDownAgsl(effect,
 * drawVines = false)`, or on the split video routes simply above the root.
 */
@Composable
fun Modifier.upsideDownRouteVines(state: UpsideDownEffectState): Modifier =
    if (isMeshSupported) meshVines(state) else upsideDownVines(state)

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
private fun Modifier.meshVinesOverlay(): Modifier {
  val capture = LocalCaptureOptions.current
  val cycle = remember { benchVinesCycle() }
  // Fixed capture pins the vines fully grown with the base seed, so a screenshot is reproducible.
  var progress by remember { mutableFloatStateOf(if (capture.fixedEffect) 1f else 0f) }
  var seed by remember { mutableStateOf(VinesCycle.BaseSeed) }

  if (!capture.fixedEffect) {
    LaunchedEffect(cycle) {
      while (true) {
        withFrameNanos { frameNanos ->
          progress = cycle.progress(frameNanos / NanosPerSecond)
          seed = cycle.seed
        }
      }
    }
  }

  val specification = remember { vinesMeshSpecification() }
  val paint = remember { Paint() }

  return drawWithCache {
    // Re-walked only when the seed or the size changes; the frame loop touches uniforms alone.
    val meshes = buildVineMeshes(specification, seed, size)
    onDrawWithContent {
      drawContent()
      val visible = progress
      if (visible > 0f) {
        drawIntoCanvas { canvas ->
          if (UiBench.enabled) UiBench.meshVineDraws.incrementAndGet()
          for (vine in meshes) {
            vine.mesh.setFloatUniform("progress", visible)
            canvas.nativeCanvas.drawMesh(vine.mesh, BlendMode.SRC_OVER, paint)
          }
        }
      }
    }
  }
}

/** One triangle strip per vine. The direct buffer stays referenced for as long as the mesh lives. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class VineMesh(val mesh: Mesh, @Suppress("unused") private val vertices: ByteBuffer)

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
fun vinesMeshSpecification(): MeshSpecification = MeshSpecification.make(
    arrayOf(
        MeshSpecification.Attribute(MeshSpecification.TYPE_FLOAT2, 0, "position"),
        MeshSpecification.Attribute(MeshSpecification.TYPE_FLOAT2, 8, "prev"),
        MeshSpecification.Attribute(MeshSpecification.TYPE_FLOAT2, 16, "next"),
        MeshSpecification.Attribute(MeshSpecification.TYPE_FLOAT2, 24, "meta"),
    ),
    VineVertexStrideBytes,
    emptyArray(),
    VINES_VERTEX_SHADER,
    VINES_FRAGMENT_SHADER,
    ColorSpace.get(ColorSpace.Named.SRGB),
    MeshSpecification.ALPHA_TYPE_PREMULTIPLIED,
)

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
fun buildVineMeshes(specification: MeshSpecification, seed: UInt, size: Size): List<VineMesh> {
  if (size.width <= 0f || size.height <= 0f) return emptyList()
  // Ribbon edges may leave the screen by up to half the root width; the bounds must contain them.
  val bounds = RectF(-MainWidthPx, -MainWidthPx, size.width + MainWidthPx, size.height + MainWidthPx)
  return (0 until VinesCount).map { vine ->
    val line = walkVineCenterline(vine, seed, size.width, size.height)
    val vertices = packVineVertices(line)
    val buffer = ByteBuffer.allocateDirect(vertices.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
    buffer.asFloatBuffer().put(vertices)
    val mesh = Mesh(specification, Mesh.TRIANGLE_STRIP, buffer, line.count * 2, bounds).apply {
      setFloatUniform("screenSize", size.width, size.height)
      setFloatUniform("mainWidth", MainWidthPx)
      setFloatUniform("pointCount", line.count.toFloat())
      setFloatUniform("progress", 0f)
    }
    VineMesh(mesh, buffer)
  }
}

/** Root width of every vine, in pixels, as in the iOS `VinesUniforms.mainWidth`. */
const val MainWidthPx = 80f
private const val NanosPerSecond = 1_000_000_000.0
