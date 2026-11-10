package cc.slokker.goatlauncher.data

/**
 * As perguntas do controlo parental com as definições já lidas: é por aqui que o ecrã, o AutoStart e o
 * HomeGuardService decidem se pedem o código. Enquanto a cópia do disco não estiver lida não se sabe se há código, e
 * "por ler" nunca conta como "sem código": cada pergunta espera por essa leitura (logo, se já estiver).
 *
 * Antes cada um esperava à mão (e um esqueceu-se); as regras em si estão no [ParentalLock].
 */
class ParentalGate(
    private val settings: SettingsRepository,
    /** O tempo de ecrã de hoje já acabou? (ver ScreenTime) */
    private val timeUp: (DeviceSettings) -> Boolean = { false },
) {
    /** Abrir [pkg] pede o código agora? */
    suspend fun appLocked(pkg: String): Boolean {
        settings.awaitReady()
        val device = settings.device.value
        return ParentalLock.appLocked(device, pkg, timeUp(device))
    }

    /** O tempo de ecrã de hoje já acabou (e o código não foi acertado há pouco)? */
    suspend fun timeUpNow(): Boolean {
        settings.awaitReady()
        return timeUp(settings.device.value) && !ParentalLock.unlocked()
    }

    /** Mexer nas Definições ou na organização pede o código agora? */
    suspend fun settingsLocked(): Boolean {
        settings.awaitReady()
        return ParentalLock.settingsLocked(settings.device.value)
    }

    /** Entrar nas definições do próprio controlo parental (ou ligar outra conta) pede o código agora? */
    suspend fun parentalLocked(): Boolean {
        settings.awaitReady()
        return ParentalLock.parentalLocked(settings.device.value)
    }
}
