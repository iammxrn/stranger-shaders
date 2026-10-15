package io.github.iammxrn.strangershaders.player

/** Main-thread owner. A delayed GL callback may arrive after composition has been disposed. */
internal class ResourceSlot<T>(private val release: (T) -> Unit) {
    private var current: T? = null
    private var closed = false
    val isClosed: Boolean get() = closed

    fun replace(next: T): Boolean {
        if (closed) {
            release(next)
            return false
        }
        current?.let(release)
        current = next
        return true
    }

    fun close() {
        closed = true
        current?.let(release)
        current = null
    }
}
