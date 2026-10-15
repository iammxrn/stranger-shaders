package io.github.iammxrn.strangershaders.effect.mesh

import org.junit.Assert.assertEquals
import org.junit.Test

class VineMeshVerticesTest {
  private val line = walkVineCenterline(vine = 0, seed = 1337u, screenWidth = 1080f, screenHeight = 2424f)

  @Test fun everyCenterlinePointBecomesOneStripVertexPairInOrder() {
    val vertices = packVineVertices(line)
    assertEquals(line.count * 2 * VineVertexFloats, vertices.size)
    for (i in 0 until line.count) {
      for (side in 0..1) {
        val base = (i * 2 + side) * VineVertexFloats
        assertEquals("x of point $i", line.x(i), vertices[base], 0f)
        assertEquals("y of point $i", line.y(i), vertices[base + 1], 0f)
        assertEquals("index of point $i", i.toFloat(), vertices[base + 6], 0f)
        assertEquals("side of pair $i", if (side == 0) 1f else -1f, vertices[base + 7], 0f)
      }
    }
  }

  @Test fun neighboursAreClampedAtTheRootAndTheTip() {
    val vertices = packVineVertices(line)
    val last = line.count - 1
    // Root: prev is the root itself, next is point 1.
    assertEquals(line.x(0), vertices[2], 0f)
    assertEquals(line.y(0), vertices[3], 0f)
    assertEquals(line.x(1), vertices[4], 0f)
    assertEquals(line.y(1), vertices[5], 0f)
    // Tip: prev is the point before, next is the tip itself.
    val tipBase = (last * 2) * VineVertexFloats
    assertEquals(line.x(last - 1), vertices[tipBase + 2], 0f)
    assertEquals(line.y(last - 1), vertices[tipBase + 3], 0f)
    assertEquals(line.x(last), vertices[tipBase + 4], 0f)
    assertEquals(line.y(last), vertices[tipBase + 5], 0f)
    // Middle: prev and next are the actual neighbours.
    val midBase = (10 * 2 + 1) * VineVertexFloats
    assertEquals(line.x(9), vertices[midBase + 2], 0f)
    assertEquals(line.x(11), vertices[midBase + 4], 0f)
  }

  @Test fun strideMatchesTheAttributeLayout() {
    assertEquals(8, VineVertexFloats)
    assertEquals(VineVertexFloats * 4, VineVertexStrideBytes)
  }
}
