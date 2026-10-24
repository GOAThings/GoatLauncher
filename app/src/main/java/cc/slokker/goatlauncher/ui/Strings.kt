package cc.slokker.goatlauncher.ui

import androidx.core.os.ConfigurationCompat
import android.content.Context
import android.content.res.Configuration
import android.text.TextUtils
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.os.LocaleListCompat
import cc.slokker.goatlauncher.R
import java.util.Locale

/**
 * Os textos do launcher. Estão em `strings_ui.xml`: o inglês em `res/values` e cada língua na sua pasta (`values-fr`…)
 * (as mesmas 15 do site). A língua é a escolhida nas Definições (por app, com o AppCompat) ou, em
 * "Automático", a do sistema; uma que não exista cai no inglês.
 */
class Strings(context: Context) {
    private val res = context.resources
    val locale: Locale = ConfigurationCompat.getLocales(res.configuration)[0] ?: Locale.getDefault()

    /** Línguas da direita para a esquerda (árabe): o Compose espelha o ecrã sozinho. */
    val rtl: Boolean = TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL

    private fun s(id: Int): String = res.getString(id)
    private fun s(id: Int, vararg args: Any?): String = res.getString(id, *args)
    private fun p(id: Int, n: Int, vararg args: Any?): String = res.getQuantityString(id, n, *args)

