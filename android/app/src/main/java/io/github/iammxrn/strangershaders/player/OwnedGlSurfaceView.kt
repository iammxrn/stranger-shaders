package io.github.iammxrn.strangershaders.player

import android.content.Context
import android.opengl.GLSurfaceView
import io.github.iammxrn.strangershaders.effect.gl.UpsideDownVideoRenderer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Compose can call onRelease after Android has already stopped the GL thread during detach. */
internal class OwnedGlSurfaceView(
    context: Context,
    private val renderer: UpsideDownVideoRenderer,
    private val beforeShutdown: () -> Unit,
) : GLSurfaceView(context) {
    private var closed = false

    fun shutdown() {
        if (closed) return
        closed = true
        // Reject delayed GL-to-main handoffs and unbind ExoPlayer before releasing its texture.
        beforeShutdown()
        renderer.stopRendering()
        val released = CountDownLatch(1)
        queueEvent {
            try { renderer.release() } finally { released.countDown() }
        }
        // Cleanup has no main-thread dependency. EGL teardown below owns any remaining GL names.
        if (!released.await(2, TimeUnit.SECONDS)) renderer.releaseSurfaceTexture()
    }

    override fun onDetachedFromWindow() {
        shutdown()
        super.onDetachedFromWindow()
    }
}
