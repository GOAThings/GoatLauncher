package cc.slokker.goatlauncher.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import cc.slokker.goatlauncher.ui.common.TvTextField
import cc.slokker.goatlauncher.ui.common.GlassButton
import cc.slokker.goatlauncher.ui.settings.SettingsScreen
import cc.slokker.goatlauncher.data.WeatherNow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.LauncherApp
import cc.slokker.goatlauncher.data.SystemIntents
import cc.slokker.goatlauncher.data.WatchNextItem
import cc.slokker.goatlauncher.ui.LauncherViewModel
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.Screen
import cc.slokker.goatlauncher.ui.common.GlassRow
import cc.slokker.goatlauncher.ui.common.GoatIcons
import cc.slokker.goatlauncher.ui.common.glass
import cc.slokker.goatlauncher.ui.common.requestFocusSafely
import cc.slokker.goatlauncher.ui.theme.GoatColors
import cc.slokker.goatlauncher.ui.theme.GoatShapes
import cc.slokker.goatlauncher.ui.theme.ShelfTitleStyle
import cc.slokker.goatlauncher.ui.theme.TvSafePadding
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

/** Apps no Início (as primeiras da ordem guardada); as restantes estão no separador Apps. */
const val FavoriteCount = 5

/** Colunas da grelha do separador Apps (a primeira linha são os favoritos, alinhada com o Início). */
private const val AppsColumns = 5

/** Quanto tempo o foco tem de ficar num favorito para o "Continuar a ver" mudar para ele. */
private const val SECTION_SETTLE_MS = 150L

/** Quantos cartões, no máximo, na fila do estilo "Down Under". */
private const val DOWN_UNDER_MAX = 20

private val TileWidth = 148.dp
private val TileGap = 24.dp
private val TilesRowWidth = TileWidth * FavoriteCount + TileGap * (FavoriteCount - 1)
private val SafeH = 48.dp

/**
 * Ecrã inicial, com dois separadores (Início e Apps) e as Definições à parte.
 *  - Início: saudação, o "Continuar a ver" do favorito com foco e os 5 favoritos ao centro.
 *    O "Continuar a ver" tem dois estilos (Definições): uma fila de cartões, ou um destaque em ecrã inteiro
 *    (o conteúdo mais recente da app enche o fundo; ‹ › passa pelos outros, OK continua).
 *  - Apps: todas as apps numa grelha; a primeira linha são os favoritos. Mover uma app entre linhas
 *    muda o que está no Início (como no tvOS).
 */
