package cc.slokker.goatlauncher.ui.home

import cc.slokker.goatlauncher.ui.common.focusSoon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import cc.slokker.goatlauncher.ui.common.GoatIcons
import cc.slokker.goatlauncher.ui.common.glass
import cc.slokker.goatlauncher.ui.common.PinPad
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cc.slokker.goatlauncher.ui.common.TvTextField
import cc.slokker.goatlauncher.ui.common.GlassButton
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.data.LauncherApp
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.common.GlassRow
import cc.slokker.goatlauncher.ui.common.dialogPanel
import cc.slokker.goatlauncher.ui.theme.GoatColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Nome da app no launcher (ex.: "YouTube" em vez de "TizenTube"). Guardar vazio repõe o nome da app. */
@Composable
internal fun RenameDialog(app: LauncherApp, custom: Boolean, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val field = remember { FocusRequester() }
    var text by remember { mutableStateOf(app.label) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .dialogPanel(460.dp)
                .padding(20.dp),
        ) {
            Text(s.rename, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            TvTextField(text, { text = it }, s.nameHint, Modifier.fillMaxWidth().focusRequester(field), onDone = { onSave(text) })
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton(s.save, onClick = { onSave(text) })
                if (custom) GlassButton(s.restoreName, onClick = { onSave(null) })
                GlassButton(s.cancel, onClick = onDismiss)
            }
        }
        LaunchedEffect(Unit) { field.focusSoon() }
    }
}

/**
 * Da primeira vez, se o Home ainda abre o ecrã inicial do Android: ligar o "Manter como ecrã inicial" na Acessibilidade
 * ([onTurnOn]). Escolher o GoatLauncher em "App de ecrã inicial predefinida" não chega: no Google TV o Android passa a
 * dizer que é ele, mas o Home continua a abrir o launcher da Google (visto no emulador).
 */
@Composable
internal fun HomeSetupDialog(onTurnOn: () -> Unit, onLater: () -> Unit) {
    val s = LocalStrings.current
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onLater, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .dialogPanel(640.dp)
                .padding(20.dp),
        ) {
            Text(s.homeSetupTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            Text(s.homeSetupText, style = MaterialTheme.typography.bodyMedium, color = GoatColors.TextDim)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton(s.homeSetupTurnOn, onClick = onTurnOn, modifier = Modifier.focusRequester(first))
                GlassButton(s.homeSetupLater, onClick = onLater)
            }
        }
        LaunchedEffect(Unit) { first.focusSoon() }
    }
}

/**
 * O menu de uma app (manter OK): abre junto ao banner dela ([anchor], em coordenadas da janela; null = ao centro),
 * como um balão de vidro com uma pílula branca no foco. Primeiro só o principal; "Mais opções…" mostra o resto no
 * mesmo menu.
 */
@Composable
internal fun AppMenu(
    app: LauncherApp,
    anchor: Rect?,
    onMove: () -> Unit,
    /** A app está nos favoritos; null = sem a opção. */
    favorite: Boolean?,
    onToggleFavorite: () -> Unit,
    onChangeIcon: () -> Unit,
    onRename: () -> Unit,
    hidden: Boolean,
    onHide: () -> Unit,
    continueOff: Boolean,
    onToggleContinue: () -> Unit,
    onInfo: () -> Unit,
    lock: Boolean?,
    onToggleLock: () -> Unit,
    /** null nas apps do sistema. */
    onUninstall: (() -> Unit)?,
) {
    val s = LocalStrings.current
    val first = remember { FocusRequester() }
    val firstMore = remember { FocusRequester() }
    var more by remember(app.packageName) { mutableStateOf(false) }
    // Voltar é tratado no ecrã (fecha o menu); aqui só se escurece um pouco o fundo e se prende o foco ao menu.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.15f)),
    ) {
        Column(
            Modifier
                .placeNear(anchor)
                .width(MENU_WIDTH)
                .glass(MenuShape, tint = GoatColors.PanelTint)
                .padding(8.dp)
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MenuPill(GoatIcons.Move, s.move, onMove, Modifier.focusRequester(first))
            if (favorite != null) {
                MenuPill(if (favorite) GoatIcons.Star else GoatIcons.StarOutline, if (favorite) s.removeFromFavorites else s.addToFavorites, onToggleFavorite)
            }
            MenuPill(GoatIcons.Palette, s.changeIcon, onChangeIcon)
            MenuPill(GoatIcons.Info, s.appInfo, onInfo)
            if (!more) {
                MenuPill(GoatIcons.More, s.moreOptions, { more = true })
            } else {
                MenuPill(GoatIcons.Edit, s.rename, onRename, Modifier.focusRequester(firstMore))
                MenuPill(if (hidden) GoatIcons.Visibility else GoatIcons.VisibilityOff, if (hidden) s.showOnHome else s.hide, onHide)
                MenuPill(GoatIcons.Play, if (continueOff) s.showContinue else s.hideContinue, onToggleContinue)
                if (lock != null) MenuPill(GoatIcons.Lock, if (lock) s.unlockAppMenu else s.lockApp, onToggleLock)
                if (onUninstall != null) MenuPill(GoatIcons.Delete, s.uninstall, onUninstall)
            }
        }
        LaunchedEffect(Unit) { first.focusSoon() }
        // Ao abrir "Mais opções…", o foco passa para a primeira das novas.
        LaunchedEffect(more) { if (more) firstMore.focusSoon() }
    }
}

