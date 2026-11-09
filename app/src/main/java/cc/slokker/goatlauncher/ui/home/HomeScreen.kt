package cc.slokker.goatlauncher.ui.home

import cc.slokker.goatlauncher.ui.home.HomeMetrics.TileWidth
import cc.slokker.goatlauncher.ui.home.HomeMetrics.TileGap
import cc.slokker.goatlauncher.ui.home.HomeMetrics.TilesRowWidth
import cc.slokker.goatlauncher.ui.home.HomeMetrics.ColumnTileWidth
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import cc.slokker.goatlauncher.ui.settings.SettingsScreen
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.data.LauncherApp
import cc.slokker.goatlauncher.data.SystemIntents
import cc.slokker.goatlauncher.ui.LauncherViewModel
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.Screen
import cc.slokker.goatlauncher.ui.common.requestFocusSafely
import cc.slokker.goatlauncher.ui.theme.GoatColors
import cc.slokker.goatlauncher.ui.theme.TvSafePadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.rememberScrollState
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
    // Como o fundo (LauncherRoot), a cor da barra de baixo passa de dia para noite em 600 ms.
    val dockTint by animateColorAsState(dockColor(dockNight), tween(600), label = "dockNight")
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
    val style = HomeStyle.of(account)
    val appleTv = style == HomeStyle.AppleTv
    val columnLayout = style == HomeStyle.SideColumn
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
    val shelfPos = shelfIndex.coerceIn(0, (sectionItems.size - 1).coerceAtLeast(0))
    // O conteúdo que enche o fundo: o do destaque, ou (apps ao lado) o primeiro da app com foco.
    val shelfItem = sectionItems.getOrNull(shelfPos)?.takeIf { style.heroBackdrop }

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
            // O pedido do código fica com o foco, e devolve-o quando fechar.
            if (vm.pinPrompt.value != null) return@LaunchedEffect
            if (target.requestFocusSafely()) {
                // A troca de separador (fade) pode tirar-lhe o foco quando acaba: volta a pedi-lo depois dela.
                delay(TAB_FADE_MS)
                if (vm.pinPrompt.value == null) target.requestFocusSafely()
                return@LaunchedEffect
            }
        }
    }

    fun focusApp(pkg: String?) {
        targetPkg = pkg
        focusRequest++
    }

    // O que as teclas fazem no "Continuar a ver" (abrir, ‹ ›, o cartão com foco, ↓ dos cartões da Apple TV).
    val sectionActions = SectionActions(
        open = { vm.openWatchNext(context, it) },
        prev = { if (shelfIndex > 0) shelfIndex-- },
        next = { if (shelfIndex < sectionItems.size - 1) shelfIndex++ },
        focusItem = { shelfIndex = it },
        down = { focusApp(it.packageName) },
    )

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
    fun Tile(app: LauncherApp, home: Boolean, modifier: Modifier = Modifier, width: Dp = TileWidth, label: Boolean = account.showAppName) {
        val spec = device.icons[app.packageName]
        // (ícone, já se sabe?): com um ícone escolhido, até ele carregar fica só a cor da app, para não piscar
        // o banner original antes. Se não carregar (ex.: icon pack desinstalado), aparece o original.
        val custom by produceState(spec?.let { vm.icons.cached(it) } to (spec == null), spec) {
            value = spec?.let { vm.icons.load(it) } to true
        }
        AppTile(
            app = app,
            width = width,
            custom = custom.first,
            customPending = !custom.second,
            showLabel = label,
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
        MoveHintText(if (moving != null) s.moveHint else s.holdToRearrange, alpha = { hintAlpha }, modifier)
    }

    /** Os favoritos (na fila, na coluna ou na doca): ao descer dos cartões ou dos separadores, volta ao último com foco. */
    @Composable
    fun FavoriteTiles(width: Dp = TileWidth, label: Boolean = account.showAppName) {
        favorites.forEachIndexed { i, app ->
            key(app.packageName) {
                Tile(app, home = true, modifier = if (i == 0) Modifier.focusRequester(favFirst) else Modifier, width = width, label = label)
            }
        }
    }

    // Fundo do destaque: a imagem do conteúdo, se tiver a forma do ecrã (ver rememberHeroBackdrop).
    val backdrop = rememberHeroBackdrop(shelfItem)
    val shelfFullBleed = backdrop.fullBleed

    // Modo ambiente: sem tocar no comando durante account.idleMinutes (com o launcher à frente), fica só o fundo
    // e as horas. Ao voltar de uma app, a contagem recomeça.
    // A hora da última tecla não é estado do Compose: escrevê-la a cada tecla recompunha o ecrã inteiro (na SHIELD,
    // 50–190 ms por tecla). O temporizador lê-a quando acorda; só "idle" muda o ecrã.
    val lastInput = remember { longArrayOf(SystemClock.elapsedRealtime()) }
    var idle by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Mudar o nome e mudar o ícone são janelas à parte (Dialog): as teclas delas não passam pelo onPreviewKeyEvent de
    // baixo. Enquanto uma está aberta não se conta o tempo, senão o ecrã inicial apagava-se por trás dela; ao fechar,
    // a contagem recomeça.
    val dialogOpen = renameFor != null || iconFor != null
    LaunchedEffect(account.idleMinutes, dialogOpen) {
        idle = false
        if (account.idleMinutes <= 0 || dialogOpen) return@LaunchedEffect
        val idleMs = account.idleMinutes * 60_000L
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            lastInput[0] = SystemClock.elapsedRealtime()
            idle = false
            while (true) {
                val left = idleMs - (SystemClock.elapsedRealtime() - lastInput[0])
                if (left > 0) {
                    delay(left)
                } else {
                    idle = true
                    // Fica assim até uma tecla o acordar (ver onPreviewKeyEvent).
                    snapshotFlow { idle }.first { !it }
                }
            }
        }
    }
    val uiAlpha by animateFloatAsState(if (idle) 0f else 1f, tween(if (idle) AMBIENT_FADE_MS else 250), label = "ambient")
    val photo by vm.onlinePhoto.collectAsStateWithLifecycle()
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
        LocalClock24h provides account.clock24h,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = uiAlpha }
                    .onPreviewKeyEvent { e ->
                        // Modo ambiente: qualquer tecla acorda o ecrã e não faz mais nada.
                        if (e.type == KeyEventType.KeyDown) {
                            lastInput[0] = SystemClock.elapsedRealtime()
                            if (idle) idle = false
                        }
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
                HeroBackdropImage(backdrop, visible = shelfFullBleed && !onApps)
                // Sempre que há destaque (com a imagem do conteúdo ou sobre o fundo normal), para o texto grande se ler.
                val sideScrim by animateFloatAsState(if (shelfItem != null && !onApps) 1f else 0f, tween(400), label = "side")
                // Apps ao lado: o texto fica por cima da imagem toda (não só à esquerda), por isso escurece-se mais.
                val columnDim by animateFloatAsState(if (columnLayout && shelfFullBleed && !onApps) 1f else 0f, tween(400), label = "columnDim")
                val appsDim by animateFloatAsState(if (onApps || onSettings) 1f else 0f, tween(250), label = "dim")
                HomeScrims(side = { sideScrim }, columnDim = { columnDim }, appsDim = { appsDim })

                Column(Modifier.fillMaxSize().padding(TvSafePadding)) {
                    TopBar(
                        showApps = style.hasAppsTab,
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
                        } else {
                            // ---- Início, no estilo escolhido (o mesmo corpo que a pré-visualização da Aparência) ----
                            HomeBody(
                                style = style,
                                section = { modifier ->
                                    ContinueSection(style, sectionApp, sectionItems, shelfPos, showLogo = !shelfFullBleed, actions = sectionActions, modifier = modifier)
                                },
                                favorites = {
                                    when {
                                        // Apple TV: a doca. Ao voltar a ela, o ecrã sobe todo.
                                        appleTv -> Dock(dockTint) {
                                            when {
                                                apps == null -> Spacer(Modifier.height(TileWidth * 9 / 16))
                                                list.isEmpty() -> Text(s.noApps, Modifier.padding(vertical = 20.dp), color = GoatColors.TextDim)
                                                else -> FavoritesRow(
                                                    Modifier
                                                        .focusRestorer(favFirst)
                                                        .onFocusChanged { if (it.hasFocus) scope.launch { homeScroll.animateScrollTo(0) } },
                                                ) { FavoriteTiles() }
                                            }
                                        }
                                        columnLayout -> if (list.isNotEmpty()) {
                                            FavoritesColumn(Modifier.focusRestorer(favFirst)) { FavoriteTiles(width = ColumnTileWidth, label = false) }
                                        }
                                        apps == null -> Spacer(Modifier.height(TileWidth * 9 / 16 + 30.dp))
                                        list.isEmpty() -> Text(s.noApps, Modifier.padding(vertical = 30.dp), color = GoatColors.TextDim)
                                        else -> FavoritesRow(Modifier.focusRestorer(favFirst)) { FavoriteTiles() }
                                    }
                                },
                                moveHint = { modifier -> MoveHint(modifier) },
                                scroll = homeScroll,
                                below = {
                                    // Apple TV: a descer, as outras apps (as mesmas do separador Apps, também as escondidas).
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
                                },
                            )
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
                                // No Início a app sai da fila: o foco passa para a vizinha. Nas Apps (e na grelha da
                                // Apple TV) continua lá, com o foco.
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
                            val lock = app.packageName !in device.locked
                            vm.withParentalUnlocked { vm.setLocked(app.packageName, lock) }
                        },
                    )
                }

                var pinShown by remember { mutableStateOf<PinPrompt?>(null) }
                if (pinPrompt != null) pinShown = pinPrompt
                AnimatedVisibility(visible = pinPrompt != null, enter = fadeIn(tween(MENU_MS)), exit = fadeOut(tween(MENU_MS))) {
                    val prompt = pinShown ?: return@AnimatedVisibility
                    PinOverlay(prompt.title, waitMs = vm::pinWaitMs, onPin = vm::submitPin)
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
                    caption = photo?.title?.ifBlank { null },
                    credit = photo?.credit,
                )
            }
        }
    }
}

/** Depois de acabar de mover, um clique na app durante este tempo não a abre (é o OK que confirmou a posição). */
private const val MOVE_CLICK_GUARD_MS = 600L

/** O vidro da doca (Apple TV): escuro no modo noite, claro no modo dia, como o resto do ecrã. */
fun dockColor(night: Boolean): Color = if (night) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.26f)

private const val MENU_MS = 120

private const val AMBIENT_FADE_MS = 1500