@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val screen by vm.screen.collectAsStateWithLifecycle()
    val onApps = screen == Screen.Apps
    val onSettings = screen == Screen.Settings
    val weather by vm.weather.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val watchNext by vm.watchNext.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val list = apps.orEmpty()
    val currentList by rememberUpdatedState(list)
    val favorites = list.take(FavoriteCount)
    val shelfStyle = account.continueStyle == AccountSettings.STYLE_SHELF
    val downUnder = account.continueStyle == AccountSettings.STYLE_DOWN_UNDER

    var focusedPkg by remember { mutableStateOf(vm.focusedPackage) }
    // O "Continuar a ver" só muda quando o foco assenta num favorito: a correr pela fila não carrega cada um.
    // O foco é lido num snapshotFlow (não na composição): mudar de app não volta a compor o ecrã todo.
    var sectionPkg by remember { mutableStateOf(vm.focusedPackage) }
    LaunchedEffect(Unit) {
        snapshotFlow { focusedPkg }.collectLatest { pkg ->
            if (sectionPkg != null) delay(SECTION_SETTLE_MS)
            sectionPkg = pkg
        }
    }
    val sectionApp = favorites.firstOrNull { it.packageName == sectionPkg }?.takeIf { account.showContinueWatching && !onApps }
    val sectionItems = sectionApp?.let { watchNext[it.packageName] }.orEmpty()
    var shelfIndex by remember(sectionPkg) { mutableIntStateOf(0) }
    val shelfItem = sectionItems.getOrNull(shelfIndex.coerceIn(0, (sectionItems.size - 1).coerceAtLeast(0)))
        ?.takeIf { shelfStyle }

    var menuFor by remember { mutableStateOf<LauncherApp?>(null) }
    var renameFor by remember { mutableStateOf<LauncherApp?>(null) }
    var tabBarFocused by remember { mutableStateOf(false) }
    var iconFor by remember { mutableStateOf<LauncherApp?>(null) }
    var moving by remember { mutableStateOf<String?>(null) }
    val searchIntent = remember { SystemIntents.search(context) }

    // O foco vai para a app [targetPkg] (o banner com o [target]) sempre que [focusRequest] muda.
    val target = remember { FocusRequester() }
    val favFirst = remember { FocusRequester() }
    var targetPkg by remember { mutableStateOf(vm.focusedPackage) }
    var focusRequest by remember { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()

    /** Posição de [pkg] na grelha das Apps (contando os dois títulos de secção). */
    fun gridIndex(pkg: String): Int {
        val i = list.indexOfFirst { it.packageName == pkg }
        return if (i < FavoriteCount) 1 + i else 2 + i
    }

    LaunchedEffect(focusRequest, list.isNotEmpty()) {
        if (list.isEmpty()) return@LaunchedEffect
        val pool = if (onApps) list else favorites
        val pkg = targetPkg?.takeIf { p -> pool.any { it.packageName == p } } ?: pool.first().packageName
        targetPkg = pkg
        if (onApps) {
            val idx = gridIndex(pkg)
            if (gridState.layoutInfo.visibleItemsInfo.none { it.index == idx }) gridState.scrollToItem(idx)
        }
        // O banner só tem o FocusRequester depois de recompor: tenta durante alguns frames.
        repeat(5) {
            withFrameNanos { }
            if (target.requestFocusSafely()) return@LaunchedEffect
        }
    }

    fun focusApp(pkg: String?) {
        targetPkg = pkg
        focusRequest++
    }

    fun lastFavorite() = favorites.firstOrNull { it.packageName == focusedPkg }?.packageName ?: favorites.firstOrNull()?.packageName

    fun endMove() {
        if (moving != null) {
            moving = null
            vm.finishMove()
        }
    }

    // Botão Home com o launcher já aberto: volta ao Início, no primeiro favorito.
    LaunchedEffect(Unit) {
        vm.homeEvents.collect {
            endMove()
            menuFor = null
            iconFor = null
            focusApp(currentList.firstOrNull()?.packageName)
        }
    }

    @Composable
    fun Tile(app: LauncherApp, home: Boolean, modifier: Modifier = Modifier) {
        val spec = device.icons[app.packageName]
        val custom by produceState<ImageBitmap?>(null, spec) { value = spec?.let { vm.icons.load(it) } }
        AppTile(
            app = app,
            width = TileWidth,
            custom = custom,
            showLabel = account.showAppName,
            hasContinue = home && !downUnder && account.showContinueWatching && !watchNext[app.packageName].isNullOrEmpty(),
            moving = moving == app.packageName,
            onFocused = {
                focusedPkg = app.packageName
                vm.focusedPackage = app.packageName
            },
            onClick = { vm.launchApp(context, app.packageName) },
            onLongClick = { menuFor = app },
            bannerModifier = modifier.then(if (app.packageName == targetPkg) Modifier.focusRequester(target) else Modifier),
        )
    }

    @Composable
    fun SectionHeader(title: String, hint: Boolean, modifier: Modifier = Modifier) {
        Row(modifier, verticalAlignment = Alignment.Bottom) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            if (hint) {
                Text(
                    if (moving != null) s.moveHint else s.holdToRearrange,
                    style = MaterialTheme.typography.labelSmall,
                    color = GoatColors.TextFaint,
                )
            }
        }
    }

    @Composable
    fun FavoritesBlock() {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            SectionHeader(s.favorites, hint = true, modifier = Modifier.width(TilesRowWidth))
            Spacer(Modifier.height(12.dp))
            when {
                apps == null -> Spacer(Modifier.height(TileWidth * 9 / 16 + 30.dp))
                list.isEmpty() -> Text(s.noApps, Modifier.padding(vertical = 30.dp), color = GoatColors.TextDim)
                // Ao descer dos cartões ou dos separadores, volta ao último favorito com foco.
                else -> Row(
                    Modifier.width(TilesRowWidth).focusRestorer(favFirst),
                    horizontalArrangement = Arrangement.spacedBy(TileGap, Alignment.CenterHorizontally),
                ) {
                    favorites.forEachIndexed { i, app ->
                        key(app.packageName) {
                            Tile(app, home = true, modifier = if (i == 0) Modifier.focusRequester(favFirst) else Modifier)
                        }
                    }
                }
            }
        }
    }

    // Fundo do destaque: a imagem do conteúdo, se tiver a forma do ecrã. Os logos quadrados (ex.: canais da
    // GoatTV) não enchem o ecrã: fica o fundo normal e o logo aparece pequeno no texto.
    val shelfPainter = rememberAsyncImagePainter(
        ImageRequest.Builder(context).data(shelfItem?.imageUri).crossfade(400).build(),
    )
    val shelfState by shelfPainter.state.collectAsState()
    val shelfSize = (shelfState as? AsyncImagePainter.State.Success)?.painter?.intrinsicSize
    // Os canais em direto (ex.: GoatTV) só têm o logo: nunca enchem o ecrã, mesmo que o logo seja largo.
    val shelfFullBleed = shelfItem != null && !shelfItem.isChannel && shelfItem.imageUri != null && shelfSize != null && shelfSize.height > 0f &&
        abs(shelfSize.width / shelfSize.height - 16f / 9f) < 0.25f

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                val pkg = moving
                val up = e.type == KeyEventType.KeyUp
                when {
                    // A mover: no Início só ‹ › (a app fica nos favoritos); nas Apps também ↑ ↓ (uma linha inteira).
                    pkg != null -> when (e.key) {
                        Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> {
                            if (e.type == KeyEventType.KeyDown) {
                                val delta = when (e.key) {
                                    Key.DirectionLeft -> -1
                                    Key.DirectionRight -> 1
                                    Key.DirectionUp -> -AppsColumns
                                    else -> AppsColumns
                                }
                                val idx = list.indexOfFirst { it.packageName == pkg }
                                val allowed = onApps || (abs(delta) == 1 && idx + delta in 0 until minOf(FavoriteCount, list.size))
                                if (allowed) vm.moveApp(pkg, delta)
                                focusApp(pkg)
                            }
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back -> {
                            if (up) endMove()
                            true
                        }
                        else -> false
                    }
                    // As Definições tratam o seu Voltar (páginas); se o foco estiver nos separadores, ver onKeyEvent.
                    e.key == Key.Back && onSettings -> false
                    // É o ecrã inicial: Voltar não sai daqui. Nas Apps, volta ao Início.
                    e.key == Key.Back -> {
                        if (up && onApps) {
                            vm.openHome()
                            focusApp(lastFavorite())
                        }
                        true
                    }
                    else -> false
                }
            }
            // Voltar nas Definições que ninguém tratou (o foco está nos separadores): volta ao Início.
            .onKeyEvent { e ->
                if (e.key != Key.Back || !onSettings) return@onKeyEvent false
                if (e.type == KeyEventType.KeyUp) {
                    vm.openHome()
                    focusApp(lastFavorite())
                }
                true
            },
    ) {
        AnimatedVisibility(visible = shelfFullBleed && !onApps, enter = fadeIn(tween(400)), exit = fadeOut(tween(300))) {
            Image(shelfPainter, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // Escurece o topo e o fundo (e a esquerda, com o destaque) para o texto se ler sobre qualquer imagem.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.25f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.78f),
                    ),
                ),
        )
        // Sempre que há destaque (com a imagem do conteúdo ou sobre o fundo normal), para o texto grande se ler.
        val sideScrim by animateFloatAsState(if (shelfItem != null && !onApps) 1f else 0f, tween(400), label = "side")
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = sideScrim }
                .background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.7f), 0.45f to Color.Black.copy(alpha = 0.35f), 0.75f to Color.Transparent)),
        )
        val appsDim by animateFloatAsState(if (onApps || onSettings) 1f else 0f, tween(250), label = "dim")
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = appsDim }.background(Color.Black.copy(alpha = 0.6f)))

        Column(Modifier.fillMaxSize().padding(TvSafePadding)) {
            TopBar(
                selected = when {
                    onApps -> 1
                    onSettings -> 2
                    else -> 0
                },
                clock24h = account.clock24h,
                showGreeting = account.showGreeting,
                weather = weather?.takeIf { account.showWeather },
                hasSearch = searchIntent != null,
                onHome = vm::openHome,
                onApps = vm::openApps,
                onSettings = vm::openSettings,
                onSearch = { if (!SystemIntents.start(context, searchIntent)) vm.toast(s.noSearch) },
                onFocusChanged = { tabBarFocused = it },
            )

            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(150)) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "tab",
            ) { tab ->
                if (tab == Screen.Settings) {
                    // Definições no próprio separador. Escolhidas pelo foco nos separadores, não tiram de lá o foco.
                    SettingsScreen(
                        vm,
                        embedded = true,
                        autoFocus = !tabBarFocused,
                        onExit = {
                            vm.openHome()
                            focusApp(lastFavorite())
                        },
                    )
                } else if (tab == Screen.Apps) {
                    // ---- Apps: todas as apps, os favoritos na primeira linha ----
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        if (list.isEmpty()) {
                            Text(s.noApps, Modifier.padding(top = 60.dp), color = GoatColors.TextDim)
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(AppsColumns),
                                state = gridState,
                                modifier = Modifier.width(TilesRowWidth + 24.dp).fillMaxHeight(),
                                horizontalArrangement = Arrangement.spacedBy(TileGap),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 24.dp),
                            ) {
                                item(key = "h-fav", span = { GridItemSpan(maxLineSpan) }) { SectionHeader(s.favorites, hint = true) }
                                items(favorites, key = { it.packageName }) { app -> Tile(app, home = false) }
                                if (list.size > FavoriteCount) {
                                    item(key = "h-more", span = { GridItemSpan(maxLineSpan) }) {
                                        SectionHeader(s.moreApps, hint = false, modifier = Modifier.padding(top = 8.dp))
                                    }
                                    items(list.drop(FavoriteCount), key = { it.packageName }) { app -> Tile(app, home = false) }
                                }
                            }
                        }
                    }
                } else {
                    // ---- Início ----
                    Column(Modifier.fillMaxSize()) {
                        if (shelfStyle) {
                            Spacer(Modifier.weight(1f))
                            AnimatedContent(
                                targetState = sectionApp?.takeIf { sectionItems.isNotEmpty() },
                                contentKey = { it?.packageName },
                                transitionSpec = { fadeIn(tween(260, delayMillis = 80)) togetherWith fadeOut(tween(140)) },
                                label = "shelf",
                            ) { app ->
                                val item = shelfItem
                                if (app != null && item != null && item.packageName == app.packageName) {
                                    ShelfHero(
                                        app = app,
                                        item = item,
                                        count = sectionItems.size,
                                        showLogo = !shelfFullBleed,
                                        onPrev = { if (shelfIndex > 0) shelfIndex-- },
                                        onNext = { if (shelfIndex < sectionItems.size - 1) shelfIndex++ },
                                        onOpen = { vm.openWatchNext(context, item) },
                                    )
                                } else {
                                    Spacer(Modifier.fillMaxWidth())
                                }
                            }
                            Spacer(Modifier.height(28.dp))
                        } else if (downUnder) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                            // Entre favoritos, a secção troca com um fade (e a altura acompanha), em vez de saltar.
                            AnimatedContent(
                                targetState = sectionApp?.takeIf { sectionItems.isNotEmpty() }?.let { it to sectionItems },
                                contentKey = { it?.first?.packageName },
                                transitionSpec = {
                                    (fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)))
                                        .using(SizeTransform(clip = false) { _, _ -> tween(220, easing = FastOutSlowInEasing) })
                                },
                                label = "continue",
                            ) { section ->
                                if (section != null) {
                                    ContinueWatchingRow(section.first, section.second, onOpen = { vm.openWatchNext(context, it) })
                                } else {
                                    Spacer(Modifier.fillMaxWidth())
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        FavoritesBlock()
                        if (downUnder && account.showContinueWatching) {
                            // Tudo o que há para continuar, de todas as apps, o mais recente primeiro.
                            val all = remember(list, watchNext) {
                                list.flatMap { watchNext[it.packageName].orEmpty() }.sortedByDescending { it.lastEngagement }.take(DOWN_UNDER_MAX)
                            }
                            if (all.isNotEmpty()) {
                                Spacer(Modifier.height(14.dp))
                                DownUnderRow(all, list.associateBy { it.packageName }, onOpen = { vm.openWatchNext(context, it) })
                            }
                        }
                    }
                }
            }
        }

        menuFor?.let { app ->
            AppMenu(
                app = app,
                onMove = {
                    menuFor = null
                    moving = app.packageName
                },
                onChangeIcon = {
                    menuFor = null
                    iconFor = app
                },
                onRename = {
                    menuFor = null
                    renameFor = app
                },
                onHide = {
                    menuFor = null
                    // O foco passa para a vizinha antes de a app sair.
                    val pool = if (onApps) list else favorites
                    val i = pool.indexOfFirst { it.packageName == app.packageName }
                    focusApp((pool.getOrNull(i + 1) ?: pool.getOrNull(i - 1))?.packageName)
                    vm.setHidden(app.packageName, true)
                },
                onInfo = {
                    menuFor = null
                    SystemIntents.start(context, SystemIntents.appInfo(app.packageName))
                },
                onDismiss = { menuFor = null },
            )
        }

        renameFor?.let { app ->
            RenameDialog(
                app = app,
                custom = device.names[app.packageName] != null,
                onSave = {
                    renameFor = null
                    vm.setCustomName(app.packageName, it)
                },
                onDismiss = { renameFor = null },
            )
        }

        iconFor?.let { app ->
            IconPicker(app, current = device.icons[app.packageName], vm = vm, onDone = { iconFor = null })
        }
    }
}

