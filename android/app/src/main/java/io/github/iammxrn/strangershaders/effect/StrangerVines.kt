@file:Suppress("all")
package io.github.iammxrn.strangershaders.effect

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws animated "Stranger Things" style vines that grow from the edges toward the center.
 *
 * @param baseColor The color of the vines (default: dark purple)
 * @param mainStartWidthPx Starting width of main vines in pixels
 * @param mainAfterBranchWidthPx Width of main vine after branch point
 * @param branchStartWidthPx Starting width of branch vines
 * @param growDurationMs Duration of grow/retract cycle in milliseconds
 * @param seed Random seed for procedural generation (randomized by default)
 * @param previewProgressOverride Manual progress override for previews (0.0 to 1.0)
 */
fun Modifier.strangerVines(
  baseColor: Color = Color(0xFF140A1C),
  mainStartWidthPx: Float = 80f,
  mainAfterBranchWidthPx: Float = 20f,
  branchStartWidthPx: Float = 20f,
  growDurationMs: Int = 6_000,
  previewProgressOverride: Float? = null
): Modifier = composed {
  val seed: Int = remember { 1337 + Random.nextInt(100) }
  val inf = rememberInfiniteTransition(label = "vines")
  // Smooth ease for growth/retract
  val animatedProgress by inf.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
          animation = tween(growDurationMs, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse
      ),
      label = "growRetract"
  )
  val progress = previewProgressOverride ?: animatedProgress

  drawWithCache {
    val w = size.width
    val h = size.height
    val midY = h / 2f

    val leftOrigin = Offset(0f, midY)
    val rightOrigin = Offset(w, midY)

    // === GENERATE STATIC GEOMETRY (cached until size changes) ===
    val leftMain = generateCenterline(
        start = leftOrigin + Offset(-5f, 0f),
        targetDir = Offset(1f, 0f),
        maxLenPx = w * 0.72f,
        stepPx = 8f,
        seed = seed,
        bounds = size
    )
    val rightMain = generateCenterline(
        start = rightOrigin + Offset(5f, 0f),
        targetDir = Offset(-1f, 0f),
        maxLenPx = w * 0.72f,
        stepPx = 8f,
        seed = seed + 42,
        bounds = size
    )

    // Branches split off at 30% along the main vines
    val leftBranchIdx = (leftMain.lastIndex * 0.30f).roundToInt().coerceAtLeast(4)
    val rightBranchIdx = (rightMain.lastIndex * 0.30f).roundToInt().coerceAtLeast(4)

    val leftBranch = generateBranchFrom(
        origin = leftMain[leftBranchIdx],
        baseDir = directionAt(leftMain, leftBranchIdx),
        turnDegrees = +35f,
        lengthPx = w * 0.36f,
        stepPx = 8f,
        seed = seed + 101,
        bounds = size
    )
    val rightBranch = generateBranchFrom(
        origin = rightMain[rightBranchIdx],
        baseDir = directionAt(rightMain, rightBranchIdx),
        turnDegrees = -35f,
        lengthPx = w * 0.36f,
        stepPx = 8f,
        seed = seed + 202,
        bounds = size
    )

    // === PRE-COMPUTE ARC LENGTHS (for smooth trimming animation) ===
    val leftMainLengths = computeCumulativeLengths(leftMain)
    val rightMainLengths = computeCumulativeLengths(rightMain)
    val leftBranchLengths = computeCumulativeLengths(leftBranch)
    val rightBranchLengths = computeCumulativeLengths(rightBranch)

    // === WIDTH TAPER FUNCTIONS ===
    val mainWidth = createTaperFunction(mainStartWidthPx, endWidth = 6f)
    val mainAfterBranchWidth = createTaperFunction(mainAfterBranchWidthPx, endWidth = 4f)
    val branchWidth = createTaperFunction(branchStartWidthPx, endWidth = 2f)

    // === REUSABLE OBJECTS (zero allocations per frame) ===
    val leftMainPath = Path()
    val rightMainPath = Path()
    val leftBranchPath = Path()
    val rightBranchPath = Path()
    val leftAfterPath = Path()
    val rightAfterPath = Path()

    val pointsBuffer = ArrayList<Offset>(200)
    val pointsBuffer2 = ArrayList<Offset>(200)
    val leftEdgeBuffer = ArrayList<Offset>(200)
    val rightEdgeBuffer = ArrayList<Offset>(200)
    val lengthBuffer = FloatArray(200)

    onDrawWithContent {
      drawContent()

      val hasLeftAfter = buildMainVinePath(
          vineData = VineRenderData(
              centerline = leftMain,
              cachedLengths = leftMainLengths,
              branchIndex = leftBranchIdx,
              progress = progress,
              pointsBuffer = pointsBuffer,
              mainPath = leftMainPath,
              afterBranchPath = leftAfterPath,
              mainWidthFn = mainWidth,
              afterBranchWidthFn = mainAfterBranchWidth
          ),
          leftEdgeBuffer = leftEdgeBuffer,
          rightEdgeBuffer = rightEdgeBuffer,
          lengthBuffer = lengthBuffer
      )

      val hasRightAfter = buildMainVinePath(
          vineData = VineRenderData(
              centerline = rightMain,
              cachedLengths = rightMainLengths,
              branchIndex = rightBranchIdx,
              progress = progress,
              pointsBuffer = pointsBuffer2,
              mainPath = rightMainPath,
              afterBranchPath = rightAfterPath,
              mainWidthFn = mainWidth,
              afterBranchWidthFn = mainAfterBranchWidth
          ),
          leftEdgeBuffer = leftEdgeBuffer,
          rightEdgeBuffer = rightEdgeBuffer,
          lengthBuffer = lengthBuffer
      )

      val branchProgress = ((progress - 0.30f) / 0.70f).coerceIn(0f, 1f)

      buildBranchVinePath(
          centerline = leftBranch,
          cachedLengths = leftBranchLengths,
          progress = branchProgress,
          pointsBuffer = pointsBuffer,
          path = leftBranchPath,
          widthFn = branchWidth,
          leftEdgeBuffer = leftEdgeBuffer,
          rightEdgeBuffer = rightEdgeBuffer,
          lengthBuffer = lengthBuffer
      )

      buildBranchVinePath(
          centerline = rightBranch,
          cachedLengths = rightBranchLengths,
          progress = branchProgress,
          pointsBuffer = pointsBuffer2,
          path = rightBranchPath,
          widthFn = branchWidth,
          leftEdgeBuffer = leftEdgeBuffer,
          rightEdgeBuffer = rightEdgeBuffer,
          lengthBuffer = lengthBuffer
      )

      // Render all paths
      drawPath(leftMainPath, baseColor, alpha = 0.96f, style = Fill)
      drawPath(rightMainPath, baseColor, alpha = 0.96f, style = Fill)
      drawPath(leftBranchPath, baseColor, alpha = 0.92f, style = Fill)
      drawPath(rightBranchPath, baseColor, alpha = 0.92f, style = Fill)
      if (hasLeftAfter) drawPath(leftAfterPath, baseColor, alpha = 0.96f, style = Fill)
      if (hasRightAfter) drawPath(rightAfterPath, baseColor, alpha = 0.96f, style = Fill)
    }
  }
}

