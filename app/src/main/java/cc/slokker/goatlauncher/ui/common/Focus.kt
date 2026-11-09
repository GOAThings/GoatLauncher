package cc.slokker.goatlauncher.ui.common

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/** Pede o foco sem rebentar se o elemento ainda não estiver no ecrã (devolve false). */
fun androidx.compose.ui.focus.FocusRequester.requestFocusSafely(): Boolean = try {
    requestFocus()
    true
} catch (e: IllegalStateException) {
    false
}

/** Pede o foco a [requester] durante alguns frames (o elemento pode ainda não estar composto). */
suspend fun focusSoon(requester: FocusRequester) {
    repeat(5) {
        withFrameNanos { }
        if (requester.requestFocusSafely()) return
    }
}
