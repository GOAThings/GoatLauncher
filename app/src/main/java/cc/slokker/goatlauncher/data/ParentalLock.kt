package cc.slokker.goatlauncher.data

import android.os.SystemClock
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Calendar

/**
 * Controlo parental, como no Projectivy: um código de 4 dígitos para abrir as apps escolhidas (sempre ou só
 * entre certas horas) e, se a pessoa quiser, para mexer nas Definições e na organização das apps.
 *
 * O código guarda-se com sal e SHA-256 (nas definições desta TV, que também vão para a conta). Depois de o
 * acertar, não volta a ser pedido durante [GRACE_MS]. Esquecido o código: limpar os dados da app.
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
