package io.github.iammxrn.strangershaders.wholescene

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.iammxrn.strangershaders.effect.mesh.VINES_VERTEX_SHADER
import io.github.iammxrn.strangershaders.effect.snow.SNOW_SHADER

/** Source-level guards: the route must not fall back to the app's AGSL effects, and ports stay ports. */
class WholeSceneSourceTest {
  private val packageDir = File("src/main/java/io/github/iammxrn/strangershaders/wholescene")

  @Test fun theRouteUsesNoApplicationAgslEffect() {
    val sources = packageDir.listFiles { file -> file.extension == "kt" }.orEmpty()
    assertTrue("package sources must be visible to the test", sources.size >= 8)
    val forbidden = listOf(
        "upsideDownAgsl", "agslGlitch", "agslUpsideDown", "upsideDownSnow", "upsideDownRouteVines",
        "meshVines", "upsideDownVines", "strangerVines", "RuntimeShader", "MeshSpecification",
        "drawMesh", "RenderEffect", "SNOW_SHADER", "GLITCH_SHADER", "upsideDownShader", "chromeModifier",
    )
    val hits = sources.flatMap { file ->
      val code = file.readText().lines().filterNot { it.trimStart().let { l -> l.startsWith("*") || l.startsWith("//") || l.startsWith("/*") } }
      forbidden.filter { token -> code.any { token in it } }.map { "${file.name}: $it" }
    }
    assertEquals(emptyList<String>(), hits)
  }

  @Test fun snowIsALiteralPortOfTheAgslSnow() {
    assertEquals(normalize(function(SNOW_SHADER, "float2 hash2")), normalize(function(SNOW_FRAGMENT_SHADER, "vec2 hash2")))
    assertEquals(normalize(function(SNOW_SHADER, "float snowLayer")), normalize(function(SNOW_FRAGMENT_SHADER, "float snowLayer")))
    // The three layers: scale, velocity, time offset and weight.
    assertEquals(normalize(flakeLines(SNOW_SHADER)), normalize(flakeLines(SNOW_FRAGMENT_SHADER)))
    assertEquals(3, flakeLines(SNOW_FRAGMENT_SHADER).lines().size)
    assertTrue("coverage scale", "clamp(flakes, 0.0, 1.0) * 0.9" in SNOW_FRAGMENT_SHADER)
  }

  @Test fun vineVertexMathIsALiteralPortOfTheMeshShader() {
    fun body(source: String) = source.substringAfter("float maxLen").substringBefore("float2 nrm").substringBefore("vec2 nrm")
    val agsl = body(VINES_VERTEX_SHADER)
        .replace("attributes.", "")
        .replace("Varyings varyings;", "")
    assertEquals(normalize(agsl), normalize(body(VINES_GL_VERTEX_SHADER)))
  }

  private fun function(source: String, signature: String): String {
    val start = source.indexOf(signature)
    check(start >= 0) { signature }
    var depth = 0
    var index = source.indexOf('{', start)
    val open = index
    while (true) {
      when (source[index]) {
        '{' -> depth++
        '}' -> if (--depth == 0) return source.substring(start, index + 1).also { check(open > start) }
      }
      index++
    }
  }

  private fun flakeLines(source: String) = source.lines().map { it.trim() }.filter { it.startsWith("float flakes") || it.startsWith("flakes +=") }
      .joinToString("\n")

  private fun normalize(code: String) = code
      .replace("float2", "vec2").replace("float3", "vec3")
      .replace("smoothstepSksl", "smoothstep")
      .lines().map { it.substringBefore("//").trim() }.filter { it.isNotEmpty() }
      .joinToString("\n")
}
