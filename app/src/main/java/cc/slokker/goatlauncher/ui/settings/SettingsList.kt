package cc.slokker.goatlauncher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.slokker.goatlauncher.ui.LauncherViewModel
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.common.SectionLabel

/**
 * Uma página de definições feita do catálogo (SettingsCatalog): as linhas visíveis de [page], pela ordem, numa lista.
 * A primeira que pode receber o foco leva o [first]. [spacing]: espaço entre as linhas (a página principal tem cartões).
 */
@Composable
internal fun SettingsList(
    vm: LauncherViewModel,
    page: Page,
    first: FocusRequester,
    onPage: (Page) -> Unit,
    spacing: Dp? = null,
    /** Ver [SettingsScope.onFocusPage]. */
    onFocusPage: ((Page) -> Unit)? = null,
    /** Ver [SettingsScope.navRequester]. */
    navRequester: ((Page) -> FocusRequester)? = null,
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val account by vm.account.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val values = SettingsValues(account, device)
    val scope = SettingsScope(vm, values, s, context, onPage, onFocusPage, navRequester)
    val shown = SettingsCatalog.on(page).filter { it.visible(values) }
    val firstKey = shown.firstOrNull { it.focusable }?.key
    LazyColumn(
        state = rememberPaneListState(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = if (spacing != null) Arrangement.spacedBy(spacing) else Arrangement.Top,
    ) {
        shown.forEach { setting -> row(setting, scope, if (setting.key == firstKey) Modifier.focusRequester(first) else Modifier) }
    }
}

/**
 * Sobe a cada pedido para as listas do painel voltarem ao início. Numa lista que desceu, a primeira linha sai do ecrã e
 * deixa de estar composta: o → das categorias (que entra sempre nela) não a encontrava e caía na linha visível mais
 * perto (a segunda, ao voltar às Definições).
 */
internal val LocalPaneTop = compositionLocalOf { 0 }

/** O estado de uma lista do painel: volta ao início quando [LocalPaneTop] sobe (fora do painel nunca sobe). */
@Composable
internal fun rememberPaneListState(): LazyListState {
    val state = rememberLazyListState()
    val top = LocalPaneTop.current
    LaunchedEffect(top) { if (top > 0) state.scrollToItem(0) }
    return state
}

private fun LazyListScope.row(setting: Setting, scope: SettingsScope, modifier: Modifier) {
    item(key = setting.key) { SettingRow(setting, scope, modifier) }
}

/** Uma linha do catálogo com o aspeto de sempre (ver o kit em SettingsRows). */
@Composable
private fun SettingRow(setting: Setting, scope: SettingsScope, modifier: Modifier) {
    val s = scope.s
    when (setting) {
        is Setting.Section -> SectionLabel(setting.title(s))
        is Setting.Toggle -> ToggleRow(
            title = setting.title(s),
            checked = setting.read(scope.values),
            onToggle = { setting.write.send(scope.vm, it) },
            subtitle = setting.subtitle?.invoke(scope),
            modifier = modifier,
        )
        is Setting.Cycle<*> -> CycleSettingRow(setting, scope, modifier)
        is Setting.Choice -> ChoiceRow(
            title = setting.title(s),
            selected = setting.selected(scope.values),
            onClick = { setting.write.send(scope.vm, Unit) },
            subtitle = setting.subtitle?.invoke(s),
            leading = setting.leading,
            modifier = modifier,
        )
        is Setting.Nav -> {
            val icon = setting.icon
            val leading: (@Composable RowScope.() -> Unit)? = if (icon != null) {
                { IconTile(icon(), accent = setting.accent) }
            } else {
                null
            }
            val onClick = {
                setting.action?.invoke(scope)
                setting.to?.let(scope.onPage)
                Unit
            }
            val to = setting.to
            val follow = scope.onFocusPage
            val requester = if (to != null) scope.navRequester?.invoke(to) else null
            NavRow(
                title = setting.title(s),
                onClick = onClick,
                subtitle = setting.subtitle?.invoke(scope),
                leading = leading,
                modifier = modifier
                    .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                    .then(if (to != null && follow != null) Modifier.onFocusChanged { if (it.isFocused) follow(to) } else Modifier),
            )
        }
        is Setting.Custom -> setting.content(scope, modifier)
        is Setting.Group -> Group {
            val firstKey = setting.rows.firstOrNull { it.focusable }?.key
            setting.rows.filter { it.visible(scope.values) }.forEach { child ->
                SettingRow(child, scope, if (child.key == firstKey) modifier else Modifier)
            }
        }
    }
}

@Composable
private fun <T> CycleSettingRow(setting: Setting.Cycle<T>, scope: SettingsScope, modifier: Modifier) {
    CycleRow(
        title = setting.title(scope.s),
        options = setting.options(scope.values),
        current = setting.read(scope.values),
        label = { setting.label(scope.values, it, scope.s) },
        onPick = { setting.write.send(scope.vm, it) },
        modifier = modifier,
    )
}
