package cc.slokker.goatlauncher.ui.settings

import androidx.compose.runtime.rememberUpdatedState
import cc.slokker.goatlauncher.data.ButtonMap
import cc.slokker.goatlauncher.ButtonCapture
import cc.slokker.goatlauncher.ui.Strings
import androidx.compose.ui.input.key.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.text.style.TextAlign
import cc.slokker.goatlauncher.ui.common.PinPad
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.data.DeviceSettings
import cc.slokker.goatlauncher.data.after
import cc.slokker.goatlauncher.HomeGuardService
import cc.slokker.goatlauncher.data.SystemIntents
import cc.slokker.goatlauncher.ui.LauncherViewModel
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.common.GlassButton
import cc.slokker.goatlauncher.ui.common.GlassRow
import cc.slokker.goatlauncher.ui.common.GoatIcons
import cc.slokker.goatlauncher.ui.common.Toggle
import cc.slokker.goatlauncher.ui.theme.GoatColors
/** Ao ligar: a app que abre quando a TV liga (e acorda) e quanto tempo depois. */
@Composable
internal fun StartupPage(vm: LauncherViewModel, first: FocusRequester, onPage: (Page) -> Unit) {
    val s = LocalStrings.current
    val device by vm.device.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item {
            val apps by vm.allApps.collectAsStateWithLifecycle()
            GlassRow(
                modifier = Modifier.focusRequester(first),
                title = s.autostart,
                subtitle = device.autostart?.let { pkg -> apps?.firstOrNull { it.packageName == pkg }?.label ?: pkg } ?: s.autostartNone,
                onClick = { onPage(Page.Autostart) },
                trailing = { Chevron() },
            )
        }
        if (device.autostart != null) {
            item {
                GlassRow(
                    title = s.autostartWake,
                    subtitle = s.autostartWakeHint,
                    onClick = { vm.setAutostartWake(!device.autostartWake) },
                    trailing = { Toggle(device.autostartWake) },
                )
            }
            item {
                val next = DeviceSettings.AUTOSTART_DELAYS.after(device.autostartDelay)
                GlassRow(
                    title = s.autostartDelay,
                    subtitle = s.seconds(device.autostartDelay),
                    onClick = { vm.setAutostartDelay(next) },
                    trailing = { Chevron() },
                )
            }
        }

    }
}

/** Com as definições protegidas pelo controlo parental: só o botão que pede o código. */
@Composable
internal fun ProtectedPage(vm: LauncherViewModel, first: FocusRequester) {
    val s = LocalStrings.current
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(s.settingsProtected, style = MaterialTheme.typography.titleMedium)
        Text(s.settingsProtectedHint, style = MaterialTheme.typography.bodyMedium, color = GoatColors.TextDim)
        Spacer(Modifier.height(18.dp))
        GlassButton(s.unlock, onClick = { vm.withSettingsUnlocked { } }, modifier = Modifier.focusRequester(first))
    }
}

/** Escolher uma app (ou [none], que dá null): para abrir ao ligar e para o botão Netflix. */
@Composable
internal fun AppPickerPage(vm: LauncherViewModel, first: FocusRequester, current: String?, none: String, onPick: (String?) -> Unit) {
    val apps by vm.allApps.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item {
            GlassRow(
                title = none,
                onClick = { onPick(null) },
                trailing = { if (current == null) Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.focusRequester(first),
            )
        }
        items(apps.orEmpty(), key = { it.packageName }) { app ->
            GlassRow(
                title = app.label,
                onClick = { onPick(app.packageName) },
                leading = { AppThumb(app) },
                trailing = { if (current == app.packageName) Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) },
            )
        }
    }
}

/** Controlo parental ligado: código, apps bloqueadas, proteção das definições e horário. */
@Composable
internal fun ParentalPage(vm: LauncherViewModel, first: FocusRequester, onPage: (Page) -> Unit) {
    val s = LocalStrings.current
    val device by vm.device.collectAsStateWithLifecycle()
    var confirmOff by remember { mutableStateOf(false) }
    val from = device.lockFrom
    val to = device.lockTo
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item {
            GlassRow(
                title = s.lockedApps,
                subtitle = s.lockedCount(device.locked.size),
                onClick = { onPage(Page.LockedApps) },
                trailing = { Chevron() },
                modifier = Modifier.focusRequester(first),
            )
        }
        item {
            GlassRow(
                title = s.lockSettings,
                subtitle = s.lockSettingsHint,
                onClick = { vm.setLockSettings(!device.lockSettings) },
                trailing = { Toggle(device.lockSettings) },
            )
        }
        item {
            GlassRow(
                title = s.lockHours,
                subtitle = if (from != null && to != null) s.lockBetween(from, to) else s.lockAlways,
                onClick = { if (from != null) vm.setLockHours(null, null) else vm.setLockHours(20, 8) },
                trailing = { Toggle(from != null) },
            )
        }
        if (from != null && to != null) {
            item { HourRow(s.lockFrom, from) { vm.setLockHours(it, to) } }
            item { HourRow(s.lockTo, to) { vm.setLockHours(from, it) } }
        }
        item { GlassRow(title = s.changePin, onClick = { onPage(Page.PinSetup) }, trailing = { Chevron() }) }
        item {
            GlassRow(
                title = s.parentalDisable,
                subtitle = if (confirmOff) s.pressAgainToConfirm else s.parentalDisableHint,
                onClick = {
                    if (confirmOff) {
                        vm.setPin(null)
                        onPage(Page.Main)
                    }
                    confirmOff = !confirmOff
                },
            )
        }
    }
}

