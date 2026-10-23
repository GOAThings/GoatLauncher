package cc.slokker.goatlauncher.ui.home

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf
import cc.slokker.goatlauncher.data.AccountSettings
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.IntentCompat
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.data.CustomIcons
import cc.slokker.goatlauncher.data.IconPack
import cc.slokker.goatlauncher.data.LauncherApp
import cc.slokker.goatlauncher.data.PackIcons
import cc.slokker.goatlauncher.ui.LauncherViewModel
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.common.GlassButton
import cc.slokker.goatlauncher.ui.common.GlassRow
import cc.slokker.goatlauncher.ui.common.GoatIcons
import cc.slokker.goatlauncher.ui.common.ImageBrowser
import cc.slokker.goatlauncher.ui.common.SectionLabel
import cc.slokker.goatlauncher.ui.common.TvTextField
import cc.slokker.goatlauncher.ui.common.focusSoon
import cc.slokker.goatlauncher.ui.common.glass
import cc.slokker.goatlauncher.ui.common.onBackKey
import cc.slokker.goatlauncher.ui.common.parentFolder
import cc.slokker.goatlauncher.ui.theme.GoatColors
import cc.slokker.goatlauncher.ui.theme.GoatShapes
import cc.slokker.goatlauncher.ui.theme.TvSafePadding
import androidx.compose.foundation.BorderStroke
import java.io.File

private sealed interface PickerPage {
    data object Sources : PickerPage
    data class Pack(val pack: IconPack) : PickerPage
    data class Files(val dir: File?) : PickerPage
}

/**
 * Escolher o ícone de uma app, como no Projectivy: de um icon pack instalado (grelha com filtro, ou o seletor
 * do próprio pack), de uma imagem da box (pastas, discos USB ou de rede) ou do gestor de ficheiros do sistema.
 */
