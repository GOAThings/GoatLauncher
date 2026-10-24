package cc.slokker.goatlauncher.ui.home

import cc.slokker.goatlauncher.ui.theme.tracking
import cc.slokker.goatlauncher.ui.common.PinPad
import cc.slokker.goatlauncher.ui.PinPrompt
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
import androidx.compose.foundation.focusGroup
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
import cc.slokker.goatlauncher.data.NetworkKind
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import cc.slokker.goatlauncher.ui.common.TvTextField
import cc.slokker.goatlauncher.ui.common.GlassButton
import cc.slokker.goatlauncher.ui.settings.SettingsScreen
import cc.slokker.goatlauncher.data.WeatherNow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

/** Apps no Início (as primeiras da ordem guardada); as restantes estão no separador Apps. */
const val FavoriteCount = 5

/** Colunas da grelha do separador Apps (a primeira linha são os favoritos, alinhada com o Início). */
private const val AppsColumns = 5

/** Quanto tempo o foco tem de ficar num favorito para o "Continuar a ver" mudar para ele. */
private const val SECTION_SETTLE_MS = 150L

/** Quanto tempo fica a ajuda "Mantém OK para reordenar" no Início. */
private const val HINT_MS = 5_000L

/** Duração da troca de separador (o fade do conteúdo), com folga. */
private const val TAB_FADE_MS = 320L

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
 * Na disposição Apple TV (Definições → Aparência) não há separador Apps: o Início tem os cartões grandes do
 * favorito com foco, a doca dos favoritos e, a descer, as outras apps numa grelha (como no tvOS).
 */
