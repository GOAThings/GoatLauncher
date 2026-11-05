package cc.slokker.goatlauncher.data

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Calendar

/**
 * Controlo parental, como no Projectivy: um código de 4 dígitos para abrir as apps escolhidas (sempre ou só
 * entre certas horas) e, se a pessoa quiser, para mexer nas Definições e na organização das apps.
 *
 * O código guarda-se com sal e SHA-256 (nas definições desta TV, que também vão para a conta). Depois de o
 * acertar, não volta a ser pedido durante [GRACE_MS]. Muitas tentativas erradas fazem esperar ([PinAttempts]).
 * Esquecido o código: limpar os dados da app.
 */
object ParentalLock {
    private const val GRACE_MS = 15 * 60_000L

    @Volatile
    private var unlockedUntil = 0L

    fun encode(pin: String): String {
        val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }.toHex()
        return "$salt:${hash(salt, pin)}"
    }

    fun matches(stored: String?, pin: String): Boolean {
        val salt = stored?.substringBefore(':', "")?.takeIf { it.isNotEmpty() } ?: return false
        return MessageDigest.isEqual(hash(salt, pin).toByteArray(), stored.substringAfter(':').toByteArray())
    }

    /** O código foi acertado: nos próximos minutos não se pede outra vez. */
    fun grant() {
        unlockedUntil = SystemClock.elapsedRealtime() + GRACE_MS
    }

    private fun unlocked() = SystemClock.elapsedRealtime() < unlockedUntil

    /** Abrir [pkg] pede o código agora? */
    fun appLocked(device: DeviceSettings, pkg: String): Boolean =
        device.pin != null && pkg in device.locked && inWindow(device) && !unlocked()

    /** As definições do próprio controlo parental pedem sempre o código (senão bastava desligá-lo). */
    fun parentalLocked(device: DeviceSettings): Boolean = device.pin != null && !unlocked()

    /** Mexer nas Definições ou na organização pede o código agora? (Sempre, não só a certas horas.) */
    fun settingsLocked(device: DeviceSettings): Boolean = device.pin != null && device.lockSettings && !unlocked()

    private fun inWindow(device: DeviceSettings): Boolean {
        val from = device.lockFrom ?: return true
        val to = device.lockTo ?: return true
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        // 20 → 8 passa a meia-noite; 9 → 17 não.
        return if (from <= to) hour in from until to else hour >= from || hour < to
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}

/**
 * As tentativas erradas do código seguidas e quando foi a última ([failedAt], em elapsedRealtime). As primeiras não
 * custam nada (enganos); depois, cada uma faz esperar mais, até ao máximo. Só um código certo volta ao início.
 */
internal data class PinAttempts(val failures: Int = 0, val failedAt: Long = 0L) {
    val delayMs: Long
        get() = if (failures <= FREE) 0L else DELAYS_MS[(failures - FREE - 1).coerceAtMost(DELAYS_MS.lastIndex)]

    /** Quanto falta esperar. Com [now] antes de [failedAt] a TV reiniciou e não se sabe: conta a espera inteira. */
    fun remainingMs(now: Long): Long =
        if (now < failedAt) delayMs else (failedAt + delayMs - now).coerceAtLeast(0L)

    fun failed(now: Long) = PinAttempts(failures + 1, now)

    private companion object {
        const val FREE = 3
        val DELAYS_MS = longArrayOf(30_000, 60_000, 2 * 60_000, 5 * 60_000, 15 * 60_000, 30 * 60_000, 60 * 60_000)
    }
}

/**
 * [PinAttempts] guardadas só nesta TV, para valerem entre pedidos do código e depois de reiniciar a app ou a TV. O
 * relógio é o elapsedRealtime: mudar a hora do sistema não encurta a espera, e limpar os dados também apaga o código.
 */
class PinAttemptStore(context: Context) {
    private val prefs = context.getSharedPreferences("pin_attempts", Context.MODE_PRIVATE)
    private var attempts = PinAttempts(prefs.getInt(KEY_FAILURES, 0), prefs.getLong(KEY_FAILED_AT, 0L))

    /** Quanto falta para se poder tentar outra vez (0 = já). */
    fun waitMs(): Long {
        val now = SystemClock.elapsedRealtime()
        val waiting = attempts.remainingMs(now)
        // A TV reiniciou: a espera volta a contar a partir de agora.
        if (now < attempts.failedAt) save(attempts.copy(failedAt = now))
        return waiting
    }

    fun failed() = save(attempts.failed(SystemClock.elapsedRealtime()))

    fun reset() {
        if (attempts != PinAttempts()) save(PinAttempts())
    }

    private fun save(new: PinAttempts) {
        attempts = new
        // Gravado já: desligar logo a seguir a errar não apaga a tentativa.
        prefs.edit(commit = true) {
            putInt(KEY_FAILURES, new.failures)
            putLong(KEY_FAILED_AT, new.failedAt)
        }
    }

    private companion object {
        const val KEY_FAILURES = "failures"
        const val KEY_FAILED_AT = "failed_at"
    }
}
