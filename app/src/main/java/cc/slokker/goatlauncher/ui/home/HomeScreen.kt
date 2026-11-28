package cc.slokker.goatlauncher.ui.home

import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import cc.slokker.goatlauncher.ui.common.OkKeys
import cc.slokker.goatlauncher.ui.common.focusSoon
import cc.slokker.goatlauncher.ui.home.HomeMetrics.TileWidth
import cc.slokker.goatlauncher.ui.home.HomeMetrics.TileGap
import cc.slokker.goatlauncher.ui.home.HomeMetrics.TilesRowWidth
import cc.slokker.goatlauncher.ui.home.HomeMetrics.ColumnTileWidth
import cc.slokker.goatlauncher.ui.PinPrompt
import cc.slokker.goatlauncher.data.AppGrid
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.State
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.layout.layout
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

/** Apps no Início (as primeiras da ordem guardada); as restantes estão no separador Apps. A regra é a do [AppGrid]. */
const val FavoriteCount = AppGrid.FAVORITES

/**
 * Quanto tempo o foco tem de ficar num favorito para o "Continuar a ver" mudar para ele: depois de o cartão acabar
 * de crescer e de acender (FocusSpring, ~0,6 s). O destaque novo constrói-se num frame comprido (na SHIELD 30–80 ms),
 * que a meio dessas animações dava um salto. Na Apple TV o de cima também só muda depois de o foco pousar (~0,9 s).
 */
private const val SECTION_SETTLE_MS = 600L

/** Quanto tempo fica a ajuda "Mantém OK para reordenar" no Início. */
private const val HINT_MS = 5_000L

