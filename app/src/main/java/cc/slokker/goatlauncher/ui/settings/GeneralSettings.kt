package cc.slokker.goatlauncher.ui.settings

import cc.slokker.goatlauncher.ui.Strings
import androidx.compose.ui.input.key.key
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import cc.slokker.goatlauncher.data.Place
import cc.slokker.goatlauncher.ui.LauncherViewModel
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.common.GlassButton
import cc.slokker.goatlauncher.ui.common.GlassRow
import cc.slokker.goatlauncher.ui.common.GoatIcons
import cc.slokker.goatlauncher.ui.common.Toggle
import cc.slokker.goatlauncher.ui.common.TvTextField
import cc.slokker.goatlauncher.ui.home.clockText
import java.util.Calendar
import cc.slokker.goatlauncher.ui.theme.GoatColors
/** Geral: a língua, onde fica a TV (o tempo e o dia/noite), a noite automática e os sons. */
@Composable
internal fun GeneralPage(vm: LauncherViewModel, first: FocusRequester, onPage: (Page) -> Unit) {
    val sun by vm.sunTimes.collectAsStateWithLifecycle()
    val s = LocalStrings.current
    val account by vm.account.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item {
            val code = Strings.normalize(account.language)
            GlassRow(
                title = s.language,
                subtitle = Strings.LANGUAGES.firstOrNull { it.first == code }?.second ?: s.languageAuto,
                onClick = { onPage(Page.Language) },
                trailing = { Chevron() },
                modifier = Modifier.focusRequester(first),
            )
        }
        item {
            val device by vm.device.collectAsStateWithLifecycle()
            GlassRow(
                title = s.location,
                subtitle = device.location?.name ?: vm.ipCity()?.let(s::locationAutoCity) ?: s.locationAuto,
                onClick = { onPage(Page.Location) },
                trailing = { Chevron() },
            )
        }
        item {
            GlassRow(
                title = s.nightAuto,
                // Com a localização, do pôr ao nascer do sol de hoje; sem ela, a hora fixa.
                subtitle = sun?.let { (rise, set) -> s.nightAutoSun(sunClock(set, s, account.clock24h), sunClock(rise, s, account.clock24h)) }
                    ?: s.nightAutoHint,
                onClick = { vm.setNightAuto(!account.nightAuto) },
                trailing = { Toggle(account.nightAuto) },
            )
        }
        item {
            GlassRow(
                title = s.navSounds,
                subtitle = s.navSoundsHint,
                onClick = { vm.setNavSounds(!account.navSounds) },
                trailing = { Toggle(account.navSounds) },
            )
        }
    }
}

/** [minutes] desde a meia-noite (de hoje) no formato do relógio de cima: "07:42", ou "7:42 PM", "午前7:42" sem 24 horas. */
private fun sunClock(minutes: Int, s: Strings, clock24h: Boolean): String {
    val time = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minutes / 60)
        set(Calendar.MINUTE, minutes % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return clockText(time, s, clock24h)
}

/** "Automático" e as 15 línguas, cada uma com o nome na própria língua. Ao escolher, o ecrã muda logo. */
/**
 * Onde está a TV (para o tempo e o dia/noite): automática pelo IP, ou uma cidade procurada pelo nome.
 * O IP engana-se com VPN ou com alguns operadores (dá a cidade do servidor).
 */
@Composable
internal fun LocationPage(vm: LauncherViewModel, first: FocusRequester) {
    val s = LocalStrings.current
    val device by vm.device.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    var searching by remember { mutableStateOf(false) }
    fun search() {
        if (query.isBlank()) return
        searching = true
        scope.launch {
            results = vm.searchPlaces(query)
            searching = false
        }
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item {
            Text(s.locationHint, style = MaterialTheme.typography.bodySmall, color = GoatColors.TextFaint, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
        }
        item {
            GlassRow(
                title = vm.ipCity()?.let(s::locationAutoCity) ?: s.locationAuto,
                onClick = { vm.setLocation(null) },
                trailing = { if (device.location == null) Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.focusRequester(first),
            )
        }
        device.location?.let { place ->
            item {
                GlassRow(title = place.name, onClick = {}, trailing = { Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) })
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvTextField(query, { query = it }, s.locationSearchHint, Modifier.weight(1f), onDone = ::search)
                GlassButton(s.search, onClick = ::search)
            }
        }
        when {
            searching -> item { Text(s.loading, color = GoatColors.TextFaint, modifier = Modifier.padding(12.dp)) }
            results?.isEmpty() == true -> item { Text(s.locationNone, color = GoatColors.TextFaint, modifier = Modifier.padding(12.dp)) }
            else -> items(results.orEmpty(), key = { "${it.lat},${it.lon}" }) { place ->
                GlassRow(title = place.name, onClick = { vm.setLocation(place) })
            }
        }
    }
}

@Composable
internal fun LanguagePage(vm: LauncherViewModel, first: FocusRequester) {
    val s = LocalStrings.current
    val account by vm.account.collectAsStateWithLifecycle()
    val current = Strings.normalize(account.language)
    val options = listOf("auto" to s.languageAuto) + Strings.LANGUAGES
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        items(options, key = { it.first }) { (code, name) ->
            GlassRow(
                title = name,
                onClick = { vm.setLanguage(code) },
                trailing = { if (code == current) Icon(GoatIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = if (code == current) Modifier.focusRequester(first) else Modifier,
            )
        }
    }
}