/** Novo código: escreve-se duas vezes. O primeiro liga o controlo parental. */
@Composable
internal fun PinSetupPage(vm: LauncherViewModel, onDone: () -> Unit) {
    val s = LocalStrings.current
    var firstPin by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        key(attempt) {
            PinPad(
                title = if (firstPin == null) s.newPin else s.repeatPin,
                message = error,
                onDone = { pin ->
                    val pending = firstPin
                    when {
                        pending == null -> {
                            firstPin = pin
                            error = null
                        }
                        pending == pin -> {
                            vm.setPin(pin)
                            vm.toast(s.pinSaved)
                            onDone()
                        }
                        else -> {
                            firstPin = null
                            error = s.pinMismatch
                        }
                    }
                    attempt++
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(s.forgotPin, style = MaterialTheme.typography.bodySmall, color = GoatColors.TextFaint, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun LockedAppsPage(vm: LauncherViewModel, first: FocusRequester) {
    val s = LocalStrings.current
    val apps by vm.allApps.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val list = apps.orEmpty()
    Column {
        Text(s.lockedAppsHint, style = MaterialTheme.typography.bodySmall, color = GoatColors.TextFaint, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
            items(list, key = { it.packageName }) { app ->
                val locked = app.packageName in device.locked
                GlassRow(
                    title = app.label,
                    onClick = { vm.setLocked(app.packageName, !locked) },
                    leading = { AppThumb(app) },
                    trailing = { Toggle(locked) },
                    modifier = if (app == list.first()) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}

/** "Botão Netflix" para o da Shield; os outros pelo nome do Android ("Button 12", "Settings", "Guide"…). */
internal fun buttonName(keyCode: Int, scanCode: Int, s: Strings): String =
    if (keyCode == ButtonMap.SHIELD_NETFLIX_KEY && scanCode == ButtonMap.SHIELD_NETFLIX_SCAN) {
        s.buttonNetflix
    } else {
        android.view.KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").replace('_', ' ')
            .lowercase().replaceFirstChar { it.uppercase() }
    }

/** Os botões do comando com app (até 5) e "Adicionar botão". */
@Composable
internal fun ButtonsPage(vm: LauncherViewModel, first: FocusRequester, onEdit: (Pair<Int, Int>) -> Unit, onAdd: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val device by vm.device.collectAsStateWithLifecycle()
    val apps by vm.allApps.collectAsStateWithLifecycle()
    var guard by remember { mutableStateOf(HomeGuardService.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        guard = HomeGuardService.isEnabled(context)
        onPauseOrDispose { }
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        items(device.buttons, key = { "${it.keyCode}:${it.scanCode}" }) { b ->
            GlassRow(
                title = buttonName(b.keyCode, b.scanCode, s),
                subtitle = apps?.firstOrNull { it.packageName == b.app }?.label ?: b.app,
                onClick = { onEdit(b.keyCode to b.scanCode) },
                trailing = { Chevron() },
                modifier = if (b == device.buttons.first()) Modifier.focusRequester(first) else Modifier,
            )
        }
        item {
            val full = device.buttons.size >= ButtonMap.MAX
            GlassRow(
                title = if (guard) s.addButton else s.accessibilityOpen,
                subtitle = when {
                    !guard -> s.needsGuard
                    full -> s.buttonsFull
                    else -> null
                },
                onClick = {
                    when {
                        // Só o serviço de acessibilidade vê os botões (e os abre depois): sem ele, vai-se ligá-lo.
                        !guard -> SystemIntents.start(context, SystemIntents.accessibilitySettings())
                        !full -> onAdd()
                    }
                },
                modifier = if (device.buttons.isEmpty()) Modifier.focusRequester(first) else Modifier,
            )
        }
    }
}

/** "Carrega no botão": o HomeGuardService apanha o próximo botão que se possa usar. */
@Composable
internal fun ButtonCapturePage(first: FocusRequester, onCaptured: (Pair<Int, Int>) -> Unit, onCancel: () -> Unit) {
    val s = LocalStrings.current
    val captured by rememberUpdatedState(onCaptured)
    DisposableEffect(Unit) {
        ButtonCapture.listener = { key, scan -> captured(key to scan) }
        onDispose { ButtonCapture.listener = null }
    }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(s.pressButton, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(s.pressButtonHint, style = MaterialTheme.typography.bodyMedium, color = GoatColors.TextDim)
        Spacer(Modifier.height(18.dp))
        GlassButton(s.cancel, onClick = onCancel, modifier = Modifier.focusRequester(first))
    }
}