// === Data structures ===

private class CumulativeLengths(
  val lengths: FloatArray,
  val total: Float
)

private class VineRenderData(
  val centerline: List<Offset>,
  val cachedLengths: CumulativeLengths,
  val branchIndex: Int,
  val progress: Float,
  val pointsBuffer: ArrayList<Offset>,
  val mainPath: Path,
  val afterBranchPath: Path,
  val mainWidthFn: (Float) -> Float,
  val afterBranchWidthFn: (Float) -> Float
)

// === Vine path building functions ===

/**
 * Builds a main vine path with optional thinner continuation after branch point.
 * @return true if the "after branch" path was built
 */
private fun buildMainVinePath(
  vineData: VineRenderData,
  leftEdgeBuffer: ArrayList<Offset>,
  rightEdgeBuffer: ArrayList<Offset>,
  lengthBuffer: FloatArray
): Boolean {
  vineData.pointsBuffer.clear()
  val trimSize = trimByProgressLengthCachedInto(
      vineData.pointsBuffer,
      vineData.centerline,
      vineData.cachedLengths,
      vineData.progress
  )

  vineData.mainPath.reset()
  buildTaperedRibbonInto(
      vineData.mainPath, vineData.pointsBuffer, 0, trimSize,
      vineData.mainWidthFn, leftEdgeBuffer, rightEdgeBuffer, lengthBuffer
  )

  // Build thinner continuation after branch point
  if (trimSize > vineData.branchIndex + 1) {
    val afterStartIdx = vineData.branchIndex.coerceAtLeast(0)
    val afterSize = trimSize - afterStartIdx
    if (afterSize >= 2) {
      vineData.afterBranchPath.reset()
      buildTaperedRibbonInto(
          vineData.afterBranchPath, vineData.pointsBuffer, afterStartIdx, afterSize,
          vineData.afterBranchWidthFn, leftEdgeBuffer, rightEdgeBuffer, lengthBuffer
      )
      return true
    }
  }
  return false
}

