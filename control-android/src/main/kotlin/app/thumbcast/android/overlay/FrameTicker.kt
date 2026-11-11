package app.thumbcast.android.overlay

import android.view.View

/**
 * Impuls raz na klatkę (`View.postOnAnimation`), planowany tylko, gdy [isNeeded] (palec dotyka ekranu).
 * Sam nic nie wysyła: woła [onFrame], a ten przekazuje czas do silnika. Bez palców nie ma impulsów.
 */
internal class FrameTicker(
    private val view: View,
    private val isNeeded: () -> Boolean,
    private val onFrame: () -> Unit,
) {
    private var scheduled: Boolean = false
    private val runnable: Runnable = Runnable { run() }

    fun scheduleIfNeeded() {
        if (scheduled || !isNeeded()) return
        scheduled = true
        view.postOnAnimation(runnable)
    }

    fun cancel() {
        view.removeCallbacks(runnable)
        scheduled = false
    }

    private fun run() {
        scheduled = false
        if (!isNeeded()) return
        onFrame()
        scheduleIfNeeded()
    }
}