@Composable
fun IconPicker(app: LauncherApp, current: String?, vm: LauncherViewModel, onDone: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val pkg = app.packageName
    var page by remember { mutableStateOf<PickerPage>(PickerPage.Sources) }
    val first = remember { FocusRequester() }
    val packs = remember { vm.icons.iconPacks() }
    val canBrowseSystem = remember {
        Intent(Intent.ACTION_GET_CONTENT).setType("image/*").resolveActivity(context.packageManager) != null
    }

    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            vm.setIconFromImage(pkg, uri)
            onDone()
        }
    }
    // O seletor do próprio pack devolve um Bitmap ("icon") ou o recurso escolhido (ShortcutIconResource).
    val packPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
        val resource = IntentCompat.getParcelableExtra(data, Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource::class.java)
        val bitmap = IntentCompat.getParcelableExtra(data, "icon", Bitmap::class.java)
        when {
            resource?.resourceName != null ->
                vm.setCustomIcon(pkg, CustomIcons.PACK + resource.packageName + "/" + resource.resourceName.substringAfterLast('/'))
            bitmap != null -> vm.setIconFromBitmap(pkg, bitmap)
            else -> return@rememberLauncherForActivityResult
        }
        onDone()
    }

    LaunchedEffect(page) { if (page !is PickerPage.Files) focusSoon(first) }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(TvSafePadding)
                .onBackKey {
                    when (val p = page) {
                        PickerPage.Sources -> onDone()
                        is PickerPage.Pack -> page = PickerPage.Sources
                        is PickerPage.Files -> page = if (p.dir == null) PickerPage.Sources else PickerPage.Files(parentFolder(p.dir))
                    }
                    true
                },
        ) {
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(680.dp)
                    .fillMaxHeight()
                    .glass(GoatShapes.Panel, tint = Color(0xE6111522))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Text(
                    when (val p = page) {
                        is PickerPage.Pack -> p.pack.label
                        is PickerPage.Files -> s.boxImages
                        PickerPage.Sources -> "${s.changeIcon} · ${app.label}"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                )
                when (val p = page) {
                    PickerPage.Sources -> LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                        if (packs.isNotEmpty()) item { SectionLabel(s.iconPack) }
                        items(packs, key = { it.packageName }) { pack ->
                            GlassRow(
                                title = pack.label,
                                leading = { Icon(GoatIcons.Palette, null, Modifier.size(20.dp)) },
                                trailing = { Icon(GoatIcons.ChevronRight, null, Modifier.size(20.dp)) },
                                onClick = { page = PickerPage.Pack(pack) },
                                modifier = if (pack == packs.first()) Modifier.focusRequester(first) else Modifier,
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                        item {
                            GlassRow(
                                title = s.boxImages,
                                subtitle = s.boxImagesHint,
                                leading = { Icon(GoatIcons.Folder, null, Modifier.size(20.dp)) },
                                trailing = { Icon(GoatIcons.ChevronRight, null, Modifier.size(20.dp)) },
                                onClick = { page = PickerPage.Files(null) },
                                modifier = if (packs.isEmpty()) Modifier.focusRequester(first) else Modifier,
                            )
                        }
                        if (canBrowseSystem) {
                            item {
                                GlassRow(
                                    title = s.fileManager,
                                    leading = { Icon(GoatIcons.Image, null, Modifier.size(20.dp)) },
                                    onClick = { systemPicker.launch("image/*") },
                                )
                            }
                        }
                        if (current != null) {
                            item { Spacer(Modifier.height(8.dp)) }
                            item {
                                GlassRow(
                                    title = s.restoreIcon,
                                    leading = { Icon(GoatIcons.Restore, null, Modifier.size(20.dp)) },
                                    onClick = {
                                        vm.setCustomIcon(pkg, null)
                                        onDone()
                                    },
                                )
                            }
                        }
                    }
                    is PickerPage.Pack -> PackGrid(
                        pack = p.pack,
                        forPackage = pkg,
                        label = app.label,
                        vm = vm,
                        first = first,
                        onPick = {
                            vm.setCustomIcon(pkg, it)
                            onDone()
                        },
                        onOpenPack = {
                            runCatching { packPicker.launch(Intent(CustomIcons.ACTION_PICK_ICON).setPackage(p.pack.packageName)) }
                        },
                    )
                    is PickerPage.Files -> ImageBrowser(
                        dir = p.dir,
                        onDir = { page = PickerPage.Files(it) },
                        onPick = {
                            vm.setIconFromImage(pkg, it)
                            onDone()
                        },
                        first = first,
                    )
                }
            }
        }
    }
}