/** Duração da troca de separador (o frame que constrói o separador novo e o fade do TabCrossfade), com folga. */
private const val TAB_FADE_MS = 450L

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
    // As listas só se refazem quando as apps mudam. Feitas de novo a cada composição eram "outras" (o Compose compara
    // as listas pela instância): mudar o destaque voltava a compor todos os cartões da fila, da doca e da grelha de
    // baixo (na disposição Apple TV, a meio do deslize para as outras apps, e na SHIELD isso dava um salto).
    val favorites = remember(list) { list.take(FavoriteCount) }
    // No separador Apps: os favoritos e depois todas as outras, incluindo as escondidas do Início.
    val moreApps = remember(tabApps, favorites) { tabApps.orEmpty().filter { app -> favorites.none { it.packageName == app.packageName } } }
    val gridApps = remember(favorites, moreApps) { favorites + moreApps }
    val style = HomeStyle.of(account)
    val appleTv = style == HomeStyle.AppleTv
    val columnLayout = style == HomeStyle.SideColumn
    // Apple TV: o Início desce da doca para as outras apps (as mesmas do separador Apps, também as escondidas).
    val homeScroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    // Onde está o banner de cada app no ecrã (o menu da app abre junto dele). Não é estado: só se lê ao abrir.
    val tileBounds = remember { HashMap<String, androidx.compose.ui.geometry.Rect>() }
    // O fundo desfocado para o vidro do menu da app.
    val menuWallpaper by vm.wallpaperImage.collectAsStateWithLifecycle()
    val wallpaperBlur = rememberWallpaperBlur(account.wallpaper, menuWallpaper)
    // A app com foco, para onde o foco vai, o "mover" e o que está por cima (menu, código…): ver HomeState.
    val state = remember { HomeState(vm.focusedPackage, pinShown = { vm.pinPrompt.value != null }) }
    // O ecrã de agora (banners incluídos) desfocado, tirado ao abrir o menu; sem ele, só o fundo.
    var screenBlur by remember { mutableStateOf<ImageBitmap?>(null) }
    val menuView = LocalView.current
    val menuOpen = state.menu != null
    LaunchedEffect(menuOpen) { if (menuOpen) screenBlur = captureScreenBlur(menuView) }
    val menuBackdrop = screenBlur ?: wallpaperBlur
    // O "Continuar a ver" só muda quando o foco assenta num favorito: a correr pela fila não carrega cada um.
    // O foco é lido num snapshotFlow (não na composição): mudar de app não volta a compor o ecrã todo.
    // Só os favoritos têm "Continuar a ver": numa das outras apps (a grelha por baixo da doca na Apple TV, o separador
    // Apps) fica o do último favorito. Antes passava a "nenhum", e na Apple TV isso reconstruía o ecrã a meio do deslize
    // para as outras apps (na SHIELD, um frame comprido que fazia a grelha saltar), para nada: os cartões já lá não se veem.
    var sectionPkg by remember { mutableStateOf(vm.focusedPackage) }
    val currentFavorites by rememberUpdatedState(favorites)
    LaunchedEffect(Unit) {
        snapshotFlow { state.focusedPkg }.collectLatest { pkg ->
            if (pkg != null && currentFavorites.none { it.packageName == pkg }) return@collectLatest
            if (sectionPkg != null) delay(SECTION_SETTLE_MS)
            sectionPkg = pkg
        }
    }
    // A app do destaque sem contar com o separador aberto: o fundo e os véus seguem o separador que está a aparecer
    // (fadeScreen), e não mudam no frame em que o separador novo se constrói (ver TabCrossfade).
    val heroApp = favorites.firstOrNull { it.packageName == sectionPkg }?.takeIf { account.showContinueWatching }
    val heroItems = heroApp?.let { watchNext[it.packageName] }.orEmpty()
    val sectionApp = heroApp?.takeIf { !onApps }
    val sectionItems = if (sectionApp != null) heroItems else emptyList()
    var shelfIndex by remember(sectionPkg) { mutableIntStateOf(0) }
    val shelfPos = shelfIndex.coerceIn(0, (heroItems.size - 1).coerceAtLeast(0))
    // O conteúdo que enche o fundo: o do destaque, ou (apps ao lado) o primeiro da app com foco.
    val shelfItem = heroItems.getOrNull(shelfPos)?.takeIf { style.heroBackdrop }
    // O separador cujo fade já começou. Lê-se só no TabBackdrop: mudá-lo não volta a compor o ecrã todo.
    val fadeScreen = remember { mutableStateOf(screen) }

    var tabBarFocused by remember { mutableStateOf(false) }
    val moving = state.moving

    // O foco vai para a app state.targetPkg (o banner com o [target]) sempre que state.focusRequest muda.
    val target = remember { FocusRequester() }
    val favFirst = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    /** Posição de [pkg] na grelha das Apps (contando os dois títulos de secção). */
    fun gridIndex(pkg: String): Int {
        val i = gridApps.indexOfFirst { it.packageName == pkg }
        return if (i < favorites.size) 1 + i else 2 + i
    }

    LaunchedEffect(state.focusRequest, list.isNotEmpty()) {
        if (list.isEmpty()) return@LaunchedEffect
        val pool = when {
            onApps || appleTv -> gridApps
            else -> favorites
        }
        val pkg = state.targetPkg?.takeIf { p -> pool.any { it.packageName == p } } ?: pool.first().packageName
        state.targetPkg = pkg
        if (onApps) {
            val idx = gridIndex(pkg)
            if (gridState.layoutInfo.visibleItemsInfo.none { it.index == idx }) gridState.scrollToItem(idx)
        }
        // O banner só tem o FocusRequester depois de recompor (e de a troca de separador acabar). O código, se abrir,
        // fica com o foco, e devolve-o quando fechar (HomeState.close).
        if (target.focusSoon(stop = { state.holdsFocus })) {
            // A troca de separador (fade) pode tirar-lhe o foco quando acaba: volta a pedi-lo depois dela.
            delay(TAB_FADE_MS)
            if (!state.holdsFocus) target.requestFocusSafely()
        }
    }

    fun focusApp(pkg: String?) = state.focusApp(pkg)

    // O que as teclas fazem no "Continuar a ver" (abrir, ‹ ›, o cartão com foco, ↓ dos cartões da Apple TV).
    val sectionActions = SectionActions(
        open = { vm.openWatchNext(context, it) },
        prev = { if (shelfIndex > 0) shelfIndex-- },
        next = { if (shelfIndex < sectionItems.size - 1) shelfIndex++ },
        focusItem = { shelfIndex = it },
        down = { focusApp(it.packageName) },
    )

    // Ao fechar o menu de uma app, o foco volta ao banner dela (o menu está por cima, no mesmo ecrã).
    fun closeMenu() = state.closeMenu()

    // Pedido do código (controlo parental): está por cima de tudo enquanto aberto (ver o LaunchedEffect lá em baixo).
    val pinPrompt by vm.pinPrompt.collectAsStateWithLifecycle()

    fun lastFavorite() = favorites.firstOrNull { it.packageName == state.focusedPkg }?.packageName ?: favorites.firstOrNull()?.packageName

    // O OK que confirma a posição não pode também abrir a app (ver HomeState.clickOpensApp).
    fun endMove() {
        if (state.endMove(SystemClock.uptimeMillis())) vm.finishMove()
    }

    // Ao voltar ao Início vindo das Apps ou das Definições (por Voltar ou pelos separadores), o foco vai para o
    // último favorito, se não estiver nos separadores (senão perdia-se com o conteúdo que saiu).
    var previousScreen by remember { mutableStateOf(screen) }
    // O separador de onde se abriram as Definições (para onde Voltar regressa).
    var settingsFrom by remember { mutableStateOf<Screen>(Screen.Home) }
    LaunchedEffect(screen) {
        if (screen == Screen.Settings && previousScreen != Screen.Settings) settingsFrom = previousScreen
        if (screen == Screen.Home && previousScreen != Screen.Home && !tabBarFocused) focusApp(lastFavorite())
        // → e logo ↓ nos separadores: as Apps abrem já com o foco fora da barra (no Início que está a sair); sem isto
        // o foco perdia-se com ele e voltava aos separadores, e era preciso outro ↓ para chegar à grelha.
        if (screen == Screen.Apps && previousScreen != Screen.Apps && !tabBarFocused) focusApp(null)
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
            state.dismissForHome()
            focusApp(currentList.firstOrNull()?.packageName)
        }
    }

    @Composable
    fun Tile(app: LauncherApp, home: Boolean, modifier: Modifier = Modifier, width: Dp = TileWidth, label: Boolean = account.showAppName) {
        val slotActive = LocalFadeSlotActive.current
        val spec = device.icons[app.packageName]
        // (ícone, já se sabe?): com um ícone escolhido, até ele carregar fica só a cor da app, para não piscar
        // o banner original antes. Se não carregar (ex.: icon pack desinstalado), aparece o original.
        // [app] também: depois de uma atualização da app, o "ícone da app" (CustomIcons.APP) volta a ler-se.
        val custom by produceState(spec?.let { vm.icons.cached(it) } to (spec == null), spec, app) {
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
                state.focusedPkg = app.packageName
                vm.focusedPackage = app.packageName
            },
            onClick = {
                when {
                    state.moving != null -> endMove()
                    state.clickOpensApp(SystemClock.uptimeMillis()) -> vm.launchApp(context, app.packageName)
                }
            },
            // Com as definições protegidas, o menu (mover, esconder, mudar nome…) pede o código.
            onLongClick = { vm.withSettingsUnlocked { state.open(Overlay.Menu(app)) } },
            bannerModifier = modifier
                .then(if (app.packageName == state.targetPkg) Modifier.focusRequester(target) else Modifier)
                // Onde o banner está, para o menu da app abrir junto dele (sem recompor: só se lê ao abrir).
                // Só o separador à vista (os outros ficam construídos, invisíveis): o menu abre junto ao banner certo.
                .onGloballyPositioned { if (slotActive) tileBounds[app.packageName] = it.boundsInWindow() },
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

    // Fundo do destaque: a imagem do conteúdo, se tiver a forma do ecrã (ver rememberHeroBackdrop). Lê-se só no fundo e no
    // destaque (ver rememberHeroBackdropState): quando a imagem carrega, o ecrã todo não volta a compor.
    val backdrop = rememberHeroBackdropState(shelfItem)

    // Modo ambiente: sem tocar no comando durante account.idleMinutes (com o launcher à frente), fica só o fundo
    // e as horas. Ao voltar de uma app, a contagem recomeça.
    // A hora da última tecla não é estado do Compose: escrevê-la a cada tecla recompunha o ecrã inteiro (na SHIELD,
    // 50–190 ms por tecla). O temporizador lê-a quando acorda; só "idle" muda o ecrã.
    val lastInput = remember { longArrayOf(SystemClock.elapsedRealtime()) }
    val tabPrebuild = remember(style.hasAppsTab) { listOfNotNull(Screen.Apps.takeIf { style.hasAppsTab }, Screen.Settings) }
    var idle by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Mudar o nome e mudar o ícone são janelas à parte (Dialog): as teclas delas não passam pelo onPreviewKeyEvent de
    // baixo. Enquanto uma está aberta não se conta o tempo, senão o ecrã inicial apagava-se por trás dela; ao fechar,
    // a contagem recomeça.
    val dialogOpen = state.dialogOpen
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
                        val pkg = state.moving
                        val action = homeKey(
                            homeKeyOf(e.key, moving = pkg != null),
                            down = e.type == KeyEventType.KeyDown,
                            up = e.type == KeyEventType.KeyUp,
                            HomeKeyContext(
                                moving = pkg != null,
                                pinOpen = pinPrompt != null,
                                menuOpen = state.menu != null,
                                onApps = onApps,
                                onSettings = onSettings,
                                appleTv = appleTv,
                                scrolled = homeScroll.value > 0,
                                movingIndex = list.indexOfFirst { it.packageName == pkg },
                                appCount = list.size,
                            ),
                        )
                        when (action) {
                            is HomeAction.Move -> {
                                if (pkg != null && action.allowed) vm.moveApp(pkg, action.delta)
                                focusApp(pkg)
                            }
                            HomeAction.EndMove -> endMove()
                            HomeAction.CancelPin -> vm.cancelPin()
                            HomeAction.CloseMenu -> closeMenu()
                            HomeAction.BackToHome -> {
                                vm.openHome()
                                focusApp(lastFavorite())
                            }
                            HomeAction.BackToDock -> focusApp(lastFavorite())
                            HomeAction.Pass, HomeAction.Swallow -> Unit
                        }
                        action.consumed
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
                TabBackdrop(backdrop, hero = shelfItem != null, columnLayout = columnLayout, tab = fadeScreen)

                Column(Modifier.fillMaxSize().padding(TvSafePadding)) {
                    // Nas Definições (página inteira) a barra de cima esconde-se, ao ritmo do fade da página, e o foco
                    // não entra nela.
                    val barAlpha by animateFloatAsState(
                        if (fadeScreen.value == Screen.Settings) 0f else 1f,
                        tween(if (fadeScreen.value == Screen.Settings) BAR_OUT_MS else BAR_IN_MS),
                        label = "topBar",
                    )
                    Box(
                        Modifier
                            .graphicsLayer { alpha = barAlpha }
                            .focusProperties { onEnter = { if (onSettings) cancelFocusChange() } }
                            .focusGroup(),
                    ) {
                    TopBar(
                        showApps = style.hasAppsTab,
                        selected = if (onApps || (onSettings && settingsFrom == Screen.Apps)) 1 else 0,
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
                    }

                    TabCrossfade(
                        target = screen,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onFadeStart = { fadeScreen.value = it },
                        // As Apps e as Definições constroem-se invisíveis num momento calmo: nem a primeira visita tem o
                        // frame comprido de as construir.
                        prebuild = tabPrebuild,
                        canPrebuild = { SystemClock.elapsedRealtime() - lastInput[0] > PREBUILD_IDLE_MS },
                    ) { tab ->
                        if (tab == Screen.Settings) {
                            // Definições em página inteira (abertas no botão da roda): ocupam também o sítio da barra
                            // de cima, que se esconde. Voltar regressa ao separador de onde se veio.
                            Box(Modifier.extendUp(TopBarHeight)) {
                                SettingsScreen(
                                    vm,
                                    refocus = state.settingsRefocus,
                                    onExit = {
                                        if (settingsFrom == Screen.Apps) {
                                            vm.openApps()
                                        } else {
                                            vm.openHome()
                                            focusApp(lastFavorite())
                                        }
                                    },
                                )
                            }
                        } else if (tab == Screen.Apps) {
                            // ---- Apps: todas as apps, os favoritos na primeira linha ----
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                if (list.isEmpty()) {
                                    Text(s.noApps, Modifier.padding(top = 60.dp), color = GoatColors.TextDim)
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(APPS_COLUMNS),
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
                                    // heroApp (e não sectionApp): ao ir para as Apps, o Início fica igual enquanto desaparece no fade.
                                    ContinueSection(style, heroApp, heroItems, shelfPos, showLogo = !backdrop.value.fullBleed, actions = sectionActions, modifier = modifier)
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
                                            moreApps.chunked(APPS_COLUMNS).forEach { row ->
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
                state.menu?.let { menuShown = it }
                AnimatedVisibility(visible = state.menu != null, enter = fadeIn(tween(MENU_MS, delayMillis = MENU_BUILD_MS)), exit = fadeOut(tween(MENU_MS))) {
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
                        anchor = tileBounds[app.packageName],
                        backdrop = menuBackdrop,
                        onMove = {
                            closeMenu()
                            state.startMove(app.packageName)
                        },
                        // Tirar só se houver quem entre no lugar (a primeira app de baixo).
                        favorite = if (isFavorite && !AppGrid.canUnfavorite(gridApps.size)) null else isFavorite,
                        onToggleFavorite = {
                            closeMenu()
                            if (isFavorite) {
                                // Desce para o início da lista de baixo e a primeira de lá sobe para o lugar vago.
                                // No Início Android a app sai da fila: o foco passa para a vizinha.
                                if (!onApps && !appleTv) focusApp(HomeState.neighbour(favorites.map { it.packageName }, app.packageName))
                                vm.unfavorite(app.packageName)
                            } else {
                                // Entra no último lugar (a que lá estava desce para o início da lista de baixo) e fica
                                // logo a mover, para se pôr no sítio certo com ‹ ›.
                                vm.favorite(app.packageName)
                                state.startMove(app.packageName)
                            }
                        },
                        onChangeIcon = {
                            closeMenu()
                            state.open(Overlay.Icon(app))
                        },
                        onRename = {
                            closeMenu()
                            state.open(Overlay.Rename(app))
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
                                if (!onApps && !appleTv) focusApp(HomeState.neighbour(favorites.map { it.packageName }, app.packageName))
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
                AnimatedVisibility(visible = pinPrompt != null, enter = fadeIn(tween(MENU_MS, delayMillis = MENU_BUILD_MS)), exit = fadeOut(tween(MENU_MS))) {
                    val prompt = pinShown ?: return@AnimatedVisibility
                    PinOverlay(prompt.title, waitMs = vm::pinWaitMs, onPin = vm::submitPin)
                }
                // O código abre e fecha no ViewModel: aqui entra e sai do que está por cima. Ao fechar (certo ou
                // cancelado), o foco volta para onde estava, a menos que o código certo tenha aberto o menu (HomeState.close).
                LaunchedEffect(pinPrompt) {
                    if (pinPrompt != null) {
                        state.open(Overlay.Pin)
                        return@LaunchedEffect
                    }
                    if (state.overlay == null) return@LaunchedEffect
                    withFrameNanos { }
                    state.close(Overlay.Pin, onSettings, ::lastFavorite)
                }

                state.renaming?.let { app ->
                    RenameDialog(
                        app = app,
                        custom = device.names[app.packageName] != null,
                        onSave = {
                            state.close(Overlay.Rename(app))
                            vm.setCustomName(app.packageName, it)
                        },
                        onDismiss = { state.close(Overlay.Rename(app)) },
                    )
                }

                state.choosingIcon?.let { app ->
                    IconPicker(app, current = device.icons[app.packageName], vm = vm, onDone = { state.close(Overlay.Icon(app)) })
                }

                // Da primeira vez, se o Home ainda abre o ecrã inicial do Android (quem instala sem adb): um bocado
                // depois de abrir, para não tapar o arranque, e só sem nada por cima.
                LaunchedEffect(Unit) {
                    delay(HOME_SETUP_DELAY_MS)
                    if (state.overlay == null && vm.shouldAskHomeSetup()) state.open(Overlay.HomeSetup)
                }
                if (state.homeSetup) {
                    val answered = {
                        state.close(Overlay.HomeSetup)
                        vm.homeSetupAnswered()
                    }
                    HomeSetupDialog(
                        onTurnOn = {
                            answered()
                            SystemIntents.start(context, SystemIntents.accessibilitySettings())
                        },
                        onLater = answered,
                    )
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

/**
 * O fundo do destaque e os véus por cima dele, para o separador [tab] (o que está a aparecer: muda quando o fade do
 * TabCrossfade começa, não no frame em que o separador novo se constrói, senão as animações começavam com um salto).
 * Lê [tab] e a imagem do destaque ([backdropState]) aqui dentro: só isto volta a compor quando mudam.
 */
@Composable
private fun TabBackdrop(backdropState: State<HeroBackdrop>, hero: Boolean, columnLayout: Boolean, tab: State<Screen>) {
    val backdrop = backdropState.value
    val onApps = tab.value == Screen.Apps
    val onSettings = tab.value == Screen.Settings
    HeroBackdropImage(backdrop, visible = backdrop.fullBleed && !onApps)
    // Sempre que há destaque (com a imagem do conteúdo ou sobre o fundo normal), para o texto grande se ler.
    val sideScrim by animateFloatAsState(if (hero && !onApps) 1f else 0f, tween(400), label = "side")
    // Apps ao lado: o texto fica por cima da imagem toda (não só à esquerda), por isso escurece-se mais.
    val columnDim by animateFloatAsState(if (columnLayout && backdrop.fullBleed && !onApps) 1f else 0f, tween(400), label = "columnDim")
    val appsDim by animateFloatAsState(if (onApps || onSettings) 1f else 0f, tween(250), label = "dim")
    HomeScrims(side = { sideScrim }, columnDim = { columnDim }, appsDim = { appsDim })
}

/** O vidro da doca (Apple TV): escuro no modo noite, claro no modo dia, como o resto do ecrã. */
fun dockColor(night: Boolean): Color = if (night) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.26f)

private const val MENU_MS = 120

/** O menu constrói-se no primeiro frame (na SHIELD ~30 ms): o fade só começa dois frames depois, para esse frame
 * pesado passar ainda invisível. */
private const val MENU_BUILD_MS = 34

private const val HOME_SETUP_DELAY_MS = 3000L

private const val AMBIENT_FADE_MS = 1500

/** A tecla [key] para a tabela do Início. OK inclui o A e o Select do comando de jogos só a mover (como sempre). */
private fun homeKeyOf(key: Key, moving: Boolean): HomeKey = when (key) {
    Key.DirectionLeft -> HomeKey.Left
    Key.DirectionRight -> HomeKey.Right
    Key.DirectionUp -> HomeKey.Up
    Key.DirectionDown -> HomeKey.Down
    in OkKeys -> HomeKey.Ok
    Key.ButtonA, Key.ButtonSelect -> if (moving) HomeKey.Ok else HomeKey.Other
    Key.Back -> HomeKey.Back
    else -> HomeKey.Other
}

/** A altura da barra de cima (a do TopBar): as Definições em página inteira ocupam também esse sítio. */
private val TopBarHeight = 48.dp

/** A barra de cima some-se ao abrir as Definições e volta ao fechá-las, ao ritmo do fade da página. */
private const val BAR_OUT_MS = 150
private const val BAR_IN_MS = 220

/** Estende o conteúdo [by] para cima (por cima do que está antes dele na coluna), sem mudar o espaço que ocupa. */
private fun Modifier.extendUp(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = by.roundToPx()
    val maxH = if (constraints.hasBoundedHeight) constraints.maxHeight + extra else constraints.maxHeight
    val placeable = measurable.measure(constraints.copy(minHeight = minOf(constraints.minHeight + extra, maxH), maxHeight = maxH))
    layout(placeable.width, (placeable.height - extra).coerceAtLeast(0)) { placeable.place(0, -extra) }
}

/** Sem teclas há este tempo: dá para construir, invisíveis, os separadores ainda não visitados. */
private const val PREBUILD_IDLE_MS = 1_500L
