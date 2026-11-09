package cc.slokker.goatlauncher.ui.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import cc.slokker.goatlauncher.HomeGuardService
import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.ApodFeed
import cc.slokker.goatlauncher.data.DeviceSettings
import cc.slokker.goatlauncher.data.SystemIntents
import cc.slokker.goatlauncher.data.Wallpaper
import cc.slokker.goatlauncher.data.WikimediaFeed
import cc.slokker.goatlauncher.ui.LauncherViewModel
import cc.slokker.goatlauncher.ui.Strings
import cc.slokker.goatlauncher.ui.common.GlassRow
import cc.slokker.goatlauncher.ui.common.GoatIcons
import cc.slokker.goatlauncher.ui.common.Toggle
import cc.slokker.goatlauncher.ui.home.FocusEffectKind
import cc.slokker.goatlauncher.ui.home.HomeStyle
import cc.slokker.goatlauncher.ui.home.Wallpapers
import cc.slokker.goatlauncher.ui.state.UpdateState
import cc.slokker.goatlauncher.ui.theme.GoatShapes

/*
 * As linhas das páginas de definições "de valores" (Aparência, Fundo, Apps, Modo ambiente, Ao ligar, Geral, Controlo
 * parental e a página principal), cada uma descrita uma vez, pela ordem em que aparecem. SettingsList desenha-as.
 *
 * Uma definição nova (depois do campo em LauncherSettings) é uma entrada aqui e os seus textos:
 *     toggle("nav_sounds", Page.General, Strings::navSounds, hint = Strings::navSoundsHint,
 *         read = { it.account.navSounds }, write = account { a, on -> a.copy(navSounds = on) })
 * Uma página nova é uma entrada em Page (pai e título) e as suas linhas aqui. As linhas que não são um valor
 * (listas de apps, o código, a pesquisa de cidades…) são `custom` ou uma página à parte (ver SettingsScreen).
 */

/** As definições como estão: as da conta (iguais em todas as TVs) e as desta TV. É o que as linhas leem. */
internal data class SettingsValues(val account: AccountSettings, val device: DeviceSettings)

/** Como uma linha muda as definições: a conta ou esta TV, com o valor escolhido. */
internal class Write<T> private constructor(
    private val account: ((AccountSettings, T) -> AccountSettings)?,
    private val device: ((DeviceSettings, T) -> DeviceSettings)?,
) {
    /** O que [value] faz a [values] (para os testes). */
    fun apply(values: SettingsValues, value: T) = SettingsValues(
        account?.invoke(values.account, value) ?: values.account,
        device?.invoke(values.device, value) ?: values.device,
    )

    fun send(vm: LauncherViewModel, value: T) {
        account?.let { change -> vm.updateAccount { change(it, value) } }
        device?.let { change -> vm.updateDevice { change(it, value) } }
    }

    companion object {
        fun <T> account(change: (AccountSettings, T) -> AccountSettings) = Write(change, null)
        fun <T> device(change: (DeviceSettings, T) -> DeviceSettings) = Write(null, change)
    }
}

/** O que uma linha tem à mão ao desenhar-se. */
internal class SettingsScope(
    val vm: LauncherViewModel,
    val values: SettingsValues,
    val s: Strings,
    val context: Context,
    val onPage: (Page) -> Unit,
) {
    val account get() = values.account
    val device get() = values.device
}

/** Uma linha de uma página de definições. [key] é único na página. [visible] diz se aparece agora. */
internal sealed class Setting(val page: Page, val key: String, val visible: (SettingsValues) -> Boolean) {
    /** Recebe o foco ao abrir a página (se for a primeira que o pode receber). */
    open val focusable: Boolean get() = true

    /** Título de secção ("ECRÃ INICIAL"). */
    class Section(page: Page, key: String, val title: (Strings) -> String, visible: (SettingsValues) -> Boolean) :
        Setting(page, key, visible) {
        override val focusable get() = false
    }

    /** Liga/desliga. */
    class Toggle(
        page: Page,
        key: String,
        val title: (Strings) -> String,
        val subtitle: (@Composable SettingsScope.() -> String?)?,
        val read: (SettingsValues) -> Boolean,
        val write: Write<Boolean>,
        visible: (SettingsValues) -> Boolean,
    ) : Setting(page, key, visible)

