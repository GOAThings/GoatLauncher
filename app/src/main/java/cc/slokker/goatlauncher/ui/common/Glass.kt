package cc.slokker.goatlauncher.ui.common

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.ui.theme.ControlRadius
import cc.slokker.goatlauncher.ui.theme.GoatColors
import cc.slokker.goatlauncher.ui.theme.GoatShapes
import kotlin.math.roundToInt

/**
 * Vidro fumado: véu escuro translúcido, reflexo de luz no topo e contorno fino.
 * Sem desfocagem do fundo (o Android só a tem nativa a partir do 12; a SHIELD está no 11).
 */
fun Modifier.glass(shape: Shape = GoatShapes.Panel, tint: Color = Color(0x99111522)): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val highlight = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.14f),
        0.3f to Color.White.copy(alpha = 0.04f),
        1f to Color.Transparent,
    )
    val rim = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.36f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.14f)),
    )
    val stroke = Stroke(width = 1.2.dp.toPx())
    onDrawBehind {
        clipPath(path) {
            drawRect(tint)
            drawRect(highlight)
        }
        drawOutline(outline, rim, style = stroke)
    }
}

/** Botão só com texto: fundo claro translúcido; com foco fica branco com texto escuro. */
@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(GoatShapes.Control),
        colors = focusColors(container = Color.White.copy(alpha = 0.14f)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(text, Modifier.padding(horizontal = 28.dp, vertical = 12.dp), style = MaterialTheme.typography.titleSmall)
    }
}

/** Linha de lista (definições, menus). Com foco fica branca com texto escuro. */
@Composable
fun GlassRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    subtitle: String? = null,
    title: String,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(GoatShapes.Control),
        colors = focusColors(container = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.82f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                trailing()
            }
        }
    }
}

/** Interruptor pequeno para as linhas de definições (só desenho: a linha é que recebe o clique). */
@Composable
fun Toggle(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 40.dp, height = 22.dp)
            .clip(CircleShape)
            // Desligado: a cor do texto da linha, translúcida (escura na linha com foco, que é branca).
            .background(if (checked) GoatColors.Accent else SolidColor(LocalContentColor.current.copy(alpha = 0.25f))),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.padding(3.dp).size(16.dp).clip(CircleShape).background(Color.White))
    }
}

/** O foco de toda a interface (como o menu em cápsula): branco com texto escuro, sem contorno nem brilho. */
@Composable
fun focusColors(container: Color) = ClickableSurfaceDefaults.colors(
    containerColor = container,
    contentColor = GoatColors.Text,
    focusedContainerColor = Color.White,
    focusedContentColor = Color.Black,
    pressedContainerColor = Color.White.copy(alpha = 0.8f),
    pressedContentColor = Color.Black,
)


/** Título de secção pequeno, em maiúsculas. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.fillMaxWidth().padding(start = 12.dp, top = 14.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = GoatColors.TextFaint,
    )
}