/** Uma opção do menu da app: ícone e texto; com foco, uma pílula branca com o texto escuro. */
@Composable
private fun MenuPill(icon: ImageVector, title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = GoatColors.Text,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White.copy(alpha = 0.8f),
            pressedContentColor = Color.Black,
        ),
        // Sem crescer: só muda de cor.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Põe o menu junto ao banner [anchor]: um pouco para dentro da esquerda dele e por cima (por baixo se não couber),
 * sem sair do ecrã. Sem banner conhecido, fica ao centro.
 */
private fun Modifier.placeNear(anchor: Rect?): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val margin = MENU_MARGIN.roundToPx()
    val gap = MENU_GAP.roundToPx()
    val w = placeable.width
    val h = placeable.height
    val (x, y) = if (anchor == null) {
        (constraints.maxWidth - w) / 2 to (constraints.maxHeight - h) / 2
    } else {
        val x = (anchor.left.toInt() + MENU_INSET.roundToPx()).coerceIn(margin, (constraints.maxWidth - w - margin).coerceAtLeast(margin))
        val above = anchor.top.toInt() - gap - h
        val below = anchor.bottom.toInt() + gap
        val y = when {
            above >= margin -> above
            below + h <= constraints.maxHeight - margin -> below
            else -> (constraints.maxHeight - h - margin).coerceAtLeast(margin)
        }
        x to y
    }
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
}

private val MENU_WIDTH = 300.dp
private val MenuShape = RoundedCornerShape(26.dp)
private val MENU_MARGIN = 24.dp
private val MENU_GAP = 12.dp
/** Quanto o menu entra para dentro do banner, a partir da esquerda (como um balão a sair dele). */
private val MENU_INSET = 32.dp

/**
 * O teclado do código por cima do ecrã (Voltar cancela: ver o ecrã). [onPin] devolve false se estiver errado.
 * Depois de muitas tentativas erradas, [waitMs] diz quanto falta: até lá o teclado mostra o tempo e não aceita nada.
 */
@Composable
internal fun PinOverlay(title: String, waitMs: () -> Long, onPin: (String) -> Boolean) {
    val s = LocalStrings.current
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var wait by remember { mutableLongStateOf(waitMs()) }
    LaunchedEffect(attempt) {
        while (wait > 0) {
            delay(wait.coerceAtMost(1000))
            wait = waitMs()
            if (wait == 0L) error = null
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .dialogPanel(440.dp)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A cada tentativa errada o teclado recomeça (e volta a mostrar o erro, mesmo que seja o mesmo texto).
            key(attempt) {
                PinPad(
                    title = title,
                    message = if (wait > 0) s.pinWait(waitText(wait)) else error,
                    waiting = wait > 0,
                    onDone = { pin ->
                        if (!onPin(pin)) {
                            error = s.wrongPin
                            wait = waitMs()
                            attempt++
                        }
                    },
                )
            }
            Text(s.forgotPin, style = MaterialTheme.typography.bodySmall, color = GoatColors.TextFaint, textAlign = TextAlign.Center)
        }
    }
}

/** Minutos e segundos, arredondados para cima. */
private fun waitText(ms: Long): String {
    val seconds = (ms + 999) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