    /** Um valor de uma lista curta: OK passa ao seguinte. */
    class Cycle<T>(
        page: Page,
        key: String,
        val title: (Strings) -> String,
        val options: (SettingsValues) -> List<T>,
        val read: (SettingsValues) -> T,
        /** O nome de um valor (pode depender das outras definições, ex.: a animação de cada efeito). */
        val label: (SettingsValues, T, Strings) -> String,
        val write: Write<T>,
        visible: (SettingsValues) -> Boolean,
    ) : Setting(page, key, visible)

    /** Uma opção de uma lista (o visto na escolhida). */
    class Choice(
        page: Page,
        key: String,
        val title: (Strings) -> String,
        val subtitle: ((Strings) -> String)?,
        val selected: (SettingsValues) -> Boolean,
        val write: Write<Unit>,
        val leading: (@Composable RowScope.() -> Unit)?,
        visible: (SettingsValues) -> Boolean,
    ) : Setting(page, key, visible)

    /** Leva a outra página ([to]) ou faz [action] (abrir as definições do Android…). */
    class Nav(
        page: Page,
        key: String,
        val title: (Strings) -> String,
        val subtitle: (@Composable SettingsScope.() -> String?)?,
        val icon: (() -> ImageVector)?,
        val accent: Boolean,
        val to: Page?,
        val action: (SettingsScope.() -> Unit)?,
        visible: (SettingsValues) -> Boolean,
    ) : Setting(page, key, visible)

    /** Uma linha à medida (amostras de cores, horas, o estado do serviço…). Recebe o modificador do foco. */
    class Custom(
        page: Page,
        key: String,
        override val focusable: Boolean,
        val content: @Composable SettingsScope.(Modifier) -> Unit,
        visible: (SettingsValues) -> Boolean,
    ) : Setting(page, key, visible)

    /** Um cartão com várias linhas (a página principal). */
    class Group(page: Page, key: String, val rows: List<Setting>, visible: (SettingsValues) -> Boolean) : Setting(page, key, visible)
}

private val Always: (SettingsValues) -> Boolean = { true }

private fun section(page: Page, key: String, title: (Strings) -> String, visible: (SettingsValues) -> Boolean = Always) =
    Setting.Section(page, key, title, visible)

private fun toggle(
    key: String,
    page: Page,
    title: (Strings) -> String,
    hint: ((Strings) -> String)? = null,
    subtitle: (@Composable SettingsScope.() -> String?)? = if (hint != null) {
        { hint(s) }
    } else {
        null
    },
    read: (SettingsValues) -> Boolean,
    write: Write<Boolean>,
    visible: (SettingsValues) -> Boolean = Always,
) = Setting.Toggle(page, key, title, subtitle, read, write, visible)

private fun <T> cycle(
    key: String,
    page: Page,
    title: (Strings) -> String,
    options: (SettingsValues) -> List<T>,
    read: (SettingsValues) -> T,
    label: (T, Strings) -> String = { v, _ -> v.toString() },
    write: Write<T>,
    visible: (SettingsValues) -> Boolean = Always,
    labelIn: (SettingsValues, T, Strings) -> String = { _, v, s -> label(v, s) },
) = Setting.Cycle(page, key, title, options, read, labelIn, write, visible)

private fun choice(
    key: String,
    page: Page,
    title: (Strings) -> String,
    selected: (SettingsValues) -> Boolean,
    write: Write<Unit>,
    hint: ((Strings) -> String)? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    visible: (SettingsValues) -> Boolean = Always,
) = Setting.Choice(page, key, title, hint, selected, write, leading, visible)

private fun nav(
    key: String,
    page: Page,
    title: (Strings) -> String,
    to: Page? = null,
    subtitle: (@Composable SettingsScope.() -> String?)? = null,
    icon: (() -> ImageVector)? = null,
    accent: Boolean = false,
    visible: (SettingsValues) -> Boolean = Always,
    action: (SettingsScope.() -> Unit)? = null,
) = Setting.Nav(page, key, title, subtitle, icon, accent, to, action, visible)

