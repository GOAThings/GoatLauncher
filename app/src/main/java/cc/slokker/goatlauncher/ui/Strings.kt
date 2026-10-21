package cc.slokker.goatlauncher.ui

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/** Textos em português e inglês, lado a lado: `t("Português", "English")`. Segue o idioma da TV. */
class Strings(val english: Boolean) {
    private fun t(pt: String, en: String) = if (english) en else pt

    val locale: Locale get() = if (english) Locale.UK else Locale.forLanguageTag("pt-PT")

    // ---- Início ----
    val goodMorning get() = t("Bom dia", "Good morning")
    val goodAfternoon get() = t("Boa tarde", "Good afternoon")
    val goodEvening get() = t("Boa noite", "Good evening")
    val search get() = t("Pesquisa", "Search")
    val network get() = t("Rede", "Network")
    val tabHome get() = t("Início", "Home")
    val tabApps get() = t("Apps", "Apps")
    val favorites get() = t("Favoritos", "Favorites")
    val moreApps get() = t("Mais apps", "More apps")
    val holdToRearrange get() = t("Mantém OK para reordenar", "Hold OK to rearrange")
    fun shelfLabel(app: String) = t("$app · continuar a ver", "$app · continue watching")
    fun shelfRecommended(app: String) = t("$app · recomendado", "$app · recommended")
    val continueNow get() = t("Continuar", "Continue")
    fun shelfMore(n: Int) = t("‹ › mais $n", "‹ › $n more")
    val settings get() = t("Definições", "Settings")
    fun continueIn(app: String) = t("Continuar a ver em $app", "Continue watching in $app")
    fun seasonEpisode(season: String?, episode: String?) = listOfNotNull(
        season?.let { t("T$it", "S$it") },
        episode?.let { t("E$it", "E$it") },
    ).joinToString(" ")
    fun minutesLeft(min: Long) = if (min >= 60) {
        t("faltam ${min / 60} h ${min % 60} min", "${min / 60} h ${min % 60} min left")
    } else {
        t("faltam $min min", "$min min left")
    }
    val noApps get() = t("Nenhuma app de TV instalada.", "No TV apps installed.")
    val cantOpen get() = t("Não foi possível abrir.", "Couldn't open that.")
    val noSearch get() = t("Esta TV não tem pesquisa do sistema.", "This TV has no system search.")

    // ---- Menu da app ----
    val move get() = t("Mover", "Move")
    val hide get() = t("Esconder do Início", "Hide from Home")
    val showOnHome get() = t("Mostrar no Início", "Show on Home")
    val hideContinue get() = t("Esconder o Continuar a ver", "Hide Continue watching")
    val showContinue get() = t("Mostrar o Continuar a ver", "Show Continue watching")
    val appInfo get() = t("Informação da app", "App info")
    val rename get() = t("Mudar nome", "Rename")
    val nameHint get() = t("Nome da app", "App name")
    val restoreName get() = t("Repor o nome", "Restore name")
    val changeIcon get() = t("Mudar ícone", "Change icon")
    val restoreIcon get() = t("Repor o original", "Restore original")
    val iconPack get() = t("Icon pack", "Icon pack")
    val openIconPack get() = t("Abrir no icon pack", "Open in the icon pack")
    val suggested get() = t("Feitos para esta app", "Made for this app")
    val allIcons get() = t("Todos os ícones", "All icons")
    val filterHint get() = t("Filtrar…", "Filter…")
    val noIcons get() = t("Nenhum ícone encontrado", "No icons found")
    val loading get() = t("A carregar…", "Loading…")
    val boxImages get() = t("Imagens da box", "Images on this box")
    val boxImagesHint get() = t("Pastas da box e discos USB ou de rede", "Folders on this box and USB or network drives")
    val fileManager get() = t("Gestor de ficheiros", "File manager")
    val internalStorage get() = t("Armazenamento interno", "Internal storage")
    val upFolder get() = t("Pasta acima", "Up one folder")
    val noImages get() = t("Sem imagens nesta pasta", "No images in this folder")
    val storagePermission get() = t("O launcher precisa de acesso às imagens da box.", "The launcher needs access to images on this box.")
    val allow get() = t("Permitir", "Allow")
    val imageFailed get() = t("Não foi possível usar esta imagem", "Couldn't use this image")
    val moveHint get() = t("Setas para mover · OK para terminar", "Arrows to move · OK when done")

