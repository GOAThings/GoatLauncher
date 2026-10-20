package cc.slokker.goatlauncher.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.data.ImageImport
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.theme.GoatColors
import cc.slokker.goatlauncher.ui.theme.GoatShapes
import coil3.compose.AsyncImage
import java.io.File

/** A pasta acima de [dir], ou null (a lista de discos) se [dir] já é um disco. */
fun parentFolder(dir: File): File? = if (ImageImport.isRoot(dir)) null else dir.parentFile

/**
 * Navegador de imagens da box: discos → pastas → imagens. [dir] null mostra os discos.
 * A pasta atual fica com quem chama (Voltar sobe uma pasta: ver [parentFolder]).
 */
@Composable
fun ImageBrowser(dir: File?, onDir: (File?) -> Unit, onPick: (File) -> Unit, first: FocusRequester) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) request.launch(permission) }

    if (!granted) {
        Column(Modifier.padding(12.dp)) {
            Text(s.storagePermission, style = MaterialTheme.typography.bodyMedium, color = GoatColors.TextDim)
            Spacer(Modifier.height(12.dp))
            GlassButton(s.allow, onClick = { request.launch(permission) }, modifier = Modifier.focusRequester(first))
        }
        LaunchedEffect(Unit) { focusSoon(first) }
        return
    }

    val entries by produceState<List<File>?>(null, dir) { value = if (dir == null) ImageImport.roots() else ImageImport.list(dir) }
    LaunchedEffect(dir, entries != null) { if (entries != null) focusSoon(first) }

    Column {
        Text(
            dir?.path ?: s.boxImagesHint,
            style = MaterialTheme.typography.bodySmall,
            color = GoatColors.TextFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
        )
        val list = entries ?: return@Column
        // Cada pasta começa no topo (senão a lista ficava no sítio da pasta anterior e o foco não tinha onde cair).
        val listState = remember(dir) { LazyListState() }
        LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 4.dp)) {
            if (dir != null) {
                item(key = "..") {
                    GlassRow(
                        title = s.upFolder,
                        leading = { Icon(GoatIcons.ArrowUp, null, Modifier.size(20.dp)) },
                        onClick = { onDir(parentFolder(dir)) },
                        modifier = Modifier.focusRequester(first),
                    )
                }
            }
            itemsIndexed(list, key = { _, f -> f.path }) { i, f ->
                val modifier = if (dir == null && i == 0) Modifier.focusRequester(first) else Modifier
                when {
                    dir == null -> GlassRow(
                        title = if (i == 0) s.internalStorage else f.name,
                        leading = { Icon(GoatIcons.Storage, null, Modifier.size(20.dp)) },
                        onClick = { onDir(f) },
                        modifier = modifier,
                    )
                    f.isDirectory -> GlassRow(
                        title = f.name,
                        leading = { Icon(GoatIcons.Folder, null, Modifier.size(20.dp)) },
                        onClick = { onDir(f) },
                    )
                    else -> GlassRow(
                        title = f.name,
                        leading = {
                            Box(
                                Modifier.size(64.dp, 36.dp).clip(GoatShapes.Small).background(Color(0x33FFFFFF)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(GoatIcons.Image, null, Modifier.size(18.dp), tint = GoatColors.TextFaint)
                                AsyncImage(f, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        },
                        onClick = { onPick(f) },
                    )
                }
            }
            if (dir != null && list.isEmpty()) {
                item { Text(s.noImages, color = GoatColors.TextFaint, modifier = Modifier.padding(12.dp)) }
            }
        }
    }
}

/** Campo de texto de uma linha para a TV (ex.: o filtro dos ícones). O ↓ do comando passa para o que está por baixo. */
@Composable
fun TvTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = GoatColors.Text),
        cursorBrush = SolidColor(GoatColors.Sky),
        modifier = modifier
            .onPreviewKeyEvent { e ->
                if (e.key == Key.DirectionDown && e.type == KeyEventType.KeyDown) focusManager.moveFocus(FocusDirection.Down) else false
            }
            .glass(GoatShapes.Control, tint = Color(0x33FFFFFF)),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (value.isEmpty()) Text(placeholder, color = GoatColors.TextFaint, style = MaterialTheme.typography.bodyLarge)
                inner()
            }
        },
    )
}

/** Pede o foco a [requester] durante alguns frames (o elemento pode ainda não estar composto). */
suspend fun focusSoon(requester: FocusRequester) {
    repeat(5) {
        withFrameNanos { }
        if (requester.requestFocusSafely()) return
    }
}