private fun custom(
    key: String,
    page: Page,
    focusable: Boolean = true,
    visible: (SettingsValues) -> Boolean = Always,
    content: @Composable SettingsScope.(Modifier) -> Unit,
) = Setting.Custom(page, key, focusable, content, visible)

private fun group(page: Page, key: String, vararg rows: Setting) = Setting.Group(page, key, rows.toList(), Always)

private fun <T> account(change: (AccountSettings, T) -> AccountSettings) = Write.account(change)
private fun <T> device(change: (DeviceSettings, T) -> DeviceSettings) = Write.device(change)

// ---- Resumos (a página principal e as páginas dizem o mesmo) ----

/** "Desligado" ou "Depois de 5 min". */
internal fun idleLabel(minutes: Int, s: Strings): String = if (minutes > 0) s.idleAfter(minutes) else s.idleOff

/** A língua escolhida pelo nome (na própria língua), ou "Automática". */
internal fun languageLabel(account: AccountSettings, s: Strings): String {
    val code = Strings.normalize(account.language)
    return Strings.LANGUAGES.firstOrNull { it.first == code }?.second ?: s.languageAuto
}

/** A app que abre ao ligar (pelo nome, se estiver instalada), ou "Nenhuma". */
@Composable
internal fun SettingsScope.autostartLabel(): String {
    val apps by vm.allApps.collectAsStateWithLifecycle()
    return device.autostart?.let { pkg -> apps?.firstOrNull { it.packageName == pkg }?.label ?: pkg } ?: s.autostartNone
}

// ---- O catálogo ----

internal object SettingsCatalog {
    private val main = Page.Main
    private val appearance = Page.Appearance
    private val wallpaper = Page.Wallpaper
    private val apps = Page.Apps
    private val ambient = Page.Ambient
    private val startup = Page.Startup
    private val general = Page.General
    private val parental = Page.Parental

