package com.perfmonitor

import android.view.Choreographer
import java.util.concurrent.atomic.AtomicInteger

/**
 * Monitors FPS using Choreographer frame callbacks.
 */
class FpsMonitor {

    private val choreographer = Choreographer.getInstance()
    private val frameCount = AtomicInteger(0)
    private var lastTimestamp = 0L
    private var currentFps = 0

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (lastTimestamp == 0L) {
                lastTimestamp = frameTimeNanos
            }

            frameCount.incrementAndGet()

            val elapsed = frameTimeNanos - lastTimestamp
            // Update FPS every ~1 second
            if (elapsed >= 1_000_000_000L) {
                currentFps = (frameCount.get() * 1_000_000_000L / elapsed).toInt()
                frameCount.set(0)
                lastTimestamp = frameTimeNanos
            }

            choreographer.postFrameCallback(this)
        }
    }

    fun start() {
        lastTimestamp = 0L
        frameCount.set(0)
        choreographer.postFrameCallback(frameCallback)
    }

    fun stop() {
        choreographer.removeFrameCallback(frameCallback)
    }

    fun getFps(): Int = currentFps
}