    val goodMorning get() = s(R.string.good_morning)
    val goodAfternoon get() = s(R.string.good_afternoon)
    val goodEvening get() = s(R.string.good_evening)
    val search get() = s(R.string.search)
    val network get() = s(R.string.network)
    val tabHome get() = s(R.string.tab_home)
    val tabApps get() = s(R.string.tab_apps)
    val favorites get() = s(R.string.favorites)
    val moreApps get() = s(R.string.more_apps)
    val holdToRearrange get() = s(R.string.hold_to_rearrange)
    fun shelfLabel(app: String) = s(R.string.shelf_label, app)
    fun shelfRecommended(app: String) = s(R.string.shelf_recommended, app)
    val continueNow get() = s(R.string.continue_now)
    fun shelfMore(n: Int) = s(R.string.shelf_more, n)
    val settings get() = s(R.string.settings)
    fun continueIn(app: String) = s(R.string.continue_in, app)
    val noApps get() = s(R.string.no_apps)
    val cantOpen get() = s(R.string.cant_open)
    val noSearch get() = s(R.string.no_search)
    val move get() = s(R.string.move)
    val hide get() = s(R.string.hide)
    val showOnHome get() = s(R.string.show_on_home)
    val hideContinue get() = s(R.string.hide_continue)
    val showContinue get() = s(R.string.show_continue)
    val appInfo get() = s(R.string.app_info)
    val rename get() = s(R.string.rename)
    val nameHint get() = s(R.string.name_hint)
    val restoreName get() = s(R.string.restore_name)
    val changeIcon get() = s(R.string.change_icon)
    val restoreIcon get() = s(R.string.restore_icon)
    val iconPack get() = s(R.string.icon_pack)
    val openIconPack get() = s(R.string.open_icon_pack)
    val suggested get() = s(R.string.suggested)
    val allIcons get() = s(R.string.all_icons)
    val filterHint get() = s(R.string.filter_hint)
    val noIcons get() = s(R.string.no_icons)
    val loading get() = s(R.string.loading)
    val boxImages get() = s(R.string.box_images)
    val boxImagesHint get() = s(R.string.box_images_hint)
    val fileManager get() = s(R.string.file_manager)
    val internalStorage get() = s(R.string.internal_storage)
    val upFolder get() = s(R.string.up_folder)
    val noImages get() = s(R.string.no_images)
    val storagePermission get() = s(R.string.storage_permission)
    val allow get() = s(R.string.allow)
    val imageFailed get() = s(R.string.image_failed)
    val moveHint get() = s(R.string.move_hint)
    val appearance get() = s(R.string.appearance)
    val wallpaper get() = s(R.string.wallpaper)
    val wallpaperImage get() = s(R.string.wallpaper_image)
    val reddit get() = s(R.string.reddit)
    val redditHint get() = s(R.string.reddit_hint)
    val otherSubreddit get() = s(R.string.other_subreddit)
    val use get() = s(R.string.use)
    val clock24h get() = s(R.string.clock24h)
    val greeting get() = s(R.string.greeting)
    val weather get() = s(R.string.weather)
    val youtube get() = s(R.string.youtube)
    val youtubeLink get() = s(R.string.youtube_link)
    val youtubeLinkHint get() = s(R.string.youtube_link_hint)
    val youtubeOn get() = s(R.string.youtube_on)
    val youtubeOnHint get() = s(R.string.youtube_on_hint)
    val youtubeUnlink get() = s(R.string.youtube_unlink)
    val youtubeGoTo get() = s(R.string.youtube_go_to)
    val youtubeEnterCode get() = s(R.string.youtube_enter_code)
    val youtubeWaiting get() = s(R.string.youtube_waiting)
    val youtubeRequesting get() = s(R.string.youtube_requesting)
    val youtubeLinked get() = s(R.string.youtube_linked)
    val youtubeError get() = s(R.string.youtube_error)
    val showAppName get() = s(R.string.show_app_name)
    val continueStyle get() = s(R.string.continue_style)
    val styleRow get() = s(R.string.style_row)
    val styleShelf get() = s(R.string.style_shelf)
    val layout get() = s(R.string.layout)
    val addToFavorites get() = s(R.string.add_to_favorites)
    val removeFromFavorites get() = s(R.string.remove_from_favorites)
    val uninstall get() = s(R.string.uninstall)
    val layoutAndroidHint get() = s(R.string.layout_android_hint)
    val layoutAppleHint get() = s(R.string.layout_apple_hint)
    val general get() = s(R.string.general)
    val focusedCard get() = s(R.string.focused_card)
    val focusEffect get() = s(R.string.focus_effect)
    val effectBorder get() = s(R.string.effect_border)
    val effectGlow get() = s(R.string.effect_glow)
    val effectShadow get() = s(R.string.effect_shadow)
    val effectGlass get() = s(R.string.effect_glass)
    val effectReflection get() = s(R.string.effect_reflection)
    val effectAurora get() = s(R.string.effect_aurora)
    val effectViewfinder get() = s(R.string.effect_viewfinder)
    val effectUnderline get() = s(R.string.effect_underline)
    val effectRing get() = s(R.string.effect_ring)
    val effectDim get() = s(R.string.effect_dim)
    val animSpin get() = s(R.string.anim_spin)
    val focusAnimation get() = s(R.string.focus_animation)
    val animNone get() = s(R.string.anim_none)
    val animPulse get() = s(R.string.anim_pulse)
    val animBreath get() = s(R.string.anim_breath)
    val animShine get() = s(R.string.anim_shine)
    val focusColor get() = s(R.string.focus_color)
    val colorAuto get() = s(R.string.color_auto)
    val iconBackground get() = s(R.string.icon_background)
    val bgNone get() = s(R.string.bg_none)
    val bgGraphite get() = s(R.string.bg_graphite)
    val continueWatching get() = s(R.string.continue_watching)
    val continueWatchingHint get() = s(R.string.continue_watching_hint)
    val apps get() = s(R.string.apps)
    val hiddenApps get() = s(R.string.hidden_apps)
    val homeGuard get() = s(R.string.home_guard)
    val homeGuardOn get() = s(R.string.home_guard_on)
    val homeGuardOff get() = s(R.string.home_guard_off)
    val parental get() = s(R.string.parental)
    val parentalOff get() = s(R.string.parental_off)
    fun unlockApp(app: String) = s(R.string.unlock_app, app)
    val unlockSettings get() = s(R.string.unlock_settings)
    val pinHint get() = s(R.string.pin_hint)
    val wrongPin get() = s(R.string.wrong_pin)
    val setPin get() = s(R.string.set_pin)
    val changePin get() = s(R.string.change_pin)
    val newPin get() = s(R.string.new_pin)
    val repeatPin get() = s(R.string.repeat_pin)
    val pinMismatch get() = s(R.string.pin_mismatch)
    val pinSaved get() = s(R.string.pin_saved)
    val forgotPin get() = s(R.string.forgot_pin)
    val lockedApps get() = s(R.string.locked_apps)
    val lockedAppsHint get() = s(R.string.locked_apps_hint)
    val lockSettings get() = s(R.string.lock_settings)
    val lockSettingsHint get() = s(R.string.lock_settings_hint)
    val lockHours get() = s(R.string.lock_hours)
    val lockAlways get() = s(R.string.lock_always)
    fun lockBetween(from: Int, to: Int) = s(R.string.lock_between, from, to)
    val lockFrom get() = s(R.string.lock_from)
    val lockTo get() = s(R.string.lock_to)
    val hourHint get() = s(R.string.hour_hint)
    val parentalDisable get() = s(R.string.parental_disable)
    val parentalDisableHint get() = s(R.string.parental_disable_hint)
    val settingsProtected get() = s(R.string.settings_protected)
    val settingsProtectedHint get() = s(R.string.settings_protected_hint)
    val unlock get() = s(R.string.unlock)
    val lockApp get() = s(R.string.lock_app)
    val unlockAppMenu get() = s(R.string.unlock_app_menu)
    val startup get() = s(R.string.startup)
    val autostart get() = s(R.string.autostart)
    val autostartNone get() = s(R.string.autostart_none)
    val autostartWake get() = s(R.string.autostart_wake)
    val autostartWakeHint get() = s(R.string.autostart_wake_hint)
    val autostartDelay get() = s(R.string.autostart_delay)
    val guardStuck get() = s(R.string.guard_stuck)
    val guardRepairing get() = s(R.string.guard_repairing)
    val guardStuckToast get() = s(R.string.guard_stuck_toast)
    val youtubeFeed get() = s(R.string.youtube_feed)
    val ytBoth get() = s(R.string.yt_both)
    val ytContinue get() = s(R.string.yt_continue)
    val ytRecommended get() = s(R.string.yt_recommended)
    val remote get() = s(R.string.remote)
    val buttons get() = s(R.string.buttons)
    val buttonsNone get() = s(R.string.buttons_none)
    fun buttonsCount(n: Int) = s(R.string.buttons_count, n)
    val addButton get() = s(R.string.add_button)
    val pressButton get() = s(R.string.press_button)
    val pressButtonHint get() = s(R.string.press_button_hint)
    val buttonsFull get() = s(R.string.buttons_full)
    fun buttonOpens(button: String) = s(R.string.button_opens, button)
    val removeButton get() = s(R.string.remove_button)
    val buttonNetflix get() = s(R.string.button_netflix)
    val accessibilityOpen get() = s(R.string.accessibility_open)
    val needsGuard get() = s(R.string.needs_guard)
    val idleMode get() = s(R.string.idle_mode)
    val idleOff get() = s(R.string.idle_off)
    fun idleAfter(min: Int) = s(R.string.idle_after, min)
    val idleKeepAwake get() = s(R.string.idle_keep_awake)
    val idleKeepAwakeHint get() = s(R.string.idle_keep_awake_hint)
    val minWidth get() = s(R.string.min_width)
    val anyResolution get() = s(R.string.any_resolution)
    val hiddenAppsHint get() = s(R.string.hidden_apps_hint)
    val account get() = s(R.string.account)
    val linkAccount get() = s(R.string.link_account)
    val linkAccountHint get() = s(R.string.link_account_hint)
    val syncNow get() = s(R.string.sync_now)
    val synced get() = s(R.string.synced)
    val unlink get() = s(R.string.unlink)
    val unlinkHint get() = s(R.string.unlink_hint)
    val pressAgainToConfirm get() = s(R.string.press_again_to_confirm)
    val unlinked get() = s(R.string.unlinked)
    val android get() = s(R.string.android)
    val androidSettings get() = s(R.string.android_settings)
    fun version(v: String) = s(R.string.version, v)
    val updates get() = s(R.string.updates)
    val checkUpdates get() = s(R.string.check_updates)
    fun upToDate(v: String) = s(R.string.up_to_date, v)
    val checking get() = s(R.string.checking)
    fun updateReady(v: String) = s(R.string.update_ready, v)
    fun updateAvailable(v: String) = s(R.string.update_available, v)
    fun downloading(p: Int) = s(R.string.downloading, p)
    val installing get() = s(R.string.installing)
    fun updateFailed(msg: String) = s(R.string.update_failed, msg)
    val allowInstall get() = s(R.string.allow_install)
    val updatesOnlyRelease get() = s(R.string.updates_only_release)
    val save get() = s(R.string.save)
    val cancel get() = s(R.string.cancel)
    val activateTitle get() = s(R.string.activate_title)
    val activateGoTo get() = s(R.string.activate_go_to)
    val activateEnterCode get() = s(R.string.activate_enter_code)
    val activateRequesting get() = s(R.string.activate_requesting)
    val activateQr get() = s(R.string.activate_qr)
    val activateQrDescription get() = s(R.string.activate_qr_description)
    val language get() = s(R.string.language)
    val dayNight get() = s(R.string.day_night)
    val nightOn get() = s(R.string.night_on)
    val nightOff get() = s(R.string.night_off)
    val nightAuto get() = s(R.string.night_auto)
    val nightAutoHint get() = s(R.string.night_auto_hint)
    fun nightAutoSun(sunset: String, sunrise: String) = s(R.string.night_auto_sun, sunset, sunrise)
    val languageAuto get() = s(R.string.language_auto)
    fun wallpaperName(id: String) = s(
        when (id) {
            "aurora" -> R.string.wp_aurora
            "sunset" -> R.string.wp_sunset
            "graphite" -> R.string.wp_graphite
            else -> R.string.wp_lake
        },
    )
    fun activateServerError(code: Int) = s(R.string.activate_server_error, code)
    val activateOffline get() = s(R.string.activate_offline)
    val activateBadResponse get() = s(R.string.activate_bad_response)
    val linked get() = s(R.string.linked)
    fun seasonEpisode(season: String?, episode: String?) = listOfNotNull(
        season?.let { s(R.string.season_short, it) },
        episode?.let { s(R.string.episode_short, it) },
    ).joinToString(" ")
    fun minutesLeft(min: Long) =
        if (min >= 60) s(R.string.hours_minutes_left, min / 60, min % 60) else s(R.string.minutes_left, min)
    fun parentalOn(n: Int) = p(R.plurals.parental_on, n, n)
    fun lockedCount(n: Int) = p(R.plurals.app_count, n, n)
    fun seconds(n: Int) = if (n == 0) s(R.string.seconds_now) else s(R.string.seconds_n, n)
    fun hiddenCount(n: Int) = if (n == 0) s(R.string.hidden_none) else p(R.plurals.app_count, n, n)
    fun linkedAs(email: String?) = if (email != null) s(R.string.linked_as, maskEmail(email)) else s(R.string.linked_account)
    fun activateExpires(min: Long, sec: Long) = s(R.string.activate_expires, min, sec)