/**
 * Builds a branch vine path.
 */
private fun buildBranchVinePath(
  centerline: List<Offset>,
  cachedLengths: CumulativeLengths,
  progress: Float,
  pointsBuffer: ArrayList<Offset>,
  path: Path,
  widthFn: (Float) -> Float,
  leftEdgeBuffer: ArrayList<Offset>,
  rightEdgeBuffer: ArrayList<Offset>,
  lengthBuffer: FloatArray
) {
  pointsBuffer.clear()
  val trimSize = trimByProgressLengthCachedInto(
      pointsBuffer, centerline, cachedLengths, progress
  )
  path.reset()
  buildTaperedRibbonInto(
      path, pointsBuffer, 0, trimSize, widthFn,
      leftEdgeBuffer, rightEdgeBuffer, lengthBuffer
  )
}

// === Helper functions ===

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

private fun createTaperFunction(startWidth: Float, endWidth: Float): (Float) -> Float {
  return { progress -> lerp(startWidth, endWidth, progress) }
}

/** Compute cumulative arc lengths once for reuse across frames. */
private fun computeCumulativeLengths(center: List<Offset>): CumulativeLengths {
  if (center.size < 2) return CumulativeLengths(FloatArray(center.size), 0f)

  val cum = FloatArray(center.size)
  var total = 0f
  for (i in 1 until center.size) {
    total += (center[i] - center[i - 1]).getDistance()
    cum[i] = total
  }
  return CumulativeLengths(cum, total)
}

/**
 * Trims a polyline by arc length progress (0.0 to 1.0) with smooth interpolation.
 * Writes directly into the provided buffer to avoid allocations.
 *
 * @return Number of points written to the buffer
 */
