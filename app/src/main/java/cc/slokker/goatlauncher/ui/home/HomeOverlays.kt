package cc.slokker.goatlauncher.ui.home

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
import androidx.compose.runtime.withFrameNanos
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.data.LauncherApp
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.common.GlassRow
import cc.slokker.goatlauncher.ui.common.dialogPanel
import cc.slokker.goatlauncher.ui.common.requestFocusSafely
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
        LaunchedEffect(Unit) {
            withFrameNanos { }
            field.requestFocusSafely()
        }
    }
}

/**
 * Pressão longa num banner: Mover, Pôr/Tirar dos favoritos, Mudar ícone, Mudar nome, Esconder, Informação da app,
 * Desinstalar.
 */
@Composable
internal fun AppMenu(
    app: LauncherApp,
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
    // Voltar é tratado no ecrã (fecha o menu); aqui só se escurece o fundo e se prende o foco ao menu.
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .dialogPanel(380.dp)
                .padding(16.dp)
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup(),
        ) {
            Text(app.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp, bottom = 10.dp))
            GlassRow(title = s.move, onClick = onMove, modifier = Modifier.focusRequester(first))
            if (favorite != null) GlassRow(title = if (favorite) s.removeFromFavorites else s.addToFavorites, onClick = onToggleFavorite)
            GlassRow(title = s.changeIcon, onClick = onChangeIcon)
            GlassRow(title = s.rename, onClick = onRename)
            GlassRow(title = if (hidden) s.showOnHome else s.hide, onClick = onHide)
            GlassRow(title = if (continueOff) s.showContinue else s.hideContinue, onClick = onToggleContinue)
            if (lock != null) GlassRow(title = if (lock) s.unlockAppMenu else s.lockApp, onClick = onToggleLock)
            GlassRow(title = s.appInfo, onClick = onInfo)
            if (onUninstall != null) GlassRow(title = s.uninstall, onClick = onUninstall)
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            first.requestFocusSafely()
        }
    }
}

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