    companion object {
        /** As línguas da app, iguais às do site, pela ordem do seletor (o nome de cada uma na própria língua). */
        val LANGUAGES = listOf(
            "en" to "English", "es" to "Español", "pt-PT" to "Português (PT)", "pt-BR" to "Português (BR)",
            "fr" to "Français", "de" to "Deutsch", "it" to "Italiano", "ru" to "Русский", "tr" to "Türkçe",
            "id" to "Indonesia", "hi" to "हिन्दी", "ar" to "العربية", "zh-CN" to "简体中文", "ja" to "日本語", "ko" to "한국어",
        )

        /** "auto" ou um código de [LANGUAGES]; o antigo "pt" passa a pt-PT e o resto desconhecido a "auto". */
        fun normalize(code: String?): String = when {
            code == "pt" -> "pt-PT"
            code != null && LANGUAGES.any { it.first == code } -> code
            else -> "auto"
        }

        /** Aplica a língua ("auto" segue o sistema). O AppCompat guarda-a e recria as Activities. */
        fun apply(code: String) {
            val lang = normalize(code)
            val target = if (lang == "auto") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(lang)
            if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != target.toLanguageTags()) {
                AppCompatDelegate.setApplicationLocales(target)
            }
        }

        @Volatile
        private var cache: Pair<String, Strings>? = null

        /**
         * Os textos na língua da app, para código fora das Activities: antes do Android 13 o contexto da app
         * não muda de língua sozinho, por isso cria-se um com a língua escolhida.
         */
        fun of(context: Context): Strings {
            val app = context.applicationContext
            val chosen = AppCompatDelegate.getApplicationLocales()
            val key = chosen.toLanguageTags() + "|" + Locale.getDefault().toLanguageTag()
            cache?.let { if (it.first == key) return it.second }
            val ctx = if (chosen.isEmpty) {
                app
            } else {
                app.createConfigurationContext(
                    Configuration(app.resources.configuration).also { ConfigurationCompat.setLocales(it, chosen) },
                )
            }
            return Strings(ctx).also { cache = key to it }
        }
    }
}

val LocalStrings = staticCompositionLocalOf<Strings> { error("LocalStrings sem valor (ver LauncherRoot)") }

/**
 * Email mascarado para o ecrã ("n••••••@example.com"): não aparece por inteiro em gravações ou fotos da TV.
 * O Android 11 da SHIELD não tem desfocagem real, por isso esconde-se o nome.
 */
fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    if (at <= 0) return email
    return email.first() + "••••••" + email.substring(at)
}
