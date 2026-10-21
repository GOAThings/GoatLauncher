package cc.slokker.goatlauncher.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import cc.slokker.goatlauncher.data.ImageImport
import cc.slokker.goatlauncher.data.Wallpaper
import cc.slokker.goatlauncher.ui.Strings
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision

/** Fundos que vêm com a app, desenhados em código (nítidos em qualquer ecrã e sem pesar no APK). */
class BuiltinWallpaper(val id: String, private val pt: String, private val en: String, val draw: DrawScope.() -> Unit) {
    fun name(s: Strings) = if (s.english) en else pt
}

object Wallpapers {
    val ALL = listOf(
        BuiltinWallpaper("lake", "Lago", "Lake") { drawLake() },
        BuiltinWallpaper("aurora", "Aurora", "Aurora") { drawAurora() },
        BuiltinWallpaper("sunset", "Pôr do sol", "Sunset") { drawSunset() },
        BuiltinWallpaper("graphite", "Grafite", "Graphite") { drawGraphite() },
    )

    fun byId(id: String) = ALL.firstOrNull { it.id == id } ?: ALL.first()
}

/**
 * O fundo em ecrã inteiro. Uma imagem (da box, do Reddit, ou de um endereço posto na conta) aparece por cima
 * do fundo por defeito; ao mudar de imagem (o Reddit troca de tempos a tempos), a nova entra com um fade lento.
 */
@Composable
fun WallpaperView(wallpaper: Wallpaper, redditUrl: String?, modifier: Modifier = Modifier) {
    val builtin = if (wallpaper.type == Wallpaper.TYPE_BUILTIN) Wallpapers.byId(wallpaper.value) else Wallpapers.ALL.first()
    Box(modifier.fillMaxSize()) {
        val context = LocalContext.current
        val image: Any? = when (wallpaper.type) {
            Wallpaper.TYPE_URL -> wallpaper.value
            // Imagem da box copiada para a app; noutra TV da conta não existe e fica o fundo por defeito.
            Wallpaper.TYPE_FILE -> ImageImport.wallpaperFile(context, wallpaper.value).takeIf { it.exists() }
            Wallpaper.TYPE_REDDIT -> redditUrl
            else -> null
        }
        // O desenho por defeito só aparece sem foto (ou se a foto falhar): enquanto ela carrega fica o fundo
        // escuro do ecrã (não pisca o desenho antes da foto). Por baixo da foto também não se desenha: são
        // caminhos em ecrã inteiro que a GPU da box desenha mal, e custavam isso em cada frame.
        var failed by remember(image) { mutableStateOf(false) }
        if (image == null || failed) Spacer(Modifier.fillMaxSize().drawWithCache { onDrawBehind { builtin.draw(this) } })
        // O Crossfade mantém a imagem anterior enquanto a nova aparece (a nova já vem carregada da cache).
        Crossfade(targetState = image, animationSpec = tween(1200), label = "wallpaper") { model ->
            if (model != null) {
                AsyncImage(
                    // Do tamanho do ecrã, não maior: as fotos do Reddit vêm com 4000+ px.
                    model = ImageRequest.Builder(context).data(model).precision(Precision.EXACT).crossfade(400).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { if (model == image) failed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ---- Desenhos ----

/** Silhueta de montanhas: pontos (x, y) de 0 a 1, fechada no fundo do ecrã. */
private fun ridge(size: Size, vararg points: Pair<Float, Float>, bottom: Float = 1f) = Path().apply {
    moveTo(0f, size.height * bottom)
    points.forEach { (x, y) -> lineTo(x * size.width, y * size.height) }
    lineTo(size.width, size.height * bottom)
    close()
}

/** Lago entre montanhas, ao estilo do fundo do tvOS. */
private fun DrawScope.drawLake() {
    drawRect(Brush.verticalGradient(0f to Color(0xFF5E7FA6), 0.35f to Color(0xFF9DB4CC), 0.55f to Color(0xFFD5DEE6)))
    // Nuvens
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), Offset(size.width * 0.25f, size.height * 0.18f), size.width * 0.25f), size.width * 0.25f, Offset(size.width * 0.25f, size.height * 0.18f))
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent), Offset(size.width * 0.7f, size.height * 0.1f), size.width * 0.3f), size.width * 0.3f, Offset(size.width * 0.7f, size.height * 0.1f))
    // Serra ao longe
    drawPath(
        ridge(size, 0f to 0.42f, 0.12f to 0.33f, 0.2f to 0.38f, 0.3f to 0.3f, 0.38f to 0.36f, 0.48f to 0.4f, 0.6f to 0.45f, 1f to 0.5f, bottom = 0.62f),
        Brush.verticalGradient(0.3f to Color(0xFF8395AC), 0.62f to Color(0xFF6C7F93), startY = 0f, endY = size.height),
    )
    // Montanha grande à direita (com neve no cimo)
    val big = ridge(size, 0.38f to 0.62f, 0.5f to 0.4f, 0.62f to 0.14f, 0.7f to 0.1f, 0.78f to 0.2f, 0.86f to 0.16f, 1f to 0.3f, bottom = 0.64f)
    drawPath(big, Brush.verticalGradient(0.1f to Color(0xFF8C939A), 0.3f to Color(0xFF5B6168), 0.64f to Color(0xFF3B4A44), startY = 0f, endY = size.height))
    // Floresta
    drawPath(
        ridge(size, 0f to 0.52f, 0.1f to 0.55f, 0.2f to 0.58f, 0.3f to 0.6f, 0.4f to 0.63f, 0.55f to 0.64f, 0.8f to 0.63f, 1f to 0.58f, bottom = 0.68f),
        Color(0xFF2C4436),
    )
    // Lago
    drawRect(
        Brush.verticalGradient(0f to Color(0xFF7FB3B3), 1f to Color(0xFF3F7F86), startY = size.height * 0.63f, endY = size.height * 0.85f),
        topLeft = Offset(0f, size.height * 0.63f),
        size = Size(size.width, size.height * 0.25f),
    )
    // Margem da frente
    drawPath(
        ridge(size, 0f to 0.72f, 0.12f to 0.78f, 0.3f to 0.86f, 0.55f to 0.84f, 0.75f to 0.8f, 0.9f to 0.74f, 1f to 0.66f),
        Brush.verticalGradient(0.66f to Color(0xFF203A2B), 1f to Color(0xFF0E1A14), startY = 0f, endY = size.height),
    )
}

