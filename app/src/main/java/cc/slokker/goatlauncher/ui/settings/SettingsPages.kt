package cc.slokker.goatlauncher.ui.settings

import cc.slokker.goatlauncher.ui.Strings

/** O que algumas páginas mostram além das definições: o botão do comando que se está a configurar. */
internal data class PageArgs(val buttonKey: Pair<Int, Int>? = null)

/**
 * As páginas das Definições: de onde se chega a cada uma (para onde Voltar leva) e o título.
 * Uma página nova é uma entrada aqui, as suas linhas em SettingsCatalog (ou uma página à parte) e o seu ramo no
 * `when` de SettingsScreen, que não compila sem ele.
 */
internal enum class Page(private val parentOf: (hasPin: Boolean) -> Page?, private val titleOf: (Strings, PageArgs) -> String) {
    Main({ null }, { s, _ -> s.settings }),
    Appearance({ Page.Main }, { s, _ -> s.appearance }),
    Wallpaper({ Page.Main }, { s, _ -> s.wallpaper }),
    WallpaperFiles({ Page.Wallpaper }, { s, _ -> s.boxImages }),
    Apps({ Page.Main }, { s, _ -> s.apps }),
    HiddenApps({ Page.Apps }, { s, _ -> s.hiddenApps }),
    Ambient({ Page.Main }, { s, _ -> s.idleMode }),
    Startup({ Page.Main }, { s, _ -> s.startup }),
    Autostart({ Page.Startup }, { s, _ -> s.autostart }),
    Buttons({ Page.Main }, { s, _ -> s.buttons }),
    ButtonCapture({ Page.Buttons }, { s, _ -> s.addButton }),
    ButtonApp({ Page.Buttons }, { s, args -> args.buttonKey?.let { (k, sc) -> s.buttonOpens(buttonName(k, sc, s)) } ?: s.buttons }),
    Parental({ Page.Main }, { s, _ -> s.parental }),

    /** O primeiro código liga o controlo parental a partir da página principal; mudá-lo, a partir do Controlo parental. */
    PinSetup({ hasPin -> if (hasPin) Page.Parental else Page.Main }, { s, _ -> s.parental }),
    LockedApps({ Page.Parental }, { s, _ -> s.lockedApps }),
    General({ Page.Main }, { s, _ -> s.general }),
    Language({ Page.General }, { s, _ -> s.language }),
    Location({ Page.General }, { s, _ -> s.location }),
    Account({ Page.Main }, { s, _ -> s.account }),
    YouTube({ Page.Account }, { s, _ -> s.youtube }),
    Report({ Page.Main }, { s, _ -> s.sendReport }),
    ;

    /** A página de onde se chega a esta (para onde Voltar leva); null na página principal. */
    fun parent(hasPin: Boolean): Page? = parentOf(hasPin)

    fun title(s: Strings, args: PageArgs = PageArgs()): String = titleOf(s, args)

    /** Quantos níveis abaixo da página principal (para a transição saber se vai ou volta). */
    val depth: Int get() = generateSequence(this) { it.parent(hasPin = true) }.count() - 1
}