private fun trimByProgressLengthCachedInto(
  outBuffer: ArrayList<Offset>,
  centerline: List<Offset>,
  cachedLengths: CumulativeLengths,
  progress: Float
): Int {
  if (centerline.size < 2) {
    centerline.forEach { outBuffer.add(it) }
    return centerline.size
  }

  val targetLength = cachedLengths.total * progress.coerceIn(0f, 1f)

  // Binary search for the segment containing the target length
  var segmentIndex = 1
  while (segmentIndex < cachedLengths.lengths.size && cachedLengths.lengths[segmentIndex] < targetLength) {
    segmentIndex++
  }

  // Add all complete segments before the target
  for (i in 0 until (segmentIndex - 1).coerceAtLeast(0)) {
    outBuffer.add(centerline[i])
  }

  // Interpolate the final point within the target segment
  val startIdx = (segmentIndex - 1).coerceAtLeast(0)
  val endIdx = segmentIndex.coerceAtMost(centerline.lastIndex)
  val lengthBefore = if (startIdx == 0) 0f else cachedLengths.lengths[startIdx]
  val segmentLength = (centerline[endIdx] - centerline[startIdx]).getDistance().coerceAtLeast(1e-6f)
  val segmentProgress = ((targetLength - lengthBefore) / segmentLength).coerceIn(0f, 1f)

  val interpolatedPoint = centerline[startIdx] + (centerline[endIdx] - centerline[startIdx]) * segmentProgress
  outBuffer.add(centerline[startIdx])
  if (outBuffer.last() != interpolatedPoint) {
    outBuffer.add(interpolatedPoint)
  }

  return outBuffer.size
}

private fun directionAt(points: List<Offset>, index: Int): Offset {
  val prevIdx = max(0, index - 1)
  val nextIdx = min(points.lastIndex, index + 1)
  val direction = points[nextIdx] - points[prevIdx]
  return if (direction.getDistance() < 1e-3f) Offset(1f, 0f) else direction.normalize()
}

/**
 * Builds a tapered ribbon path from a centerline with varying width.
 * Uses provided buffers to avoid allocations during rendering.
 */
private fun buildTaperedRibbonInto(
  path: Path,
  centerline: ArrayList<Offset>,
  startIndex: Int,
  pointCount: Int,
  widthAt: (Float) -> Float,
  leftEdgeBuffer: ArrayList<Offset>,
  rightEdgeBuffer: ArrayList<Offset>,
  lengthBuffer: FloatArray
) {
  if (pointCount < 2) return

  leftEdgeBuffer.clear()
  rightEdgeBuffer.clear()

  val endIndex = startIndex + pointCount

  // Calculate cumulative lengths for this segment
  var totalLength = 0f
  lengthBuffer[startIndex] = 0f
  for (i in startIndex + 1 until endIndex) {
    totalLength += (centerline[i] - centerline[i - 1]).getDistance()
    lengthBuffer[i] = totalLength
  }

  // Helper to compute perpendicular normal at any point
  fun perpendicularAt(index: Int): Offset {
    val prevIdx = max(startIndex, index - 1)
    val nextIdx = min(endIndex - 1, index + 1)
    val tangent = centerline[nextIdx] - centerline[prevIdx]
    val length = tangent.getDistance().coerceAtLeast(1e-3f)
    return Offset(-tangent.y / length, tangent.x / length)
  }

  // Build left and right edges with tapering width
  for (i in startIndex until endIndex) {
    val progress = if (totalLength == 0f) 0f else (lengthBuffer[i] / totalLength)
    val halfWidth = widthAt(progress) * 0.5f
    val perpendicular = perpendicularAt(i) * halfWidth
    leftEdgeBuffer.add(centerline[i] + perpendicular)
    rightEdgeBuffer.add(centerline[i] - perpendicular)
  }

  // Build closed ribbon path
  path.moveTo(leftEdgeBuffer.first().x, leftEdgeBuffer.first().y)
  for (i in 1 until leftEdgeBuffer.size) {
    path.lineTo(leftEdgeBuffer[i].x, leftEdgeBuffer[i].y)
  }
  for (i in rightEdgeBuffer.lastIndex downTo 0) {
    path.lineTo(rightEdgeBuffer[i].x, rightEdgeBuffer[i].y)
  }
  path.close()
}

// === Curve generation ===

/**
 * Generates a main vine centerline using procedural noise for organic movement.
 */
