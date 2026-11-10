package cc.slokker.goatlauncher.ui.common

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.FocusRequester

/** Pede o foco sem rebentar se o elemento ainda não estiver no ecrã (devolve false). */
fun FocusRequester.requestFocusSafely(): Boolean = try {
    requestFocus()
    true
} catch (e: IllegalStateException) {
    false
}

/** Quantos frames se tenta pôr o foco num elemento que ainda pode não estar composto (meio segundo a 60 Hz). */
const val FOCUS_FRAMES = 30

/**
 * A maneira de pôr o foco no que acabou de abrir (uma página, um menu, o código, um banner): o elemento só tem o
 * FocusRequester depois de composto, por isso tenta-se a cada frame, até [frames]. Desiste se [stop] disser (ex.: o
 * código abriu por cima e ficou com o foco). Devolve true se conseguiu.
 */
suspend fun FocusRequester.focusSoon(frames: Int = FOCUS_FRAMES, stop: () -> Boolean = { false }): Boolean {
    repeat(frames) {
        withFrameNanos { }
        if (stop()) return false
        if (requestFocusSafely()) return true
    }
    return false
}

/** As teclas OK do comando: o centro do D-pad e os Enter. (O A e o Select do comando de jogos só contam a mover apps.) */
val OkKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