@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val dockNight by vm.night.collectAsStateWithLifecycle()
    val s = LocalStrings.current
    val context = LocalContext.current
    val screen by vm.screen.collectAsStateWithLifecycle()
    val onApps = screen == Screen.Apps
    val onSettings = screen == Screen.Settings
    val weather by vm.weather.collectAsStateWithLifecycle()
    val network by vm.network.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val watchNext by vm.watchNext.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val list = apps.orEmpty()
    val tabApps by vm.appsTab.collectAsStateWithLifecycle()
    val currentList by rememberUpdatedState(list)
    val favorites = list.take(FavoriteCount)
    // No separador Apps: os favoritos e depois todas as outras, incluindo as escondidas do Início.
    val moreApps = tabApps.orEmpty().filter { app -> favorites.none { it.packageName == app.packageName } }
    val gridApps = favorites + moreApps
    val appleTv = account.layout == AccountSettings.LAYOUT_APPLE_TV
    val shelfStyle = !appleTv && account.continueStyle == AccountSettings.STYLE_SHELF
    // Apple TV: o Início desce da doca para as outras apps (as mesmas do separador Apps, também as escondidas).
    val homeScroll = rememberScrollState()
    val scope = rememberCoroutineScope()

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

    // O foco vai para a app [targetPkg] (o banner com o [target]) sempre que [focusRequest] muda.
    val target = remember { FocusRequester() }
    val favFirst = remember { FocusRequester() }
    var targetPkg by remember { mutableStateOf(vm.focusedPackage) }
    var focusRequest by remember { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()

    /** Posição de [pkg] na grelha das Apps (contando os dois títulos de secção). */
    fun gridIndex(pkg: String): Int {
        val i = gridApps.indexOfFirst { it.packageName == pkg }
        return if (i < favorites.size) 1 + i else 2 + i
    }

    LaunchedEffect(focusRequest, list.isNotEmpty()) {
        if (list.isEmpty()) return@LaunchedEffect
        val pool = when {
            onApps || appleTv -> gridApps
            else -> favorites
        }
        val pkg = targetPkg?.takeIf { p -> pool.any { it.packageName == p } } ?: pool.first().packageName
        targetPkg = pkg
        if (onApps) {
            val idx = gridIndex(pkg)
            if (gridState.layoutInfo.visibleItemsInfo.none { it.index == idx }) gridState.scrollToItem(idx)
        }
        // O banner só tem o FocusRequester depois de recompor (e de a troca de separador acabar): tenta uns frames.
        repeat(30) {
            withFrameNanos { }
            if (target.requestFocusSafely()) {
                // A troca de separador (fade) pode tirar-lhe o foco quando acaba: volta a pedi-lo depois dela.
                delay(TAB_FADE_MS)
                target.requestFocusSafely()
                return@LaunchedEffect
            }
        }
    }

    fun focusApp(pkg: String?) {
        targetPkg = pkg
        focusRequest++
    }

    // Ao fechar o menu de uma app, o foco volta ao banner dela (o menu está por cima, no mesmo ecrã).
    fun closeMenu() {
        menuFor?.let { focusApp(it.packageName) }
        menuFor = null
    }

    // Pedido do código (controlo parental). Ao fechar (certo ou cancelado), o foco volta à app onde estava,
    // a menos que o código tenha aberto o menu ou as Definições (esses tratam do seu foco).
    val pinPrompt by vm.pinPrompt.collectAsStateWithLifecycle()
    var hadPin by remember { mutableStateOf(false) }

    fun lastFavorite() = favorites.firstOrNull { it.packageName == focusedPkg }?.packageName ?: favorites.firstOrNull()?.packageName

    // Quando acabou o último "mover": o OK que confirma a posição não pode também abrir a app (o comando pode
    // mandar o clique por outra tecla, ou depois de o "mover" já ter acabado).
    var moveEndedAt by remember { mutableLongStateOf(0L) }

    fun endMove() {
        if (moving != null) {
            moving = null
            moveEndedAt = SystemClock.uptimeMillis()
            vm.finishMove()
        }
    }

    // Ao voltar ao Início vindo das Apps ou das Definições (por Voltar ou pelos separadores), o foco vai para o
    // último favorito, se não estiver nos separadores (senão perdia-se com o conteúdo que saiu).
    var previousScreen by remember { mutableStateOf(screen) }
    LaunchedEffect(screen) {
        if (screen == Screen.Home && previousScreen != Screen.Home && !tabBarFocused) focusApp(lastFavorite())
        previousScreen = screen
    }

    // A Apple TV não tem separador Apps: se estava aberto ao mudar de disposição, volta ao Início.
    LaunchedEffect(appleTv, onApps) {
        if (appleTv && onApps) vm.openHome()
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
        // (ícone, já se sabe?): com um ícone escolhido, até ele carregar fica só a cor da app, para não piscar
        // o banner original antes. Se não carregar (ex.: icon pack desinstalado), aparece o original.
        val custom by produceState(spec?.let { vm.icons.cached(it) } to (spec == null), spec) {
            value = spec?.let { vm.icons.load(it) } to true
        }
        AppTile(
            app = app,
            width = TileWidth,
            custom = custom.first,
            customPending = !custom.second,
            showLabel = account.showAppName,
            hasContinue = home && account.showContinueWatching && !watchNext[app.packageName].isNullOrEmpty(),
            moving = moving == app.packageName,
            onFocused = {
                focusedPkg = app.packageName
                vm.focusedPackage = app.packageName
            },
            onClick = {
                when {
                    moving != null -> endMove()
                    SystemClock.uptimeMillis() - moveEndedAt > MOVE_CLICK_GUARD_MS -> vm.launchApp(context, app.packageName)
                }
            },
            // Com as definições protegidas, o menu (mover, esconder, mudar nome…) pede o código.
            onLongClick = { vm.withSettingsUnlocked { menuFor = app } },
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

    /** A ajuda "Mantém OK para reordenar", que desaparece uns segundos depois (e volta a mover). */
    @Composable
    fun MoveHint(modifier: Modifier = Modifier) {
        var showHint by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            delay(HINT_MS)
            showHint = false
        }
        val hintAlpha by animateFloatAsState(if (showHint || moving != null) 1f else 0f, tween(600), label = "hint")
        Text(
            if (moving != null) s.moveHint else s.holdToRearrange,
            modifier.width(TilesRowWidth).graphicsLayer { alpha = hintAlpha },
            style = MaterialTheme.typography.labelSmall,
            color = GoatColors.TextFaint,
            textAlign = TextAlign.End,
        )
    }

    @Composable
    fun FavoritesBlock() {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Sem título: só a ajuda "Mantém OK para reordenar".
            MoveHint()
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

    // Modo ambiente: sem tocar no comando durante account.idleMinutes (com o launcher à frente), fica só o fundo
    // e as horas. Ao voltar de uma app, a contagem recomeça.
    var lastInput by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var idle by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lastInput, account.idleMinutes) {
        idle = false
        if (account.idleMinutes <= 0) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            idle = false
            delay(account.idleMinutes * 60_000L)
            idle = true
        }
    }
    val uiAlpha by animateFloatAsState(if (idle) 0f else 1f, tween(if (idle) AMBIENT_FADE_MS else 250), label = "ambient")
    val reddit by vm.redditImage.collectAsStateWithLifecycle()
    // "Não deixar a TV adormecer": só conta enquanto a janela do launcher está visível.
    val view = LocalView.current
    val keepAwake = account.idleMinutes > 0 && account.idleKeepAwake
    DisposableEffect(keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }

    // O efeito de foco dos cartões das apps (Definições → Aparência).
    val focusStyle = remember(account) { FocusStyle.of(account) }
    CompositionLocalProvider(
        LocalFocusStyle provides focusStyle,
        LocalIconBackground provides account.iconBackground,
        LocalFocusDim provides remember { FocusDim() },
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = uiAlpha }
                    .onPreviewKeyEvent { e ->
                        // Modo ambiente: qualquer tecla acorda o ecrã e não faz mais nada.
                        if (e.type == KeyEventType.KeyDown) lastInput = SystemClock.elapsedRealtime()
                        if (idle || uiAlpha < 1f) return@onPreviewKeyEvent true
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
                                        // Na Apple TV a doca e a grelha de baixo são uma só lista: anda-se por todas.
                                        val allowed = onApps || appleTv || (abs(delta) == 1 && idx + delta in 0 until minOf(FavoriteCount, list.size))
                                        if (allowed) vm.moveApp(pkg, delta, FavoriteCount)
                                        focusApp(pkg)
                                    }
                                    true
                                }
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA, Key.ButtonSelect, Key.Back -> {
                                    if (up) endMove()
                                    true
                                }
                                else -> false
                            }
                            pinPrompt != null && e.key == Key.Back -> {
                                if (up) vm.cancelPin()
                                true
                            }
                            menuFor != null && e.key == Key.Back -> {
                                if (up) closeMenu()
                                true
                            }
                            // As Definições tratam o seu Voltar (páginas); se o foco estiver nos separadores, ver onKeyEvent.
                            e.key == Key.Back && onSettings -> false
                            // É o ecrã inicial: Voltar não sai daqui. Nas Apps, volta ao Início; na grelha da Apple TV, à doca.
                            e.key == Key.Back -> {
                                if (up && onApps) {
                                    vm.openHome()
                                    focusApp(lastFavorite())
                                } else if (up && appleTv && homeScroll.value > 0) {
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
                // O fundo mostra o último conteúdo que enche o ecrã: ao sair (fade), fica nessa imagem, em vez de mostrar
                // por um instante, em ecrã inteiro, o logo do canal para onde se foi (o shelfPainter já mudou para ele).
                var backdropUrl by remember { mutableStateOf<String?>(null) }
                if (shelfFullBleed) backdropUrl = shelfItem?.imageUri
                AnimatedVisibility(visible = shelfFullBleed && !onApps, enter = fadeIn(tween(400)), exit = fadeOut(tween(300))) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(backdropUrl).crossfade(400).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
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
                        showApps = !appleTv,
                        selected = when {
                            onApps -> 1
                            onSettings -> if (appleTv) 1 else 2
                            else -> 0
                        },
                        clock24h = account.clock24h,
                        showGreeting = account.showGreeting,
                        weather = weather?.takeIf { account.showWeather },
                        network = network,
                        onHome = vm::openHome,
                        onApps = vm::openApps,
                        onSettings = vm::openSettings,
                        onNetwork = { SystemIntents.openNetworkSettings(context) },
                        night = vm.night.collectAsStateWithLifecycle().value,
                        onNight = vm::toggleNight,
                        onAndroidSettings = { SystemIntents.start(context, SystemIntents.androidSettings()) },
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
                                        if (moreApps.isNotEmpty()) {
                                            item(key = "h-more", span = { GridItemSpan(maxLineSpan) }) {
                                                SectionHeader(s.moreApps, hint = false, modifier = Modifier.padding(top = 8.dp))
                                            }
                                            items(moreApps, key = { it.packageName }) { app -> Tile(app, home = false) }
                                        }
                                    }
                                }
                            }
                        } else if (appleTv) {
                            // ---- Início (Apple TV): cartões grandes, a doca dos favoritos e, a descer, as outras apps ----
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val viewport = maxHeight
                                Column(Modifier.fillMaxSize().verticalScroll(homeScroll)) {
                                    // O primeiro ecrã: os cartões em cima e a doca em baixo; a grelha começa por baixo dele.
                                    Column(Modifier.fillMaxWidth().height(viewport), horizontalAlignment = Alignment.CenterHorizontally) {
                                        AnimatedContent(
                                            targetState = sectionApp?.takeIf { sectionItems.isNotEmpty() }?.let { it to sectionItems },
                                            contentKey = { it?.first?.packageName },
                                            transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            label = "topShelf",
                                        ) { section ->
                                            if (section != null) {
                                                // ↓ volta à app dona dos cartões (não à que estiver por baixo deles), como no tvOS.
                                                TopShelfRow(
                                                    section.second,
                                                    onOpen = { vm.openWatchNext(context, it) },
                                                    modifier = Modifier.onPreviewKeyEvent { e ->
                                                        if (e.key != Key.DirectionDown) return@onPreviewKeyEvent false
                                                        if (e.type == KeyEventType.KeyDown) focusApp(section.first.packageName)
                                                        true
                                                    },
                                                )
                                            } else {
                                                Spacer(Modifier.fillMaxSize())
                                            }
                                        }
                                        MoveHint(Modifier.padding(bottom = 8.dp))
                                        // A doca: só os 5 favoritos, numa barra de vidro à medida deles, com folga para o banner
                                        // aumentado das pontas (pode passar um pouco das margens). Ao voltar a ela, o ecrã sobe todo.
                                        Box(
                                            Modifier
                                                .requiredWidth(TilesRowWidth + DockPadding * 2)
                                                .background(dockColor(dockNight), DockShape)
                                                .padding(horizontal = DockPadding, vertical = 18.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            when {
                                                apps == null -> Spacer(Modifier.height(TileWidth * 9 / 16))
                                                list.isEmpty() -> Text(s.noApps, Modifier.padding(vertical = 20.dp), color = GoatColors.TextDim)
                                                else -> Row(
                                                    Modifier
                                                        .width(TilesRowWidth)
                                                        .focusRestorer(favFirst)
                                                        .onFocusChanged { if (it.hasFocus) scope.launch { homeScroll.animateScrollTo(0) } },
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
                                    if (moreApps.isNotEmpty()) {
                                        Column(
                                            Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 28.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(20.dp),
                                        ) {
                                            moreApps.chunked(AppsColumns).forEach { row ->
                                                Row(Modifier.width(TilesRowWidth), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                                                    row.forEach { app -> key(app.packageName) { Tile(app, home = false) } }
                                                }
                                            }
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
                            }
                        }
                    }
                }

                // O menu desenha-se por cima do ecrã, não numa janela à parte (Dialog): abre sem criar janela nova.
                // Guarda a última app para o fade de saída não ficar vazio.
                var menuShown by remember { mutableStateOf<LauncherApp?>(null) }
                if (menuFor != null) menuShown = menuFor
                AnimatedVisibility(visible = menuFor != null, enter = fadeIn(tween(MENU_MS)), exit = fadeOut(tween(MENU_MS))) {
                    val app = menuShown ?: return@AnimatedVisibility
                    val isFavorite = favorites.any { it.packageName == app.packageName }
                    // As apps do sistema não se desinstalam (o Android só ofereceria desinstalar as atualizações).
                    val systemApp = remember(app.packageName) {
                        runCatching {
                            context.packageManager.getApplicationInfo(app.packageName, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0
                        }.getOrDefault(true)
                    }
                    AppMenu(
                        app = app,
                        onMove = {
                            closeMenu()
                            moving = app.packageName
                        },
                        // Tirar só se houver quem entre no lugar (a primeira app de baixo).
                        favorite = if (isFavorite && gridApps.size <= FavoriteCount) null else isFavorite,
                        onToggleFavorite = {
                            closeMenu()
                            val from = gridApps.indexOfFirst { it.packageName == app.packageName }
                            if (isFavorite) {
                                // Desce para o início da lista de baixo e a primeira de lá sobe para o lugar vago.
                                // No Início Android a app sai da fila: o foco passa para a vizinha.
                                if (!onApps && !appleTv) {
                                    focusApp((favorites.getOrNull(from + 1) ?: favorites.getOrNull(from - 1))?.packageName)
                                }
                                vm.moveApp(app.packageName, FavoriteCount - from, FavoriteCount)
                                vm.finishMove()
                            } else {
                                // Entra no último lugar (a que lá estava desce para o início da lista de baixo) e fica
                                // logo a mover, para se pôr no sítio certo com ‹ ›.
                                vm.moveApp(app.packageName, minOf(favorites.size, FavoriteCount - 1) - from, FavoriteCount)
                                moving = app.packageName
                            }
                        },
                        onChangeIcon = {
                            closeMenu()
                            iconFor = app
                        },
                        onRename = {
                            closeMenu()
                            renameFor = app
                        },
                        hidden = app.packageName in device.hidden,
                        continueOff = app.packageName in device.noContinue,
                        onToggleContinue = {
                            closeMenu()
                            vm.setShowContinue(app.packageName, app.packageName in device.noContinue)
                        },
                        onHide = {
                            closeMenu()
                            if (app.packageName in device.hidden) {
                                vm.setHidden(app.packageName, false)
                            } else {
                                // No Início a app sai da fila: o foco passa para a vizinha. Nas Apps continua lá.
                                // Nas Apps (e na grelha da Apple TV) continua lá, com o foco.
                                if (!onApps && !appleTv) {
                                    val i = favorites.indexOfFirst { it.packageName == app.packageName }
                                    focusApp((favorites.getOrNull(i + 1) ?: favorites.getOrNull(i - 1))?.packageName)
                                }
                                vm.setHidden(app.packageName, true)
                            }
                        },
                        onInfo = {
                            closeMenu()
                            SystemIntents.start(context, SystemIntents.appInfo(app.packageName))
                        },
                        onUninstall = if (systemApp) null else {
                            {
                                closeMenu()
                                if (!SystemIntents.start(context, SystemIntents.uninstall(app.packageName))) vm.toast(s.cantOpen)
                            }
                        },
                        // Só com o controlo parental ligado (há código).
                        lock = if (device.pin != null) app.packageName in device.locked else null,
                        onToggleLock = {
                            closeMenu()
                            vm.setLocked(app.packageName, app.packageName !in device.locked)
                        },
                    )
                }

                var pinShown by remember { mutableStateOf<PinPrompt?>(null) }
                if (pinPrompt != null) pinShown = pinPrompt
                AnimatedVisibility(visible = pinPrompt != null, enter = fadeIn(tween(MENU_MS)), exit = fadeOut(tween(MENU_MS))) {
                    val prompt = pinShown ?: return@AnimatedVisibility
                    PinOverlay(prompt.title, onPin = vm::submitPin)
                }
                LaunchedEffect(pinPrompt) {
                    if (pinPrompt != null) {
                        hadPin = true
                        return@LaunchedEffect
                    }
                    if (!hadPin) return@LaunchedEffect
                    hadPin = false
                    withFrameNanos { }
                    if (menuFor == null && !onSettings) focusApp(focusedPkg ?: lastFavorite())
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
            AnimatedVisibility(visible = idle, enter = fadeIn(tween(AMBIENT_FADE_MS)), exit = fadeOut(tween(250))) {
                AmbientScene(
                    style = account.ambientStyle,
                    clock24h = account.clock24h,
                    weather = weather?.takeIf { account.showWeather },
                    nowPlaying = nowPlaying(watchNext),
                    caption = reddit?.title?.let(::cleanRedditTitle),
                )
            }
        }
    }
}

/**
 * À esquerda a saudação, a data e o tempo; ao centro os separadores (Início, Apps, Definições: mudam ao passar
 * o foco, como no tvOS); à direita o estado da rede e as horas.
 */
@Composable
private fun TopBar(
    /** Com o separador Apps (disposição Android); [selected] conta só os separadores que se veem. */
    showApps: Boolean,
    selected: Int,
    clock24h: Boolean,
    showGreeting: Boolean,
    weather: WeatherNow?,
    network: NetworkKind,
    onHome: () -> Unit,
    onApps: () -> Unit,
    onSettings: () -> Unit,
    onNetwork: () -> Unit,
    night: Boolean,
    onNight: () -> Unit,
    onAndroidSettings: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    val s = LocalStrings.current
    val entries = listOfNotNull(s.tabHome to onHome, (s.tabApps to onApps).takeIf { showApps }, s.settings to onSettings)
    val tabs = remember(entries.size) { List(entries.size) { FocusRequester() } }
    // Só se troca de separador ao andar com ← → nos separadores. Quando o sistema põe lá o foco sozinho
    // (ex.: o elemento com foco desapareceu), não se abre nada.
    var arrowed by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(48.dp)) {
        TopGreeting(showGreeting, weather, Modifier.align(Alignment.CenterStart))
        // Cápsula translúcida, sem contorno; o separador aberto é uma cápsula mais clara lá dentro.
        // Ao subir para os separadores, o foco cai no separador aberto.
        Row(
            Modifier
                .align(Alignment.Center)
                .onFocusChanged { onFocusChanged(it.hasFocus) }
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && (e.key == Key.DirectionLeft || e.key == Key.DirectionRight)) arrowed = true
                    false
                }
                .focusRestorer(tabs[selected.coerceIn(tabs.indices)])
                .background(Color.White.copy(alpha = 0.14f), CircleShape)
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            entries.forEachIndexed { i, (label, action) ->
                Surface(
                    onClick = action,
                    // Passar o foco por um separador abre-o (sem carregar em OK).
                    modifier = Modifier
                        .focusRequester(tabs[i])
                        .onFocusChanged {
                            if (it.isFocused && arrowed && i != selected) action()
                            if (it.isFocused) arrowed = false
                        },
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
            // As definições do Android (som, imagem, Bluetooth…), sem passar pelas do launcher.
            RoundButton(onClick = onAndroidSettings) {
                Icon(GoatIcons.Settings, contentDescription = s.androidSettings, modifier = Modifier.size(18.dp))
            }
            // Dia/noite: OK troca entre fundo claro e fundo escurecido (e fotos do tom certo).
            RoundButton(onClick = onNight) {
                Icon(if (night) GoatIcons.Moon else GoatIcons.Sun, contentDescription = s.dayNight, modifier = Modifier.size(18.dp))
            }
            // Estado da rede (Wi-Fi, cabo ou sem rede); OK abre as definições de rede.
            RoundButton(onClick = onNetwork) {
                Icon(
                    when (network) {
                        NetworkKind.ETHERNET -> GoatIcons.Ethernet
                        NetworkKind.WIFI -> GoatIcons.Wifi
                        NetworkKind.NONE -> GoatIcons.WifiOff
                    },
                    contentDescription = s.network,
                    modifier = Modifier.size(18.dp),
                )
            }
            TopClock(clock24h)
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
            (if (item.type == WatchNextItem.TYPE_RECOMMENDED) s.shelfRecommended(app.label) else s.shelfLabel(app.label)).uppercase(s.locale),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = tracking(2f)),
            color = GoatColors.TextDim,
        )
        Spacer(Modifier.height(6.dp))
        Text(item.title, style = ShelfTitleStyle.copy(letterSpacing = tracking(-1.2f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/**
 * Disposição Apple TV: o "Continuar a ver" do favorito com foco em cartões grandes, por cima da doca.
 * Os cartões ocupam a altura que sobra (até [TopShelfCardMax]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopShelfRow(items: List<WatchNextItem>, onOpen: (WatchNextItem) -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val first = remember { FocusRequester() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // O título da secção, as margens da fila (para o cartão aumentado) e o título do cartão com foco.
        val cardHeight = (maxHeight - 74.dp).coerceIn(100.dp, TopShelfCardMax)
        Column {
            Text(
                s.continueWatching,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = GoatColors.TextDim,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberEdgeScroll(SafeH + 8.dp)) {
                LazyRow(
                    // Ao descer de cima ou subir da doca, entra no último cartão visitado, ou no primeiro.
                    modifier = Modifier.bleed(SafeH).focusRestorer(first),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    contentPadding = PaddingValues(start = SafeH + 4.dp, end = SafeH + 4.dp, top = 12.dp, bottom = 4.dp),
                ) {
                    itemsIndexed(items, key = { _, item -> item.id }) { i, item ->
                        TopShelfCard(
                            item = item,
                            height = cardHeight,
                            onClick = { onOpen(item) },
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

private val TopShelfCardMax = 230.dp
private val DockShape = RoundedCornerShape(30.dp)

/** Depois de acabar de mover, um clique na app durante este tempo não a abre (é o OK que confirmou a posição). */
private const val MOVE_CLICK_GUARD_MS = 600L

/** O vidro da doca (Apple TV): escuro no modo noite, claro no modo dia, como o resto do ecrã. */
fun dockColor(night: Boolean): Color = if (night) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.26f)

/** Folga entre os favoritos das pontas e a borda da doca (o banner com foco cresce 10%). */
private val DockPadding = 30.dp

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

/**
 * Pressão longa num banner: Mover, Pôr/Tirar dos favoritos, Mudar ícone, Mudar nome, Esconder, Informação da app,
 * Desinstalar.
 */
@Composable
private fun AppMenu(
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
                .width(380.dp)
                .glass(GoatShapes.Panel, tint = Color(0xF0111522))
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

private const val MENU_MS = 120

private const val AMBIENT_FADE_MS = 1500

/** "Lago ao amanhecer [OC] [4032x3024]" → "Lago ao amanhecer". */
private fun cleanRedditTitle(title: String): String =
    title.replace(Regex("""[\[(][^\])]*[\])]"""), "").replace(Regex("""\s+"""), " ").trim()

/** O teclado do código por cima do ecrã (Voltar cancela: ver o ecrã). [onPin] devolve false se estiver errado. */
@Composable
private fun PinOverlay(title: String, onPin: (String) -> Boolean) {
    val s = LocalStrings.current
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(440.dp)
                .glass(GoatShapes.Panel, tint = Color(0xF0111522))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A cada tentativa errada o teclado recomeça (e volta a mostrar o erro, mesmo que seja o mesmo texto).
            key(attempt) {
                PinPad(
                    title = title,
                    message = error,
                    onDone = { pin ->
                        if (!onPin(pin)) {
                            error = s.wrongPin
                            attempt++
                        }
                    },
                )
            }
            Text(s.forgotPin, style = MaterialTheme.typography.bodySmall, color = GoatColors.TextFaint, textAlign = TextAlign.Center)
        }
    }
}

/** Botão redondo da barra de cima (vidro; branco com foco), como o da rede. */
@Composable
private fun RoundButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}
