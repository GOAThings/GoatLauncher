package cc.slokker.goatlauncher.ui.settings

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
import cc.slokker.goatlauncher.ui.home.FocusEffectKind
import cc.slokker.goatlauncher.ui.home.HomeStyle
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
                title = HomeStyle.ANDROID_TV_NAME,
                subtitle = s.layoutAndroidHint,
                onClick = { vm.setLayout(AccountSettings.LAYOUT_ANDROID) },
                trailing = { if (android) Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.focusRequester(first),
            )
        }
        item {
            GlassRow(
                title = HomeStyle.APPLE_TV_NAME,
                subtitle = s.layoutAppleHint,
                onClick = { vm.setLayout(AccountSettings.LAYOUT_APPLE_TV) },
                trailing = { if (!android) Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) },
            )
        }
        // O "Continuar a ver" logo a seguir à disposição: na Android escolhe-se o estilo (ou desliga-se); a Apple TV
        // tem sempre os cartões grandes, por isso só se liga ou desliga.
        if (android) {
            item { SectionLabel(s.continueStyle) }
            items(HomeStyle.ANDROID, key = { it.continueStyle!! }) { style ->
                GlassRow(
                    title = style.styleLabel(s),
                    onClick = { vm.setContinueStyle(style.continueStyle!!) },
                    trailing = {
                        if (account.showContinueWatching && account.continueStyle == style.continueStyle) {
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

        val effect = FocusEffectKind.of(account.focusEffect)
        item { SectionLabel(s.focusedCard) }
        item {
            // OK passa ao efeito seguinte (pela ordem de FOCUS_EFFECTS, e volta ao primeiro); a pré-visualização ao lado mostra-o.
            val next = AccountSettings.FOCUS_EFFECTS.after(account.focusEffect)
            GlassRow(title = s.focusEffect, subtitle = effect.label(s), onClick = { vm.setFocusEffect(next) }, trailing = { Chevron() })
        }
        // Cada efeito tem as suas animações (o vidro tem o brilho a passar e mais nenhuma); a cor não serve
        // para "só sombra" nem para "escurecer os outros".
        val animations = effect.animations
        if (animations.isNotEmpty()) {
            item {
                // OK passa à animação seguinte das que este efeito aceita (e volta à primeira).
                val current = effect.animationOf(account.focusAnimation)
                val next = animations.after(current)
                GlassRow(title = s.focusAnimation, subtitle = effect.animationLabel(current, s), onClick = { vm.setFocusAnimation(next) }, trailing = { Chevron() })
            }
        }
        if (effect.takesColor) {
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
