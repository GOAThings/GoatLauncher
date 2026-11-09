package cc.slokker.goatlauncher.ui.home

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * As medidas do ecrã inicial, num só sítio: o ecrã e a pré-visualização da Aparência usam estas, por isso a
 * pré-visualização não se afasta do ecrã quando uma muda. (O ecrã da TV tem 960 × 540 dp.)
 */
object HomeMetrics {
    /** Os favoritos na fila (e as apps da grelha). */
    val TileWidth = 148.dp
    val TileGap = 24.dp
    val TilesRowWidth = TileWidth * FavoriteCount + TileGap * (FavoriteCount - 1)

    /** Apps ao lado: os favoritos em coluna, mais pequenos para os cinco caberem na altura do ecrã. */
    val ColumnTileWidth = 116.dp
    val ColumnTileGap = 14.dp

    /** A doca da Apple TV: folga entre os favoritos das pontas e a borda (o banner com foco cresce 10%). */
    val DockPadding = 30.dp
    val DockShape = RoundedCornerShape(30.dp)

    /** A altura dos cartões da fila do "Continuar a ver" (a largura vem da forma da imagem). */
    val CardHeight = 88.dp

    /** Os cartões grandes da Apple TV ocupam a altura que sobra, até esta. */
    val TopShelfCardMax = 230.dp

    /** O carrossel: a altura máxima do cartão do meio, o tamanho dos vizinhos e o espaço entre eles. */
    val CarouselMax = 300.dp
    const val CarouselSideScale = 0.72f
    val CarouselGap = 28.dp
}