/** Os ícones de um pack: primeiro os feitos para a app, depois todos; o filtro procura pelo nome do ícone. */
@Composable
private fun PackGrid(
    pack: IconPack,
    forPackage: String,
    label: String,
    vm: LauncherViewModel,
    first: FocusRequester,
    onPick: (String) -> Unit,
    onOpenPack: () -> Unit,
) {
    val s = LocalStrings.current
    val icons by produceState<PackIcons?>(null, pack) { value = vm.icons.packIcons(pack.packageName, forPackage, label) }
    var filter by remember { mutableStateOf("") }
    val hasIcons = icons?.all?.isNotEmpty() == true
    // O foco vai para o primeiro ícone quando a lista chega (sem ícones, para o botão do pack).
    LaunchedEffect(icons != null) { if (icons != null) focusSoon(first) }

    Column {
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TvTextField(filter, { filter = it }, s.filterHint, Modifier.weight(1f))
            GlassButton(
                s.openIconPack,
                onClick = onOpenPack,
                modifier = if (icons != null && !hasIcons) Modifier.focusRequester(first) else Modifier,
            )
        }
        Spacer(Modifier.height(10.dp))
        val list = icons
        if (list == null) {
            Text(s.loading, color = GoatColors.TextFaint, modifier = Modifier.padding(12.dp))
            return@Column
        }
        val query = filter.trim().lowercase().replace(' ', '_')
        val sections: List<Pair<String, List<String>>> = if (query.isEmpty()) {
            listOf(s.suggested to list.suggested, s.allIcons to (list.all - list.suggested.toSet()))
        } else {
            listOf(s.allIcons to list.all.filter { it.lowercase().contains(query) })
        }.filter { it.second.isNotEmpty() }
        if (sections.isEmpty()) {
            Text(s.noIcons, color = GoatColors.TextFaint, modifier = Modifier.padding(12.dp))
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            sections.forEachIndexed { si, (title, names) ->
                item(key = "h-$title", span = { GridItemSpan(maxLineSpan) }) { SectionLabel(title) }
                itemsIndexed(names, key = { _, name -> "$si-$name" }) { i, name ->
                    val spec = CustomIcons.PACK + pack.packageName + "/" + name
                    PackIconCell(
                        spec = spec,
                        vm = vm,
                        onClick = { onPick(spec) },
                        modifier = if (si == 0 && i == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun PackIconCell(spec: String, vm: LauncherViewModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(null, spec) { value = vm.icons.load(spec) }
    val shape = GoatShapes.Control
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x1FFFFFFF),
            focusedContainerColor = Color(0x33FFFFFF),
            pressedContainerColor = Color(0x33FFFFFF),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), inset = 0.dp, shape = shape)),
    ) {
        image?.let { CustomIconImage(it, background = Color.Transparent) }
    }
}

/**
 * Um ícone escolhido pela pessoa dentro de um cartão 16:9: um banner (largo) enche o cartão;
 * um ícone quadrado (packs de telemóvel) fica ao centro sobre [background]. Um ícone com fundo transparente
 * (ex.: os logos brancos do Projectivy Icon Pack) fica sobre um fundo escuro, e não sobre a cor da app
 * (que no YouTube ou na Play Store é branca, e o logo desaparecia).
 */
@Composable
fun CustomIconImage(image: ImageBitmap, background: Color) {
    val transparent = remember(image) { image.hasTransparentEdges() }
    val backdrop = if (transparent) iconBackdrop(LocalIconBackground.current) else Modifier.background(background)
    if (image.width >= image.height * 1.4f) {
        Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().then(if (transparent) backdrop else Modifier))
    } else {
        Box(Modifier.fillMaxSize().then(backdrop), contentAlignment = Alignment.Center) {
            Image(image, null, modifier = Modifier.fillMaxHeight(0.7f).aspectRatio(image.width.toFloat() / image.height))
        }
    }
}

/** O fundo dos ícones transparentes escolhido em Aparência (AccountSettings.iconBackground). */
val LocalIconBackground = staticCompositionLocalOf { AccountSettings.BG_GRAPHITE }

/** Fundo grafite dos ícones transparentes, um pouco mais claro em cima (como os cartões escuros do tvOS). */
val GraphiteBackground = Brush.verticalGradient(listOf(Color(0xFF2C2E34), Color(0xFF121317)))

private fun iconBackdrop(spec: String): Modifier = when (spec) {
    AccountSettings.BG_NONE -> Modifier
    AccountSettings.BG_GRAPHITE -> Modifier.background(GraphiteBackground)
    else -> parseColor(spec)?.let { Modifier.background(it) } ?: Modifier.background(GraphiteBackground)
}

/** Se um cartão com este ícone fica sem fundo nenhum (ícone transparente e fundo "transparente"). */
@Composable
fun isSeeThrough(image: ImageBitmap?): Boolean {
    val transparent = remember(image) { image?.hasTransparentEdges() == true }
    return transparent && LocalIconBackground.current == AccountSettings.BG_NONE
}

/** Se as bordas do ícone são (quase todas) transparentes: vê-se uma amostra de pontos à volta. */
private fun ImageBitmap.hasTransparentEdges(): Boolean {
    val bitmap = asAndroidBitmap()
    if (!bitmap.hasAlpha()) return false
    val w = bitmap.width - 1
    val h = bitmap.height - 1
    val points = (0..8).flatMap { i ->
        val x = w * i / 8
        val y = h * i / 8
        listOf(x to 0, x to h, 0 to y, w to y)
    }
    // Uma imagem em memória da GPU (HARDWARE) não deixa ler píxeis: fica como opaca.
    val clear = runCatching { points.count { (x, y) -> android.graphics.Color.alpha(bitmap.getPixel(x, y)) < 128 } }.getOrDefault(0)
    return clear >= points.size / 2
}