/**
 * Separadores ao centro (Início, Apps, Definições: mudam ao passar o foco, como no tvOS), pesquisa e,
 * à direita, as horas com a saudação, a data e o tempo.
 */
@Composable
private fun TopBar(
    selected: Int,
    clock24h: Boolean,
    showGreeting: Boolean,
    weather: WeatherNow?,
    hasSearch: Boolean,
    onHome: () -> Unit,
    onApps: () -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    val s = LocalStrings.current
    val tabs = remember { List(3) { FocusRequester() } }
    Box(Modifier.fillMaxWidth().height(48.dp)) {
        // Cápsula translúcida, sem contorno; o separador aberto é uma cápsula mais clara lá dentro.
        // Ao subir para os separadores, o foco cai no separador aberto.
        Row(
            Modifier
                .align(Alignment.Center)
                .onFocusChanged { onFocusChanged(it.hasFocus) }
                .focusRestorer(tabs[selected])
                .background(Color.White.copy(alpha = 0.14f), CircleShape)
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            listOf(s.tabHome to onHome, s.tabApps to onApps, s.settings to onSettings).forEachIndexed { i, (label, action) ->
                Surface(
                    onClick = action,
                    // Passar o foco por um separador abre-o (sem carregar em OK).
                    modifier = Modifier
                        .focusRequester(tabs[i])
                        .onFocusChanged { if (it.isFocused && i != selected) action() },
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (i == selected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                        contentColor = if (i == selected) Color.White else Color.White.copy(alpha = 0.78f),
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black,
                        pressedContainerColor = Color.White.copy(alpha = 0.8f),
                        pressedContentColor = Color.Black,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                ) {
                    Text(
                        label,
                        Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium),
                    )
                }
            }
        }
        Row(
            Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (hasSearch) {
                Surface(
                    onClick = onSearch,
                    modifier = Modifier.size(40.dp),
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.14f),
                        contentColor = GoatColors.Text,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black,
                        pressedContainerColor = Color.White.copy(alpha = 0.8f),
                        pressedContentColor = Color.Black,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(GoatIcons.Search, contentDescription = s.search, modifier = Modifier.size(18.dp))
                    }
                }
            }
            TopInfo(clock24h, showGreeting, weather)
        }
    }
}

