package cc.slokker.goatlauncher.ui.home

import cc.slokker.goatlauncher.ui.common.OkKeys
import android.view.ViewConfiguration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.data.LauncherApp
import cc.slokker.goatlauncher.ui.theme.GoatColors
import cc.slokker.goatlauncher.ui.theme.GoatShapes
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.staticCompositionLocalOf
import cc.slokker.goatlauncher.data.AccountSettings
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Brush
import androidx.tv.material3.Icon

private val BannerShape = GoatShapes.Control

/** Um favorito ou uma app da grelha: o banner e, por baixo, o nome (mais claro com foco). */
@Composable
fun AppTile(
    app: LauncherApp,
    width: Dp,
    custom: ImageBitmap?,
    customPending: Boolean,
    showLabel: Boolean,
    hasContinue: Boolean,
    moving: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    bannerModifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        AppBanner(
            app = app,
            width = width,
            custom = custom,
            customPending = customPending,
            hasContinue = hasContinue,
            moving = moving,
            onFocused = onFocused,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = bannerModifier.onFocusChanged { focused = it.isFocused },
        )
        if (showLabel) {
            Text(
                app.label,
                Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal),
                color = if (focused) GoatColors.Text else GoatColors.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * O cartão 16:9 de uma app. Com foco fica maior e com o efeito de foco escolhido (por defeito o contorno branco,
 * como no tvOS; ver FocusEffect). Em modo "mover" ([moving]) o contorno passa a ser o gradiente da marca, e o cartão abana como na Apple TV.
 */
@Composable
fun AppBanner(
    app: LauncherApp,
    width: Dp,
    /** Ícone escolhido pela pessoa (icon pack ou imagem da box), em vez do banner da app. */
    custom: ImageBitmap?,
    /** O ícone escolhido ainda está a carregar: só a cor da app, sem o banner original. */
    customPending: Boolean = false,
    hasContinue: Boolean,
    moving: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // OK tratado aqui (não pelo Surface): a pressão longa só abre o menu ao largar o botão. Assim o "largar"
    // não cai no menu (escolhendo "Mover" sem querer) nem deixa o banner preso no estado "carregado".
    var pressedAt by remember { mutableLongStateOf(NOT_PRESSED) }
    var armed by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val armedScale by animateFloatAsState(if (armed) 0.94f else 1f, tween(120), label = "armed")
    // Ícone transparente com o fundo "transparente" (Aparência): o cartão não tem cor, vê-se o fundo do ecrã.
    val container = if (isSeeThrough(custom)) Color.Transparent else app.color
    // A escala do foco é feita aqui (não pelo Surface) para o efeito de foco crescer com o cartão.
    val focusScale by animateFloatAsState(if (focused) FocusScale else 1f, tween(150), label = "focusScale")
    // Em modo "mover" o cartão abana, como as apps da Apple TV: roda uns graus para cada lado, sem parar.
    val jiggle = rememberInfiniteTransition(label = "jiggle")
    val wobble by jiggle.animateFloat(
        initialValue = -JiggleDegrees, targetValue = JiggleDegrees,
        animationSpec = infiniteRepeatable(tween(JIGGLE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "wobble",
    )
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .size(width, width * 9 / 16)
            .graphicsLayer {
                scaleX = armedScale * focusScale
                scaleY = armedScale * focusScale
                rotationZ = if (moving) wobble else 0f
            }
            .focusEffect(focused, app.color, moving)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused() else {
                    pressedAt = NOT_PRESSED
                    armed = false
                }
            }
            .onPreviewKeyEvent { e ->
                // Botão Menu do comando: abre logo o menu da app (o mesmo que manter OK).
                if (e.key == Key.Menu) {
                    if (e.type == KeyEventType.KeyUp) onLongClick()
                    return@onPreviewKeyEvent true
                }
                if (e.key !in OkKeys) return@onPreviewKeyEvent false
                val native = e.nativeKeyEvent
                when (e.type) {
                    KeyEventType.KeyDown -> when {
                        native.repeatCount == 0 -> {
                            pressedAt = native.eventTime
                            armed = false
                        }
                        // A pressão longa fica "armada" (o banner encolhe um pouco) enquanto o botão está em baixo.
                        pressedAt != NOT_PRESSED && (native.isLongPress || native.eventTime - pressedAt >= LONG_PRESS_MS) -> armed = true
                    }
                    KeyEventType.KeyUp -> {
                        // Largar sem ter carregado aqui (o foco chegou com o botão em baixo) não faz nada.
                        if (pressedAt != NOT_PRESSED) {
                            if (armed || native.eventTime - pressedAt >= LONG_PRESS_MS) onLongClick() else onClick()
                        }
                        pressedAt = NOT_PRESSED
                        armed = false
                    }
                }
                true
            },
        shape = ClickableSurfaceDefaults.shape(BannerShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            focusedContainerColor = container,
            pressedContainerColor = container,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(Modifier.fillMaxSize()) {
            BannerImage(app, custom, customPending)

            if (hasContinue) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(8.dp)
                        .shadow(2.dp, CircleShape)
                        .background(Color.White, CircleShape),
                )
            }
        }
    }
}

/** O que se vê num cartão de app: o ícone escolhido, o banner, ou o ícone da app sobre a cor dela. */
@Composable
fun BoxScope.BannerImage(app: LauncherApp, custom: ImageBitmap?, customPending: Boolean = false) {
    when {
        custom != null -> CustomIconImage(custom, background = app.color)
        customPending -> Unit
        app.banner != null -> Image(app.banner, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        app.icon != null -> Image(app.icon, contentDescription = app.label, modifier = Modifier.align(Alignment.Center).size(52.dp))
        else -> Text(app.label, Modifier.align(Alignment.Center).padding(8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private const val NOT_PRESSED = -1L

private val LONG_PRESS_MS = ViewConfiguration.getLongPressTimeout().toLong()

/** O abanar do modo "mover": graus para cada lado e o tempo de ida. */
private const val JiggleDegrees = 1.6f
private const val JIGGLE_MS = 130

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