private fun DrawScope.drawAurora() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF060A18), Color(0xFF0B1230), Color(0xFF05070D))))
    fun blob(color: Color, x: Float, y: Float, r: Float) = drawCircle(
        brush = Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(x * size.width, y * size.height), radius = r * size.width),
        radius = r * size.width,
        center = Offset(x * size.width, y * size.height),
    )
    blob(Color(0xFF38BDF8).copy(alpha = 0.35f), 0.2f, 0.25f, 0.55f)
    blob(Color(0xFF818CF8).copy(alpha = 0.38f), 0.8f, 0.8f, 0.6f)
    blob(Color(0xFFC084FC).copy(alpha = 0.2f), 0.6f, 0.15f, 0.4f)
    blob(Color(0xFF34D399).copy(alpha = 0.12f), 0.45f, 0.55f, 0.35f)
}

private fun DrawScope.drawSunset() {
    drawRect(Brush.verticalGradient(0f to Color(0xFF2B1B4A), 0.45f to Color(0xFFB4436C), 0.62f to Color(0xFFF5A35C), 0.63f to Color(0xFF3A2440)))
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFE3A3), Color(0x00FFE3A3)), Offset(size.width * 0.5f, size.height * 0.6f), size.width * 0.18f),
        size.width * 0.18f,
        Offset(size.width * 0.5f, size.height * 0.6f),
    )
    drawPath(ridge(size, 0f to 0.66f, 0.2f to 0.6f, 0.45f to 0.67f, 0.7f to 0.58f, 1f to 0.64f), Color(0xFF3A2440))
    drawPath(ridge(size, 0f to 0.78f, 0.3f to 0.72f, 0.6f to 0.8f, 1f to 0.74f), Color(0xFF21142B))
}

private fun DrawScope.drawGraphite() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2A2D33), Color(0xFF15171B), Color(0xFF0B0C0E))))
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent), Offset(size.width * 0.3f, 0f), size.width * 0.7f),
        size.width * 0.7f,
        Offset(size.width * 0.3f, 0f),
    )
}
