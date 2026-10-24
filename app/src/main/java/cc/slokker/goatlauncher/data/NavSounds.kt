package cc.slokker.goatlauncher.data

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.view.KeyEvent
import cc.slokker.goatlauncher.R

/**
 * Sons discretos ao navegar no launcher (Aparência → Sons de navegação): um tique ao mover com as setas e um
 * clique no OK. Só se carregam quando a opção está ligada.
 */
class NavSounds(private val context: Context) {
    private var pool: SoundPool? = null
    private var move = 0
    private var select = 0
    private var lastMove = 0L

    fun setEnabled(on: Boolean) {
        if (on && pool == null) {
            pool = SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
                .also {
                    move = it.load(context, R.raw.nav_move, 1)
                    select = it.load(context, R.raw.nav_select, 1)
                }
        } else if (!on) {
            pool?.release()
            pool = null
        }
    }

    /** Toca o som de [event] (setas ao carregar, OK ao largar); com a tecla presa, no máximo de [MOVE_GAP_MS] em [MOVE_GAP_MS]. */
    fun onKey(event: KeyEvent) {
        val p = pool ?: return
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.action != KeyEvent.ACTION_DOWN) return
                val now = SystemClock.uptimeMillis()
                if (now - lastMove < MOVE_GAP_MS) return
                lastMove = now
                p.play(move, VOLUME, VOLUME, 1, 0, 1f)
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) p.play(select, VOLUME, VOLUME, 1, 0, 1f)
            }
        }
    }

    private companion object {
        const val MOVE_GAP_MS = 70L
        const val VOLUME = 0.35f
    }
}
