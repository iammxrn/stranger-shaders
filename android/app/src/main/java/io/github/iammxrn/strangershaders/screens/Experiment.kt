package io.github.iammxrn.strangershaders.screens

/**
 * Experimental screens, kept apart from the five [Demo] video routes so those keep their names,
 * order and measurement tooling. Opened with `--es experiment <name>`.
 */
enum class Experiment(val title: String, val subtitle: String) {
  WholeSceneGl(
      title = "Whole-screen GL",
      subtitle = "Compose UI rendered into a texture; our GL renderer composes it with video and applies every effect.",
  ),
  ;

  companion object {
    fun fromExtra(name: String?): Experiment? =
        entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
  }
}
