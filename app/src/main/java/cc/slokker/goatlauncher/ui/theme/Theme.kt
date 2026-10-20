package cc.slokker.goatlauncher.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import cc.slokker.goatlauncher.R

/** Cores da GoatTV, para as duas apps parecerem da mesma família. */
object GoatColors {
    val Sky = Color(0xFF38BDF8)
    val Indigo = Color(0xFF818CF8)
    val Background = Color(0xFF05070D)

    // Cores de texto ao estilo Apple (label / secondaryLabel / tertiaryLabel).
    val Text = Color(0xFFF5F5F7)
    val TextDim = Color(0xD9EBEBF5)
    val TextFaint = Color(0x99EBEBF5)

    /** Elemento com foco: vidro claro, texto branco e contorno no gradiente da marca. */
    val Focus = Color(0x33FFFFFF)
    val OnFocus = Text
    val Pressed = Color(0x4DFFFFFF)

    val Error = Color(0xFFFF6961)

    val Accent = Brush.linearGradient(listOf(Sky, Indigo))
}

/** Margens de segurança de TV (overscan). */
val TvSafePadding = PaddingValues(horizontal = 48.dp, vertical = 27.dp)

/** Adwaita Sans (derivada do Inter, OFL): a fonte livre mais próxima do SF Pro. */
private fun weight(w: Int) = Font(
    R.font.adwaita_sans,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val GoatFont = FontFamily(weight(300), weight(400), weight(500), weight(600), weight(700), weight(800))

private fun TextStyle.apple(size: Int? = null, w: Int? = null, tracking: Float? = null) = copy(
    fontFamily = GoatFont,
    fontSize = size?.sp ?: fontSize,
    fontWeight = w?.let { FontWeight(it) } ?: fontWeight,
    letterSpacing = tracking?.sp ?: letterSpacing,
)

private val GoatTypography = Typography().run {
    copy(
        displayLarge = displayLarge.apple(w = 700, tracking = -1f),
        displayMedium = displayMedium.apple(w = 700, tracking = -0.8f),
        displaySmall = displaySmall.apple(w = 700, tracking = -0.5f),
        headlineLarge = headlineLarge.apple(w = 700, tracking = -0.5f),
        headlineMedium = headlineMedium.apple(w = 700, tracking = -0.4f),
        headlineSmall = headlineSmall.apple(w = 600, tracking = -0.3f),
        titleLarge = titleLarge.apple(w = 600, tracking = -0.2f),
        titleMedium = titleMedium.apple(w = 600, tracking = 0f),
        titleSmall = titleSmall.apple(w = 600, tracking = 0f),
        bodyLarge = bodyLarge.apple(w = 400, tracking = 0f),
        bodyMedium = bodyMedium.apple(w = 400, tracking = 0f),
        bodySmall = bodySmall.apple(w = 400, tracking = 0f),
        labelLarge = labelLarge.apple(w = 600, tracking = 0f),
        labelMedium = labelMedium.apple(w = 600, tracking = 0.2f),
        labelSmall = labelSmall.apple(w = 600, tracking = 0.4f),
    )
}

/** Título do destaque do "Continuar a ver" (o episódio ou filme). */
val ShelfTitleStyle = TextStyle(fontFamily = GoatFont, fontWeight = FontWeight(700), fontSize = 54.sp, letterSpacing = (-1.2).sp, lineHeight = 60.sp)

@Composable
fun GoatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = GoatColors.Sky,
            onPrimary = GoatColors.OnFocus,
            secondary = GoatColors.Indigo,
            onSecondary = GoatColors.OnFocus,
            background = GoatColors.Background,
            onBackground = GoatColors.Text,
            surface = Color.Transparent,
            onSurface = GoatColors.Text,
            surfaceVariant = Color(0x1FFFFFFF),
            onSurfaceVariant = GoatColors.TextDim,
            inverseSurface = GoatColors.Focus,
            inverseOnSurface = GoatColors.OnFocus,
            error = GoatColors.Error,
            border = Color(0x33FFFFFF),
        ),
        typography = GoatTypography,
    ) {
        // Sem isto, o Text do tv-material fora de um Surface fica preto.
        CompositionLocalProvider(
            LocalContentColor provides GoatColors.Text,
            LocalTextStyle provides GoatTypography.bodyLarge,
            content = content,
        )
    }
}