    // ---- Definições ----
    val appearance get() = t("Aspeto", "Appearance")
    val wallpaper get() = t("Fundo", "Wallpaper")
    val wallpaperImage get() = t("Imagem escolhida", "Chosen image")
    val reddit get() = t("Reddit", "Reddit")
    val redditHint get() = t("Fotos do top da semana; muda a cada 30 minutos", "Top photos of the week; changes every 30 minutes")
    val otherSubreddit get() = t("Outro subreddit", "Other subreddit")
    val use get() = t("Usar", "Use")
    val clock24h get() = t("Relógio de 24 horas", "24-hour clock")
    val greeting get() = t("Saudação", "Greeting")
    val weather get() = t("Meteorologia", "Weather")
    val youtube get() = t("YouTube", "YouTube")
    val youtubeLink get() = t("Ligar à conta Google", "Link a Google account")
    val youtubeLinkHint get() = t("As tuas recomendações do YouTube no Continuar a ver", "Your YouTube recommendations in Continue watching")
    val youtubeOn get() = t("Recomendações do YouTube ligadas", "YouTube recommendations on")
    val youtubeOnHint get() = t("Aparecem no Continuar a ver da app de YouTube", "They show in the YouTube app's Continue watching")
    val youtubeUnlink get() = t("Desligar a conta Google", "Unlink the Google account")
    val youtubeGoTo get() = t("No telemóvel ou no computador, vai a", "On your phone or computer, go to")
    val youtubeEnterCode get() = t("e escreve o código", "and enter the code")
    val youtubeWaiting get() = t("À espera da confirmação… (um código novo aparece sozinho se este expirar)", "Waiting for confirmation… (a new code appears on its own if this one expires)")
    val youtubeRequesting get() = t("A pedir um código…", "Requesting a code…")
    val youtubeLinked get() = t("Conta Google ligada", "Google account linked")
    val youtubeError get() = t("Não foi possível falar com o YouTube. A tentar de novo…", "Couldn't reach YouTube. Retrying…")
    val showAppName get() = t("Nomes das apps", "App names")
    val continueStyle get() = t("Estilo do Continuar a ver", "Continue watching style")
    val styleRow get() = t("Fila de cartões", "Row of cards")
    val styleShelf get() = t("Destaque", "Spotlight")
    val styleDownUnder get() = t("Down Under", "Down Under")
    val continueWatching get() = t("Continuar a ver", "Continue watching")
    val continueWatchingHint get() = t("O que cada app publica no \"A seguir\" do Android TV", "What each app publishes to Android TV's \"Play next\"")
    val apps get() = t("Apps", "Apps")
    val hiddenApps get() = t("Apps escondidas", "Hidden apps")
    val homeGuard get() = t("Manter como ecrã inicial", "Keep as home screen")
    val homeGuardOn get() = t("Ligado: o Home abre sempre o GoatLauncher", "On: Home always opens GoatLauncher")
    val homeGuardOff get() = t("Desligado: liga \"GoatLauncher\" na Acessibilidade", "Off: turn on \"GoatLauncher\" in Accessibility")

