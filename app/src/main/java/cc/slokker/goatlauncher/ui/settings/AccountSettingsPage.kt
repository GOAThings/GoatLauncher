package cc.slokker.goatlauncher.ui.settings

import androidx.compose.ui.text.style.TextDirection
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import cc.slokker.goatlauncher.data.SystemIntents
import cc.slokker.goatlauncher.data.api.CONTACT_EMAIL
import cc.slokker.goatlauncher.ui.common.qrBitmap
import cc.slokker.goatlauncher.ui.theme.GoatShapes
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
    LazyColumn(state = rememberPaneListState(), contentPadding = PaddingValues(vertical = 4.dp)) {
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
    LazyColumn(state = rememberPaneListState(), contentPadding = PaddingValues(vertical = 4.dp)) {
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

        // Sempre à vista, com ou sem conta nesta TV: o site pede o login ele próprio.
        item { SectionLabel(s.privacy) }
        item { NavRow(s.privacyPolicy, onClick = { onPage(Page.Privacy) }, subtitle = s.privacyPolicyHint) }
        item { NavRow(s.deleteAccount, onClick = { onPage(Page.DeleteAccount) }, subtitle = s.deleteAccountHint) }
    }
}

/**
 * Uma página do site (a política de privacidade, o apagar da conta): muitas TVs não têm browser, por isso o endereço
 * e um QR para abrir no telemóvel; se houver um browser nesta TV, também se pode abrir aqui. O endereço nunca leva o
 * token: o site pede o login ele próprio.
 */
@Composable
internal fun SiteLinkPage(vm: LauncherViewModel, url: String, first: FocusRequester) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val qr = remember(url) { qrBitmap(url) }
    val browser = remember(url) { SystemIntents.browser(context, url) }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).verticalScroll(rememberScrollState())) {
        Text(s.linkScan, style = MaterialTheme.typography.titleSmall, color = GoatColors.TextDim)
        Text(
            url.removePrefix("https://"),
            style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Ltr),
            color = GoatColors.Sky,
        )
        Spacer(Modifier.height(14.dp))
        // Sem browser, o QR fica com o foco: a página tem de ter onde o pôr (e o Voltar funciona na mesma).
        Box(
            Modifier
                .size(220.dp)
                .clip(GoatShapes.Panel)
                .background(Color.White)
                .then(if (browser == null) Modifier.focusRequester(first).focusable() else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Image(qr, contentDescription = s.linkQrDescription, Modifier.size(196.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(s.linkContact(CONTACT_EMAIL), style = MaterialTheme.typography.bodyMedium, color = GoatColors.TextFaint)
        if (browser != null) {
            Spacer(Modifier.height(10.dp))
            GlassRow(
                title = s.linkOpenHere,
                onClick = { if (!SystemIntents.start(context, browser)) vm.toast(s.cantOpen) },
                modifier = Modifier.focusRequester(first),
            )
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