    /** Todas as linhas, página a página, pela ordem do ecrã. */
    val all: List<Setting> = listOf(
        // ---- Página principal: cada secção numa página sua, cada uma com o que está escolhido ----
        group(
            main, "group_top",
            nav("appearance", main, Strings::appearance, Page.Appearance, icon = { GoatIcons.Palette }, accent = true,
                subtitle = { HomeStyle.of(account).label(s) }),
        ),
        section(main, "home_screen", Strings::homeScreen),
        group(
            main, "group_home",
            nav("wallpaper", main, Strings::wallpaper, Page.Wallpaper, icon = { GoatIcons.Image },
                subtitle = { wallpaperName(account.wallpaper, s) }),
            nav("apps", main, Strings::apps, Page.Apps, icon = { GoatIcons.Apps }, subtitle = { s.hiddenCount(device.hidden.size) }),
            nav("ambient", main, Strings::idleMode, Page.Ambient, icon = { GoatIcons.Moon }, subtitle = { idleLabel(account.idleMinutes, s) }),
        ),
        section(main, "tv", { "TV" }),
        group(
            main, "group_tv",
            nav("startup", main, Strings::startup, Page.Startup, icon = { GoatIcons.Power }, subtitle = { autostartLabel() }),
            nav("buttons", main, Strings::buttons, Page.Buttons, icon = { GoatIcons.Remote }, subtitle = {
                val n = device.buttons.size
                // Sem o serviço de acessibilidade os botões não chegam ao launcher: diz-se logo.
                when {
                    n == 0 -> s.buttonsNone
                    !HomeGuardService.isEnabled(LocalContext.current) -> "${s.buttonsCount(n)} · ${s.needsGuard}"
                    else -> s.buttonsCount(n)
                }
            }),
            nav("parental", main, Strings::parental, icon = { GoatIcons.Lock },
                subtitle = { if (device.pin == null) s.parentalOff else s.parentalOn(device.locked.size) },
                action = { if (device.pin == null) onPage(Page.PinSetup) else vm.withParentalUnlocked { onPage(Page.Parental) } }),
        ),
        section(main, "general", Strings::general),
        group(
            main, "group_general",
            nav("general", main, Strings::general, Page.General, icon = { GoatIcons.Tune }, subtitle = { languageLabel(account, s) }),
            nav("account", main, Strings::account, Page.Account, icon = { GoatIcons.Person }, subtitle = {
                val session by vm.session.collectAsStateWithLifecycle()
                session?.let { s.linkedAs(it.email) } ?: s.linkAccountHint
            }),
        ),
        section(main, "updates", Strings::updates),
        group(
            main, "group_updates",
            custom("update", main) { modifier -> UpdateRow(modifier) },
            nav("report", main, Strings::sendReport, Page.Report, icon = { GoatIcons.ArrowUp }, subtitle = {
                val report by vm.report.collectAsStateWithLifecycle()
                reportStatus(report, s)
            }),
            nav("android_settings", main, Strings::androidSettings, icon = { GoatIcons.Settings },
                action = { if (!SystemIntents.start(context, SystemIntents.androidSettings())) vm.toast(s.cantOpen) }),
        ),

        // ---- Aparência: a disposição, o estilo do "Continuar a ver", o cartão com foco e o ecrã inicial ----
        section(appearance, "layout", Strings::layout),
        choice("layout_android", appearance, { HomeStyle.ANDROID_TV_NAME }, hint = Strings::layoutAndroidHint,
            selected = { it.account.layout == AccountSettings.LAYOUT_ANDROID },
            write = account { a, _ -> a.copy(layout = AccountSettings.LAYOUT_ANDROID) }),
        choice("layout_apple", appearance, { HomeStyle.APPLE_TV_NAME }, hint = Strings::layoutAppleHint,
            selected = { it.account.layout != AccountSettings.LAYOUT_ANDROID },
            write = account { a, _ -> a.copy(layout = AccountSettings.LAYOUT_APPLE_TV) }),
        // O "Continuar a ver" logo a seguir à disposição: na Android escolhe-se o estilo (ou desliga-se); a Apple TV
        // tem sempre os cartões grandes, por isso só se liga ou desliga.
        section(appearance, "continue_style", Strings::continueStyle, visible = ::isAndroid),
    ) + HomeStyle.ANDROID.map { style ->
        choice(style.continueStyle!!, appearance, style::styleLabel, visible = ::isAndroid,
            selected = { it.account.showContinueWatching && it.account.continueStyle == style.continueStyle },
            write = account { a, _ -> a.copy(continueStyle = style.continueStyle, showContinueWatching = true) })
    } + listOf(
        choice("continue_off", appearance, Strings::idleOff, visible = ::isAndroid,
            selected = { !it.account.showContinueWatching }, write = account { a, _ -> a.copy(showContinueWatching = false) }),
        toggle("continue_watching", appearance, Strings::continueWatching, hint = Strings::continueWatchingHint,
            visible = { !isAndroid(it) }, read = { it.account.showContinueWatching },
            write = account { a, on -> a.copy(showContinueWatching = on) }),
        section(appearance, "focused_card", Strings::focusedCard),
        // OK passa ao efeito seguinte (pela ordem de FOCUS_EFFECTS, e volta ao primeiro); a pré-visualização ao lado mostra-o.
        cycle("focus_effect", appearance, Strings::focusEffect, options = { FocusEffectKind.entries },
            read = { FocusEffectKind.of(it.account.focusEffect) }, label = { kind, s -> kind.label(s) },
            write = account { a, kind -> a.copy(focusEffect = kind.id) }),
        // Cada efeito tem as suas animações (o vidro tem o brilho a passar e mais nenhuma).
        cycle("focus_animation", appearance, Strings::focusAnimation,
            visible = { effectOf(it).animations.isNotEmpty() }, options = { effectOf(it).animations },
            read = { effectOf(it).animationOf(it.account.focusAnimation) },
            labelIn = { values, anim, s -> effectOf(values).animationLabel(anim, s) },
            write = account { a, anim -> a.copy(focusAnimation = anim) }),
        // A cor não serve para "só sombra" nem para "escurecer os outros".
        custom("focus_color", appearance, visible = { effectOf(it).takesColor }) { _ ->
            FocusColorRow(account.focusColor, onPick = { c -> vm.updateAccount { it.copy(focusColor = c) } })
        },
        section(appearance, "home_screen", Strings::homeScreen),
        toggle("clock_24h", appearance, Strings::clock24h, read = { it.account.clock24h }, write = account { a, on -> a.copy(clock24h = on) }),
        toggle("greeting", appearance, Strings::greeting, read = { it.account.showGreeting }, write = account { a, on -> a.copy(showGreeting = on) }),
        toggle("weather", appearance, Strings::weather, read = { it.account.showWeather }, write = account { a, on -> a.copy(showWeather = on) }),
        toggle("app_name", appearance, Strings::showAppName, read = { it.account.showAppName }, write = account { a, on -> a.copy(showAppName = on) }),
        custom("icon_background", appearance) { _ ->
            IconBackgroundRow(account.iconBackground, onPick = { spec -> vm.updateAccount { it.copy(iconBackground = spec) } })
        },
    ) + Wallpapers.ALL.map { wp ->
        // ---- Fundo: os desenhos da app, as imagens da box, o intervalo e as fotos do dia ----
        choice(wp.id, wallpaper, wp::name,
            selected = { it.account.wallpaper.type == Wallpaper.TYPE_BUILTIN && Wallpapers.byId(it.account.wallpaper.value).id == wp.id },
            write = account { a, _ -> a.copy(wallpaper = Wallpaper(Wallpaper.TYPE_BUILTIN, wp.id)) },
            leading = {
                Box(
                    Modifier
                        .size(64.dp, 36.dp)
                        .clip(GoatShapes.Small)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), GoatShapes.Small)
                        .drawWithCache { onDrawBehind { wp.draw(this) } },
                )
            })
    } + listOf(
        // Uma imagem da box (copiada para a app), uma pasta inteira, ou do gestor de ficheiros do sistema.
        custom("box_images", wallpaper) { modifier ->
            val current = account.wallpaper
            val isImage = current.type == Wallpaper.TYPE_FILE || current.type == Wallpaper.TYPE_URL || current.type == Wallpaper.TYPE_FOLDER
            GlassRow(
                title = s.boxImages,
                subtitle = if (isImage) wallpaperName(current, s) else s.boxImagesHint,
                leading = { Icon(GoatIcons.Folder, null, Modifier.size(20.dp)) },
                onClick = { onPage(Page.WallpaperFiles) },
                trailing = { if (isImage) CheckMark() else Chevron() },
                modifier = modifier,
            )
        },
        custom("file_manager", wallpaper) { modifier ->
            val canBrowseSystem = remember { Intent(Intent.ACTION_GET_CONTENT).setType("image/*").resolveActivity(context.packageManager) != null }
            val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(vm::setWallpaperFromImage) }
            if (canBrowseSystem) {
                GlassRow(
                    title = s.fileManager,
                    leading = { Icon(GoatIcons.Image, null, Modifier.size(20.dp)) },
                    onClick = { systemPicker.launch("image/*") },
                    modifier = modifier,
                )
            }
        },
        // De quanto em quanto tempo muda a foto (fotos do dia e pasta). OK passa ao seguinte: 5 min → … → um dia → 5 min.
        cycle("wallpaper_minutes", wallpaper, Strings::rotateEvery, options = { AccountSettings.WALLPAPER_MINUTES },
            read = { it.account.wallpaperMinutes }, label = { min, s -> s.rotateInterval(min) },
            write = account { a, min -> a.copy(wallpaperMinutes = min) }),
        // Fotos do dia, sem conta nem chave: a imagem do dia do Wikimedia Commons e a da NASA. A resolução mínima
        // vale para as duas.
        section(wallpaper, "photos_of_the_day", Strings::photosOfTheDay),
        choice("wikimedia", wallpaper, { WIKIMEDIA_NAME }, hint = Strings::wikimediaHint,
            selected = { it.account.wallpaper.type == Wallpaper.TYPE_WIKIMEDIA },
            write = account { a, _ -> a.copy(wallpaper = Wallpaper(Wallpaper.TYPE_WIKIMEDIA, WikimediaFeed.ID)) }),
        choice("nasa", wallpaper, { NASA_NAME }, hint = Strings::nasaHint,
            selected = { it.account.wallpaper.type == Wallpaper.TYPE_NASA },
            write = account { a, _ -> a.copy(wallpaper = Wallpaper(Wallpaper.TYPE_NASA, ApodFeed.ID)) }),
        // OK passa à seguinte: qualquer → Full HD → 2K → 4K → qualquer.
        cycle("photo_min_width", wallpaper, Strings::minWidth, options = { AccountSettings.PHOTO_MIN_WIDTHS },
            read = { it.account.photoMinWidth }, label = ::resolutionName,
            write = account { a, px -> a.copy(photoMinWidth = px) }),

        // ---- Apps: as escondidas e "Manter como ecrã inicial" ----
        nav("hidden_apps", apps, Strings::hiddenApps, Page.HiddenApps, subtitle = { s.hiddenCount(device.hidden.size) }),
        // Liga-se nas definições de Acessibilidade do Android (uma app não o pode ligar sozinha).
        custom("home_guard", apps) { modifier ->
            val guard = rememberGuardEnabled()
            val stuck by vm.guardStuck.collectAsStateWithLifecycle()
            GlassRow(
                title = s.homeGuard,
                subtitle = when {
                    guard && stuck -> s.guardStuck
                    guard -> s.homeGuardOn
                    else -> s.homeGuardOff
                },
                onClick = { if (guard && stuck) vm.repairGuard() else SystemIntents.start(context, SystemIntents.accessibilitySettings()) },
                trailing = { Toggle(guard && !stuck) },
                modifier = modifier,
            )
        },

        // ---- Modo ambiente: quando entra, o que mostra, o protetor de ecrã e não deixar a TV adormecer ----
        // OK passa ao tempo seguinte: desligado → 1 → 2 → 5 → 10 → 30 min → desligado.
        cycle("idle_minutes", ambient, Strings::idleMode, options = { AccountSettings.IDLE_MINUTES },
            read = { it.account.idleMinutes }, label = ::idleLabel, write = account { a, min -> a.copy(idleMinutes = min) }),
        // As fotos (o fundo com as horas) ou o relógio a ressaltar; vale também para o protetor de ecrã.
        cycle("ambient_style", ambient, Strings::ambientStyle, options = { AccountSettings.AMBIENT_STYLES },
            read = { it.account.ambientStyle },
            label = { style, s -> if (style == AccountSettings.AMBIENT_BOUNCE) s.ambientBounce else s.ambientPhotos },
            write = account { a, style -> a.copy(ambientStyle = style) }),
        nav("screensaver", ambient, Strings::screensaver, subtitle = { s.screensaverHint },
            action = { SystemIntents.openScreensaverSettings(context) }),
        toggle("idle_keep_awake", ambient, Strings::idleKeepAwake, hint = Strings::idleKeepAwakeHint,
            visible = { it.account.idleMinutes > 0 }, read = { it.account.idleKeepAwake },
            write = account { a, on -> a.copy(idleKeepAwake = on) }),

        // ---- Ao ligar: a app que abre quando a TV liga (e acorda) e quanto tempo depois ----
        nav("autostart", startup, Strings::autostart, Page.Autostart, subtitle = { autostartLabel() }),
        toggle("autostart_wake", startup, Strings::autostartWake, hint = Strings::autostartWakeHint,
            visible = { it.device.autostart != null }, read = { it.device.autostartWake },
            write = device { d, on -> d.copy(autostartWake = on) }),
        cycle("autostart_delay", startup, Strings::autostartDelay, visible = { it.device.autostart != null },
            options = { DeviceSettings.AUTOSTART_DELAYS }, read = { it.device.autostartDelay }, label = { n, s -> s.seconds(n) },
            write = device { d, n -> d.copy(autostartDelay = n) }),

        // ---- Geral: a língua, onde fica a TV (o tempo e o dia/noite), a noite automática e os sons ----
        nav("language", general, Strings::language, Page.Language, subtitle = { languageLabel(account, s) }),
        nav("location", general, Strings::location, Page.Location,
            subtitle = { device.location?.name ?: vm.ipCity()?.let(s::locationAutoCity) ?: s.locationAuto }),
        toggle("night_auto", general, Strings::nightAuto, read = { it.account.nightAuto },
            write = account { a, on -> a.copy(nightAuto = on) },
            // Com a localização, do pôr ao nascer do sol de hoje; sem ela, a hora fixa.
            subtitle = {
                val sun by vm.sunTimes.collectAsStateWithLifecycle()
                sun?.let { (rise, set) -> s.nightAutoSun(sunClock(set, s, account.clock24h), sunClock(rise, s, account.clock24h)) }
                    ?: s.nightAutoHint
            }),
        toggle("nav_sounds", general, Strings::navSounds, hint = Strings::navSoundsHint,
            read = { it.account.navSounds }, write = account { a, on -> a.copy(navSounds = on) }),

        // ---- Controlo parental ligado: as apps bloqueadas, a proteção das definições, o horário e o código ----
        nav("locked_apps", parental, Strings::lockedApps, Page.LockedApps, subtitle = { s.lockedCount(device.locked.size) }),
        toggle("lock_settings", parental, Strings::lockSettings, hint = Strings::lockSettingsHint,
            read = { it.device.lockSettings }, write = device { d, on -> d.copy(lockSettings = on) }),
        toggle("lock_hours", parental, Strings::lockHours, read = { it.device.lockFrom != null },
            // Ligar começa nas 20 → 8; desligar volta a "sempre".
            write = device { d, on -> if (on) d.copy(lockFrom = 20, lockTo = 8) else d.copy(lockFrom = null, lockTo = null) },
            subtitle = {
                val from = device.lockFrom
                val to = device.lockTo
                if (from != null && to != null) s.lockBetween(from, to) else s.lockAlways
            }),
        custom("lock_from", parental, visible = ::hasLockHours) { _ ->
            val to = device.lockTo
            HourRow(s.lockFrom, device.lockFrom ?: 0) { h -> vm.updateDevice { it.copy(lockFrom = h, lockTo = to) } }
        },
        custom("lock_to", parental, visible = ::hasLockHours) { _ ->
            val from = device.lockFrom
            HourRow(s.lockTo, device.lockTo ?: 0) { h -> vm.updateDevice { it.copy(lockFrom = from, lockTo = h) } }
        },
        nav("change_pin", parental, Strings::changePin, Page.PinSetup),
        custom("parental_off", parental) { modifier ->
            ConfirmRow(s.parentalDisable, s.parentalDisableHint, onConfirm = {
                vm.setPin(null)
                onPage(Page.Main)
            }, modifier = modifier)
        },
    )

    /** As linhas de [page], pela ordem do ecrã (vazia nas páginas que se desenham à parte). */
    fun on(page: Page): List<Setting> = all.filter { it.page == page }

    private fun isAndroid(values: SettingsValues) = values.account.layout == AccountSettings.LAYOUT_ANDROID
    private fun effectOf(values: SettingsValues) = FocusEffectKind.of(values.account.focusEffect)
    private fun hasLockHours(values: SettingsValues) = values.device.lockFrom != null && values.device.lockTo != null
}

/** A linha das atualizações: verificar, a descarregar, instalar a versão nova (com as notas). */
@Composable
private fun SettingsScope.UpdateRow(modifier: Modifier) {
    val update by vm.update.collectAsStateWithLifecycle()
    val (title, subtitle) = when (val u = update) {
        is UpdateState.Available -> s.updateReady(u.release.version) to u.release.notes.takeIf { it.isNotBlank() }
        is UpdateState.Downloading -> s.downloading((u.progress * 100).toInt()) to null
        is UpdateState.Installing -> s.installing to null
        UpdateState.Checking -> s.checking to null
        UpdateState.UpToDate -> s.checkUpdates to s.upToDate(vm.appVersion)
        is UpdateState.Failed -> s.checkUpdates to s.updateFailed(u.message)
        UpdateState.Idle -> s.checkUpdates to s.version(vm.appVersion)
    }
    GlassRow(
        title = title,
        subtitle = subtitle,
        leading = { IconTile(GoatIcons.Download) },
        onClick = { if (update is UpdateState.Available) vm.installUpdate() else vm.checkForUpdate(manual = true) },
        modifier = modifier,
    )
}
