package cc.slokker.goatlauncher.ui.settings

import androidx.compose.ui.text.style.TextDirection
import cc.slokker.goatlauncher.ui.Strings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.ui.LauncherViewModel
import cc.slokker.goatlauncher.ui.state.ReportState
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.common.GlassButton
import cc.slokker.goatlauncher.ui.common.GlassRow
import cc.slokker.goatlauncher.ui.common.SectionLabel
import cc.slokker.goatlauncher.ui.common.TvTextField
import cc.slokker.goatlauncher.ui.theme.GoatColors

/** O estado do último relatório, ou o que o botão faz. */
internal fun reportStatus(state: ReportState, s: Strings): String = when (state) {
    ReportState.Idle -> s.sendReportSub
    ReportState.Sending -> s.reportSending
    is ReportState.Sent -> s.reportSent(state.id)
    is ReportState.Failed -> state.message
}

/** Enviar um relatório: o que aconteceu (opcional) e o registo do launcher, para a zona admin do site. */
@Composable
internal fun ReportPage(vm: LauncherViewModel, first: FocusRequester) {
    val s = LocalStrings.current
    val report by vm.report.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf("") }
    fun send() {
        if (report != ReportState.Sending) vm.sendReport(message)
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item {
            Text(s.sendReportSub, style = MaterialTheme.typography.bodySmall, color = GoatColors.TextFaint, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
        }
        item {
            TvTextField(message, { message = it.take(2000) }, s.reportWhat, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).focusRequester(first), onDone = ::send)
        }
        item {
            GlassRow(title = s.reportSend, subtitle = reportStatus(report, s).takeIf { report != ReportState.Idle }, onClick = ::send)
        }
    }
}

/** A conta GOAT e o YouTube. */
@Composable
internal fun AccountPage(vm: LauncherViewModel, first: FocusRequester, onPage: (Page) -> Unit) {
    val s = LocalStrings.current
    val account by vm.account.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        item { SectionLabel(s.account) }
        val current = session
        if (current == null) {
            item {
                NavRow(s.linkAccount, onClick = vm::openActivation, subtitle = s.linkAccountHint, modifier = Modifier.focusRequester(first))
            }
        } else {
            item {
                GlassRow(title = s.linkedAs(current.email), subtitle = current.deviceName, onClick = vm::syncNow, modifier = Modifier.focusRequester(first))
            }
            item { GlassRow(title = s.syncNow, onClick = vm::syncNow) }
            item { ConfirmRow(s.unlink, s.unlinkHint, onConfirm = vm::unlink) }
        }

        item { SectionLabel(s.youtube) }
        item {
            val linked by vm.youtubeLinked.collectAsStateWithLifecycle()
            if (linked) {
                // OK passa à seguinte: os dois → só a meio → só recomendações → os dois.
                CycleRow(s.youtubeFeed, AccountSettings.YOUTUBE_FEEDS, account.youtubeFeed, label = { youtubeFeedName(it, s) }, onPick = vm::setYoutubeFeed)
                ConfirmRow(s.youtubeUnlink, s.youtubeOnHint, onConfirm = vm::unlinkYouTube)
            } else {
                NavRow(s.youtubeLink, onClick = { onPage(Page.YouTube) }, subtitle = s.youtubeLinkHint)
            }
        }

    }
}

/** Ligação ao YouTube por código (como na app de TV do YouTube): a TV nunca vê a password. */
@Composable
internal fun YouTubePage(vm: LauncherViewModel, first: FocusRequester, onDone: () -> Unit) {
    val s = LocalStrings.current
    val login by vm.youtubeLogin.collectAsStateWithLifecycle()
    val linked by vm.youtubeLinked.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        if (!linked) vm.startYouTubeLogin()
        onDispose { vm.cancelYouTubeLogin() }
    }
    LaunchedEffect(linked) { if (linked) onDone() }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(s.youtubeGoTo, style = MaterialTheme.typography.titleSmall, color = GoatColors.TextDim)
        Text(
            login?.verificationUrl?.removePrefix("https://")?.removePrefix("www.") ?: "google.com/device",
            style = MaterialTheme.typography.headlineSmall,
            color = GoatColors.Sky,
        )
        Spacer(Modifier.height(14.dp))
        Text(s.youtubeEnterCode, style = MaterialTheme.typography.titleSmall, color = GoatColors.TextDim)
        Text(
            login?.userCode ?: "· · ·",
            style = MaterialTheme.typography.displayMedium.copy(letterSpacing = 4.sp, textDirection = TextDirection.Ltr),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            if (login == null) s.youtubeRequesting else s.youtubeWaiting,
            style = MaterialTheme.typography.bodyMedium,
            color = GoatColors.TextFaint,
        )
        Spacer(Modifier.height(18.dp))
        GlassButton(s.cancel, onClick = onDone, modifier = Modifier.focusRequester(first))
    }
}

/** O que o YouTube mostra no "Continuar a ver": os dois, só os vídeos a meio, ou só as recomendações. */
private fun youtubeFeedName(feed: String, s: Strings): String = when (feed) {
    AccountSettings.YT_CONTINUE -> s.ytContinue
    AccountSettings.YT_RECOMMENDED -> s.ytRecommended
    else -> s.ytBoth
}
