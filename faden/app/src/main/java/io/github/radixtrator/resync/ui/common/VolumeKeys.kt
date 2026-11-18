package io.github.radixtrator.resync.ui.common

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Lends the hardware volume buttons to a page, for Comment options › "Volume key navigation". MainActivity hands every
 * key event to [handle] first; while a page holds the buttons ([VolumeKeysEffect]), a press calls its [Handler] and the
 * volume stays as it is. With no page holding them, they change the volume as usual. Main thread only.
 */
object VolumeKeys {
    /** What a press does: volume down is [next], volume up is [previous]. */
    interface Handler {
        fun next()
        fun previous()
    }

    private val handlers = ArrayList<Handler>()

    /** The newest page to ask gets the buttons; the one under it gets them back when it lets go. */
    fun hold(handler: Handler) {
        handlers += handler
    }

    fun release(handler: Handler) {
        handlers -= handler
    }

    /**
     * Whether [event] was a volume button a page took. The press acts on key down (not on its repeats, so holding the
     * button doesn't race through the thread); its key up is swallowed too, so the system shows no volume panel.
     */
    fun handle(event: KeyEvent): Boolean = handle(event.keyCode, event.action, event.repeatCount)

    /** [handle] on the event's parts, which unit tests can make (a KeyEvent needs the Android runtime). */
    fun handle(keyCode: Int, action: Int, repeatCount: Int): Boolean {
        val handler = handlers.lastOrNull() ?: return false
        val down = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> true
            KeyEvent.KEYCODE_VOLUME_UP -> false
            else -> return false
        }
        if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
            if (down) handler.next() else handler.previous()
        }
        return true
    }
}

/** Holds the volume buttons for [onNext] / [onPrevious] while this is composed and [enabled]. */
@Composable
fun VolumeKeysEffect(enabled: Boolean, onNext: () -> Unit, onPrevious: () -> Unit) {
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val handler = object : VolumeKeys.Handler {
            override fun next() = next()

            override fun previous() = previous()
        }
        VolumeKeys.hold(handler)
        onDispose { VolumeKeys.release(handler) }
    }
}
