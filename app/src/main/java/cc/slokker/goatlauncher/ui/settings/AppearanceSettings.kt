package cc.slokker.goatlauncher.ui.settings

import cc.slokker.goatlauncher.ui.Strings
import androidx.compose.ui.input.key.key
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.after
import cc.slokker.goatlauncher.ui.LauncherViewModel
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.common.GlassRow
import cc.slokker.goatlauncher.ui.common.GoatIcons
import cc.slokker.goatlauncher.ui.common.SectionLabel
import cc.slokker.goatlauncher.ui.common.Toggle
/** Aparência: a disposição do ecrã inicial (Android TV ou Apple TV), o estilo do "Continuar a ver" e o resto. */
@Composable
internal fun AppearancePage(vm: LauncherViewModel, first: FocusRequester, onPage: (Page) -> Unit) {
    val s = LocalStrings.current
    val account by vm.account.collectAsStateWithLifecycle()
    val android = account.layout == AccountSettings.LAYOUT_ANDROID

    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item { SectionLabel(s.layout) }
        item {
            GlassRow(
                title = LAYOUT_ANDROID_NAME,
                subtitle = s.layoutAndroidHint,
                onClick = { vm.setLayout(AccountSettings.LAYOUT_ANDROID) },
                trailing = { if (android) Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.focusRequester(first),
            )
        }
        item {
            GlassRow(
                title = LAYOUT_APPLE_NAME,
                subtitle = s.layoutAppleHint,
                onClick = { vm.setLayout(AccountSettings.LAYOUT_APPLE_TV) },
                trailing = { if (!android) Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) },
            )
        }
        // O "Continuar a ver" logo a seguir à disposição: na Android escolhe-se o estilo (ou desliga-se); a Apple TV
        // tem sempre os cartões grandes, por isso só se liga ou desliga.
        if (android) {
            item { SectionLabel(s.continueStyle) }
            items(AccountSettings.STYLES.map { it to styleName(it, s) }, key = { it.first }) { (style, name) ->
                GlassRow(
                    title = name,
                    onClick = { vm.setContinueStyle(style) },
                    trailing = {
                        if (account.showContinueWatching && account.continueStyle == style) {
                            Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    },
                )
            }
            item(key = "continue_off") {
                GlassRow(
                    title = s.idleOff,
                    onClick = { vm.setShowContinueWatching(false) },
                    trailing = {
                        if (!account.showContinueWatching) Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                )
            }
        } else {
            item {
                GlassRow(
                    title = s.continueWatching,
                    subtitle = s.continueWatchingHint,
                    onClick = { vm.setShowContinueWatching(!account.showContinueWatching) },
                    trailing = { Toggle(account.showContinueWatching) },
                )
            }
        }

        item { SectionLabel(s.focusedCard) }
        item {
            // OK passa ao efeito seguinte (pela ordem de FOCUS_EFFECTS, e volta ao primeiro); a pré-visualização ao lado mostra-o.
            val effects = AccountSettings.FOCUS_EFFECTS
            val next = effects.after(account.focusEffect)
            val label = when (account.focusEffect) {
                AccountSettings.FOCUS_GLOW -> s.effectGlow
                AccountSettings.FOCUS_GLASS -> s.effectGlass
                AccountSettings.FOCUS_REFLECTION -> s.effectReflection
                AccountSettings.FOCUS_AURORA -> s.effectAurora
                AccountSettings.FOCUS_VIEWFINDER -> s.effectViewfinder
                AccountSettings.FOCUS_UNDERLINE -> s.effectUnderline
                AccountSettings.FOCUS_RING -> s.effectRing
                AccountSettings.FOCUS_DIM -> s.effectDim
                AccountSettings.FOCUS_SHADOW -> s.effectShadow
                else -> s.effectBorder
            }
            GlassRow(title = s.focusEffect, subtitle = label, onClick = { vm.setFocusEffect(next) }, trailing = { Chevron() })
        }
        // Cada efeito tem as suas animações (o vidro tem o brilho a passar e mais nenhuma); a cor não serve
        // para "só sombra" nem para "escurecer os outros".
        val effect = account.focusEffect
        val animations = AccountSettings.focusAnimations(effect)
        if (animations.isNotEmpty()) {
            item {
                // OK passa à animação seguinte das que este efeito aceita (e volta à primeira).
                val current = account.focusAnimation.takeIf { it in animations } ?: AccountSettings.ANIM_NONE
                val next = animations.after(current)
                val label = when (current) {
                    AccountSettings.ANIM_PULSE -> s.animPulse
                    AccountSettings.ANIM_BREATH -> s.animBreath
                    AccountSettings.ANIM_SHINE -> if (effect == AccountSettings.FOCUS_AURORA) s.animSpin else s.animShine
                    else -> s.animNone
                }
                GlassRow(title = s.focusAnimation, subtitle = label, onClick = { vm.setFocusAnimation(next) }, trailing = { Chevron() })
            }
        }
        if (effect != AccountSettings.FOCUS_SHADOW && effect != AccountSettings.FOCUS_DIM) {
            item { FocusColorRow(account.focusColor, onPick = vm::setFocusColor) }
        }

        item { SectionLabel(s.homeScreen) }
        item {
            GlassRow(title = s.clock24h, onClick = { vm.setClock24h(!account.clock24h) }, trailing = { Toggle(account.clock24h) })
        }
        item {
            GlassRow(title = s.greeting, onClick = { vm.setShowGreeting(!account.showGreeting) }, trailing = { Toggle(account.showGreeting) })
        }
        item {
            GlassRow(title = s.weather, onClick = { vm.setShowWeather(!account.showWeather) }, trailing = { Toggle(account.showWeather) })
        }
        item {
            GlassRow(title = s.showAppName, onClick = { vm.setShowAppName(!account.showAppName) }, trailing = { Toggle(account.showAppName) })
        }
        item { IconBackgroundRow(account.iconBackground, onPick = vm::setIconBackground) }
    }
}

private const val LAYOUT_ANDROID_NAME = "Android TV"
private const val LAYOUT_APPLE_NAME = "Apple TV"

/** "Apple TV" ou "Android TV · Destaque". */
internal fun layoutName(account: AccountSettings, s: Strings): String =
    if (account.layout == AccountSettings.LAYOUT_APPLE_TV) LAYOUT_APPLE_NAME else "$LAYOUT_ANDROID_NAME · ${styleName(account.continueStyle, s)}"

/** O nome de um estilo do "Continuar a ver" (disposição Android). */
private fun styleName(style: String, s: Strings): String = when (style) {
    AccountSettings.STYLE_SHELF -> s.styleShelf
    AccountSettings.STYLE_SPOTLIGHT -> s.styleSpotlightNext
    AccountSettings.STYLE_COLUMN -> s.styleColumn
    AccountSettings.STYLE_CAROUSEL -> s.styleCarousel
    else -> s.styleRow
}