private fun generateCenterline(
  start: Offset,
  targetDir: Offset,
  maxLenPx: Float,
  stepPx: Float,
  seed: Int,
  bounds: Size
): List<Offset> {
  val random = FastRand(seed)
  val points = ArrayList<Offset>()
  points += start

  var direction = targetDir.normalize()
  var distanceTravelled = 0f

  while (distanceTravelled < maxLenPx) {
    val noise = noise2D(points.last().x * 0.005f, points.last().y * 0.005f, seed)
    val angleChange = (noise * 6f + random.range(-2f, 2f)).toRadians()
    direction = direction.rotate(angleChange).normalize()

    val nextPoint = points.last() + direction * stepPx

    // Stop if we exit the bounds
    if (nextPoint.x !in 0f..bounds.width || nextPoint.y !in 0f..bounds.height) break

    points += nextPoint
    distanceTravelled += stepPx
  }

  // Only smooth shorter paths to avoid performance hit
  return if (points.size < 100) smoothPolyline(points) else points
}

/**
 * Generates a branch vine that splits off from the main vine.
 */
private fun generateBranchFrom(
  origin: Offset,
  baseDir: Offset,
  turnDegrees: Float,
  lengthPx: Float,
  stepPx: Float,
  seed: Int,
  bounds: Size
): List<Offset> {
  val random = FastRand(seed)
  val points = ArrayList<Offset>()
  points += origin

  var direction = baseDir.rotate(turnDegrees.toRadians()).normalize()
  var distanceTravelled = 0f

  while (distanceTravelled < lengthPx) {
    val noise = noise2D(points.last().x * 0.006f, points.last().y * 0.006f, seed + 7)
    val angleChange = (noise * 16f + random.range(-4f, 4f)).toRadians()
    direction = direction.rotate(angleChange).normalize()

    val nextPoint = points.last() + direction * stepPx

    // Stop if we exit the bounds
    if (nextPoint.x !in 0f..bounds.width || nextPoint.y !in 0f..bounds.height) break

    points += nextPoint
    distanceTravelled += stepPx
  }

  // Only smooth shorter paths to avoid performance hit
  return if (points.size < 100) smoothPolyline(points) else points
}

// === Math utilities ===

/**
 * Fast pseudo-random number generator using LCG algorithm.
 */
private class FastRand(seed: Int) {
  private var state = seed.toUInt()

  fun next(): Float {
    state = (1664525u * state + 1013904223u)
    return ((state shr 8) and 0xFFFFFFu).toFloat() / 0xFFFFFF
  }

  fun range(min: Float, max: Float) = min + (max - min) * next()
}

private fun Offset.normalize(): Offset {
  val distance = getDistance()
  return if (distance < 1e-6f) Offset(1f, 0f) else this / distance
}

private fun Float.toRadians() = this / 180f * Math.PI.toFloat()

private fun Offset.rotate(radians: Float): Offset {
  val cos = cos(radians)
  val sin = sin(radians)
  return Offset(x * cos - y * sin, x * sin + y * cos)
}

/**
 * Simple 2D noise function using combined sine waves.
 * Deterministic and dependency-free.
 */
private fun noise2D(x: Float, y: Float, seed: Int): Float {
  val sx = x * 0.9f + seed * 0.013f
  val sy = y * 0.9f - seed * 0.017f
  val value = sin(sx * 0.9f) + sin(sy * 1.1f) + sin((sx + sy) * 0.5f)
  return (value / 3f).coerceIn(-1f, 1f)
}

/**
 * Applies Chaikin's corner-cutting algorithm for smoother curves.
 * Single pass subdivision.
 */
private fun smoothPolyline(points: List<Offset>): List<Offset> {
  if (points.size < 3) return points

  val smoothed = ArrayList<Offset>(points.size * 2)
  smoothed += points.first()

  for (i in 0 until points.size - 2) {
    val p0 = points[i]
    val p1 = points[i + 1]
    smoothed += p0 * 0.75f + p1 * 0.25f
    smoothed += p0 * 0.25f + p1 * 0.75f
  }

  smoothed += points.last()
  return smoothed
}

@Preview
@Composable
private fun VinesPreview() {
  Surface {
    Box(
        Modifier
            .fillMaxSize()
            .strangerVines()
    )
  }
}