    // Controlo parental
    val parental get() = t("Controlo parental", "Parental control")
    val parentalOff get() = t("Desligado: define um código para bloquear apps", "Off: set a code to lock apps")
    fun parentalOn(n: Int) = t(if (n == 1) "Ligado · 1 app bloqueada" else "Ligado · $n apps bloqueadas", if (n == 1) "On · 1 locked app" else "On · $n locked apps")
    fun unlockApp(app: String) = t("Código para abrir $app", "Code to open $app")
    val unlockSettings get() = t("Código para mexer nas definições", "Code to change settings")
    val pinHint get() = t("Números do comando, ou ↑ ↓ e OK", "Remote's number keys, or ↑ ↓ and OK")
    val wrongPin get() = t("Código errado", "Wrong code")
    val setPin get() = t("Definir código", "Set code")
    val changePin get() = t("Mudar código", "Change code")
    val newPin get() = t("Novo código", "New code")
    val repeatPin get() = t("Repete o código", "Repeat the code")
    val pinMismatch get() = t("Os códigos não coincidem: tenta de novo", "The codes don't match: try again")
    val pinSaved get() = t("Código guardado", "Code saved")
    val forgotPin get() = t("Esqueceste o código? Limpa os dados do GoatLauncher nas definições do Android.", "Forgot the code? Clear GoatLauncher's data in Android settings.")
    val lockedApps get() = t("Apps bloqueadas", "Locked apps")
    val lockedAppsHint get() = t("OK bloqueia ou desbloqueia a app", "OK locks or unlocks the app")
    fun lockedCount(n: Int) = t(if (n == 1) "1 app" else "$n apps", if (n == 1) "1 app" else "$n apps")
    val lockSettings get() = t("Proteger definições e organização", "Protect settings and layout")
    val lockSettingsHint get() = t("O código é pedido nas Definições e no menu das apps", "Asks for the code in Settings and in the app menu")
    val lockHours get() = t("Só a certas horas", "Only at certain hours")
    val lockAlways get() = t("As apps ficam bloqueadas sempre", "Apps are always locked")
    fun lockBetween(from: Int, to: Int) = t("Bloqueadas das ${from}h às ${to}h", "Locked from ${from}:00 to ${to}:00")
    val lockFrom get() = t("Das", "From")
    val lockTo get() = t("Até às", "Until")
    val hourHint get() = t("← → muda a hora", "← → changes the hour")
    val parentalDisable get() = t("Desligar controlo parental", "Turn off parental control")
    val parentalDisableHint get() = t("Apaga o código e os bloqueios", "Deletes the code and the locks")
    val settingsProtected get() = t("Definições protegidas", "Settings are protected")
    val settingsProtectedHint get() = t("O controlo parental pede o código para as mudar.", "Parental control asks for the code to change them.")
    val unlock get() = t("Desbloquear", "Unlock")
    val lockApp get() = t("Bloquear com código", "Lock with code")
    val unlockAppMenu get() = t("Tirar o bloqueio", "Remove the lock")

    // Arranque automático
    val startup get() = t("Ao ligar", "On power-on")
    val autostart get() = t("Abrir ao ligar a TV", "Open when the TV turns on")
    val autostartNone get() = t("Nenhuma: fica o ecrã inicial", "None: stay on the home screen")
    val autostartWake get() = t("Também ao acordar do standby", "Also when waking from standby")
    val autostartWakeHint get() = t("Precisa de \"Manter como ecrã inicial\" ligado", "Needs \"Keep as home screen\" on")
    val autostartDelay get() = t("Esperar antes de abrir", "Wait before opening")
    fun seconds(n: Int) = t(if (n == 0) "Logo" else "$n s", if (n == 0) "Right away" else "$n s")

    // Modo ambiente
    val idleMode get() = t("Modo ambiente", "Ambient mode")
    val idleOff get() = t("Desligado", "Off")
    fun idleAfter(min: Int) = t("Só o fundo e as horas, depois de $min min sem tocar no comando", "Just the wallpaper and the time, after $min min without the remote")
    val idleKeepAwake get() = t("Não deixar a TV adormecer", "Keep the TV awake")
    val idleKeepAwakeHint get() = t("No ecrã inicial: sem proteção de ecrã nem standby", "On the home screen: no screensaver or standby")