/**
 * O destaque do "Continuar a ver": o conteúdo mais recente do favorito com foco, em grande.
 * Recebe o foco (↑ a partir dos favoritos): ‹ › passa pelos outros conteúdos da app, OK continua.
 * [showLogo]: a imagem não enche o ecrã (ex.: logo de um canal), por isso aparece pequena aqui.
 */
@Composable
private fun ShelfHero(
    app: LauncherApp,
    item: WatchNextItem,
    count: Int,
    showLogo: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
) {
    val s = LocalStrings.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        Modifier
            .widthIn(max = 640.dp)
            .onPreviewKeyEvent { e ->
                when (e.key) {
                    Key.DirectionLeft -> {
                        if (e.type == KeyEventType.KeyDown) onPrev()
                        true
                    }
                    Key.DirectionRight -> {
                        if (e.type == KeyEventType.KeyDown) onNext()
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (e.type == KeyEventType.KeyUp) onOpen()
                        true
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction),
    ) {
        // Sem imagem de fundo (ex.: logo de um canal): o logo aparece num cartão, como na fila de cartões.
        if (showLogo && item.imageUri != null) {
            Box(
                Modifier
                    .padding(bottom = 16.dp)
                    .size(width = 176.dp, height = 99.dp)
                    .background(Color(0xFF1C2333), GoatShapes.Control)
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(model = item.imageUri, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
        Text(
            s.shelfLabel(app.label).uppercase(s.locale),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
            color = GoatColors.TextDim,
        )
        Spacer(Modifier.height(6.dp))
        Text(item.title, style = ShelfTitleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val details = listOfNotNull(
            s.seasonEpisode(item.season, item.episode).takeIf { it.isNotEmpty() },
            item.episodeTitle?.takeIf { it != item.title },
            item.remainingMinutes?.takeIf { it > 0 }?.let(s::minutesLeft),
        ).joinToString(" · ")
        if (details.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                details,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                color = GoatColors.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item.progress?.let { p ->
            Spacer(Modifier.height(12.dp))
            Box(Modifier.width(320.dp).height(5.dp).background(Color.White.copy(alpha = 0.3f), GoatShapes.Small)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(Color.White, GoatShapes.Small))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Parece um botão: com foco fica branco (o foco está no destaque inteiro).
            Row(
                Modifier
                    .background(if (focused) Color.White else Color.White.copy(alpha = 0.18f), GoatShapes.Control)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(GoatIcons.Play, contentDescription = null, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.continueNow, style = MaterialTheme.typography.labelLarge, color = if (focused) Color.Black else Color.White)
            }
            if (count > 1) Text(s.shelfMore(count - 1), style = MaterialTheme.typography.labelMedium, color = GoatColors.TextFaint)
        }
    }
}

/**
 * Como no tvOS, uma fila só anda quando o elemento com foco ia sair dela (com [margin] de folga para o
 * cartão aumentado). O comportamento por defeito na TV centra o foco.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberEdgeScroll(margin: Dp): BringIntoViewSpec {
    val px = with(LocalDensity.current) { margin.toPx() }
    return remember(px) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val before = offset - px
                val after = offset + size + px - containerSize
                return when {
                    before < 0 -> before
                    after > 0 -> after
                    else -> 0f
                }
            }
        }
    }
}

/** Alarga o elemento [horizontal] para cada lado, para lá das margens do pai (ex.: até às bordas do ecrã). */
private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** O estilo "Down Under": uma fila com o "Continuar a ver" de todas as apps, por baixo dos favoritos. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownUnderRow(items: List<WatchNextItem>, apps: Map<String, LauncherApp>, onOpen: (WatchNextItem) -> Unit) {
    val s = LocalStrings.current
    val first = remember { FocusRequester() }
    Column {
        Text(s.continueWatching, style = MaterialTheme.typography.titleSmall, color = GoatColors.TextDim, modifier = Modifier.padding(start = 4.dp))
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberEdgeScroll(SafeH + 8.dp)) {
            LazyRow(
                modifier = Modifier.bleed(SafeH).focusRestorer(first),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(start = SafeH + 4.dp, end = SafeH + 4.dp, top = 8.dp, bottom = 2.dp),
            ) {
                itemsIndexed(items, key = { _, item -> item.packageName + item.id }) { i, item ->
                    WideWatchCard(
                        item = item,
                        app = apps[item.packageName],
                        onClick = { onOpen(item) },
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
    }
}

/** O estilo "fila de cartões": o "Continuar a ver" do favorito com foco, por cima dos favoritos. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueWatchingRow(app: LauncherApp, items: List<WatchNextItem>, onOpen: (WatchNextItem) -> Unit) {
    val s = LocalStrings.current
    val first = remember { FocusRequester() }
    Column {
        Text(s.continueIn(app.label), style = MaterialTheme.typography.titleSmall, color = GoatColors.TextDim, modifier = Modifier.padding(start = 4.dp))
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberEdgeScroll(SafeH + 8.dp)) {
            LazyRow(
                // Ao subir dos favoritos, entra no último cartão visitado desta app, ou no primeiro.
                // A fila vai até às bordas do ecrã (os cartões passam pela margem em vez de serem cortados nela).
                modifier = Modifier.bleed(SafeH).focusRestorer(first),
                horizontalArrangement = RowSpacing,
                contentPadding = PaddingValues(start = SafeH + 4.dp, end = SafeH + 4.dp, top = 10.dp, bottom = 4.dp),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { i, item ->
                    WatchNextCard(
                        item = item,
                        onClick = { onOpen(item) },
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
    }
}

/** Nome da app no launcher (ex.: "YouTube" em vez de "TizenTube"). Guardar vazio repõe o nome da app. */
@Composable
private fun RenameDialog(app: LauncherApp, custom: Boolean, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val field = remember { FocusRequester() }
    var text by remember { mutableStateOf(app.label) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(460.dp)
                .glass(GoatShapes.Panel, tint = Color(0xF0111522))
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

/** Pressão longa num banner: Mover, Mudar ícone, Mudar nome, Esconder, Informação da app. */
@Composable
private fun AppMenu(
    app: LauncherApp,
    onMove: () -> Unit,
    onChangeIcon: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(380.dp)
                .glass(GoatShapes.Panel, tint = Color(0xF0111522))
                .padding(16.dp),
        ) {
            Text(app.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp, bottom = 10.dp))
            GlassRow(title = s.move, onClick = onMove, modifier = Modifier.focusRequester(first))
            GlassRow(title = s.changeIcon, onClick = onChangeIcon)
            GlassRow(title = s.rename, onClick = onRename)
            GlassRow(title = s.hide, onClick = onHide)
            GlassRow(title = s.appInfo, onClick = onInfo)
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            first.requestFocusSafely()
        }
    }
}
