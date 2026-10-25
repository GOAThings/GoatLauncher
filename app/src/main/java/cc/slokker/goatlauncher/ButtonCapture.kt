package cc.slokker.goatlauncher

import android.view.KeyEvent

/**
 * Botões do comando: quais se podem usar para abrir apps e o "carrega no botão" das Definições. A captura é
 * feita pelo HomeGuardService (o único que vê botões que o sistema trata antes das apps, como o Netflix).
 */
object ButtonCapture {
    /** À espera de um botão: recebe (keyCode, scanCode). Null quando não há captura em curso. */
    @Volatile
    var listener: ((Int, Int) -> Unit)? = null
        set(value) {
            field = value
            // O serviço só vê as teclas enquanto fazem falta.
            HomeGuardService.instance?.updateKeyFilter()
        }

    /** Os que nunca mudam: navegação, energia, volume, controlo da reprodução, canais e números. */
    private val PROTECTED = setOf(
        KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP, KeyEvent.KEYCODE_SOFT_SLEEP,
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MUTE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_UNKNOWN,
    )

    fun allowed(keyCode: Int): Boolean =
        keyCode !in PROTECTED && keyCode !in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 &&
            keyCode !in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9
}