    // Fundo do Reddit
    val minWidth get() = t("Resolução mínima", "Minimum resolution")
    val anyResolution get() = t("Qualquer", "Any")
    fun hiddenCount(n: Int) = when (n) {
        0 -> t("Nenhuma", "None")
        1 -> t("1 app", "1 app")
        else -> t("$n apps", "$n apps")
    }
    val hiddenAppsHint get() = t("OK esconde ou mostra a app no ecrã inicial", "OK hides or shows the app on the home screen")
    val account get() = t("Conta Slokker", "Slokker account")
    val linkAccount get() = t("Ligar à conta Slokker", "Link to a Slokker account")
    val linkAccountHint get() = t("Guarda o fundo e a ordem das apps na conta", "Keeps your wallpaper and app order in your account")
    fun linkedAs(email: String?) = if (email != null) {
        t("Ligada a ${maskEmail(email)}", "Linked to ${maskEmail(email)}")
    } else {
        t("Ligada à conta", "Linked to an account")
    }
    val syncNow get() = t("Sincronizar agora", "Sync now")
    val synced get() = t("Definições sincronizadas", "Settings synced")
    val unlink get() = t("Desligar desta TV", "Unlink this TV")
    val unlinkHint get() = t("As definições ficam nesta TV, mas deixam de sincronizar", "Settings stay on this TV but stop syncing")
    val pressAgainToConfirm get() = t("Carrega de novo para confirmar", "Press again to confirm")
    val unlinked get() = t("Esta TV já não está ligada à conta", "This TV is no longer linked")
    val android get() = t("Android", "Android")
    val androidSettings get() = t("Definições do Android", "Android settings")
    fun version(v: String) = t("GoatLauncher $v", "GoatLauncher $v")
    val updates get() = t("Atualizações", "Updates")
    val checkUpdates get() = t("Procurar atualizações", "Check for updates")
    fun upToDate(v: String) = t("Versão $v · é a mais recente", "Version $v · up to date")
    val checking get() = t("A procurar…", "Checking…")
    fun updateReady(v: String) = t("Instalar a versão $v", "Install version $v")
    fun updateAvailable(v: String) = t("Nova versão $v: Definições → Atualizações", "New version $v: Settings → Updates")
    fun downloading(p: Int) = t("A descarregar… $p%", "Downloading… $p%")
    val installing get() = t("A instalar…", "Installing…")
    fun updateFailed(msg: String) = t("A atualização falhou: $msg", "Update failed: $msg")
    val allowInstall get() = t("Autoriza o GoatLauncher a instalar apps e tenta outra vez", "Allow GoatLauncher to install apps and try again")
    val updatesOnlyRelease get() = t("Só a versão publicada se atualiza", "Only the published version updates")
    val save get() = t("Guardar", "Save")
    val cancel get() = t("Cancelar", "Cancel")

    // ---- Ativação ----
    val activateTitle get() = t("Ligar à conta Slokker", "Link to a Slokker account")
    val activateGoTo get() = t("No telemóvel, vai a", "On your phone, go to")
    val activateEnterCode get() = t("e escreve o código", "and enter the code")
    fun activateExpires(min: Long, sec: Long) =
        t("Expira em %d:%02d. Depois aparece um novo sozinho.", "Expires in %d:%02d. A new one will appear on its own.").format(min, sec)
    val activateRequesting get() = t("A pedir um código…", "Requesting a code…")
    val activateQr get() = t("Ou lê o QR com a câmara", "Or scan the QR code with your camera")
    val activateQrDescription get() = t("Código QR para ligar", "QR code to link")
    fun activateServerError(code: Int) = t("O servidor respondeu $code. A tentar de novo…", "The server replied $code. Retrying…")
    val activateOffline get() = t("Sem ligação à internet. A tentar de novo…", "No internet connection. Retrying…")
    val activateBadResponse get() = t("Resposta inesperada do servidor. A tentar de novo…", "Unexpected server response. Retrying…")
    val linked get() = t("TV ligada à conta", "TV linked to your account")

    companion object {
        fun forSystem() = Strings(english = Locale.getDefault().language != "pt")
    }
}

val LocalStrings = staticCompositionLocalOf { Strings.forSystem() }

/**
 * Email mascarado para o ecrã ("n••••••@example.com"): não aparece por inteiro em gravações ou fotos da TV.
 * O Android 11 da SHIELD não tem desfocagem real, por isso esconde-se o nome.
 */
fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    if (at <= 0) return email
    return email.first() + "••••••" + email.substring(at)
}
