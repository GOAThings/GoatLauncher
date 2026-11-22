package cc.slokker.goatlauncher.data

/**
 * Uma moldura preta dentro da própria imagem (trípticos, digitalizações, fotogramas de filme): o fundo enche o ecrã com
 * Crop, que corta só dois dos lados, por isso a moldura dos outros dois ficava à vista como barras pretas (visto na
 * SHIELD com "Trilogie Hexentanz", uma imagem do dia do Commons). Mede-se numa cópia pequena da imagem.
 *
 * Conta como moldura uma faixa quase preta em dois lados opostos (cima e baixo, ou esquerda e direita), com pelo menos
 * [MIN_BAND] do lado cada uma, que acaba de repente: o céu de uma foto do espaço ou de noite também é escuro nas
 * pontas, mas vai clareando aos poucos, e um só lado escuro (o céu por cima de uma cidade) não é moldura.
 */
object ImageFrame {
    /** Cada canal abaixo disto conta como preto (o preto de um JPEG nunca é 0 certinho). */
    private const val DARK = 24

    /** Uma linha da faixa: quase todos os píxeis pretos. */
    private const val BAND_SHARE = 0.97

    /** A linha logo a seguir à faixa já é imagem: no máximo esta parte preta (senão a escuridão continua, não é moldura). */
    private const val EDGE_SHARE = 0.6

    /** Cada faixa tem pelo menos isto do lado (1%): uma linha solta de um píxel não é moldura. */
    const val MIN_BAND = 0.01

    /** Mais do que isto de um lado já não é moldura, é a imagem. */
    private const val MAX_BAND = 0.25

    /** [argb]: os píxeis em linhas de [width] (como Bitmap.getPixels). */
    fun isFramed(argb: IntArray, width: Int, height: Int): Boolean {
        if (width < 8 || height < 8 || argb.size < width * height) return false
        fun row(y: Int) = darkShare(width) { x -> argb[y * width + x] }
        fun col(x: Int) = darkShare(height) { y -> argb[y * width + x] }
        val top = band(height, ::row)
        val bottom = band(height) { row(height - 1 - it) }
        val left = band(width, ::col)
        val right = band(width) { col(width - 1 - it) }
        return minOf(top, bottom) >= MIN_BAND || minOf(left, right) >= MIN_BAND
    }

    /**
     * A largura da faixa preta a partir de uma ponta, em fração do lado ([lines] linhas; [share] diz a parte preta da
     * linha i a contar da ponta). 0 se não houver, ou se não acabar de repente.
     */
    private fun band(lines: Int, share: (Int) -> Double): Double {
        val max = (lines * MAX_BAND).toInt()
        var n = 0
        while (n < max && share(n) >= BAND_SHARE) n++
        if (n == 0 || n >= max || share(n) > EDGE_SHARE) return 0.0
        return n.toDouble() / lines
    }

    private inline fun darkShare(count: Int, pixel: (Int) -> Int): Double {
        var dark = 0
        for (i in 0 until count) if (isDark(pixel(i))) dark++
        return dark.toDouble() / count
    }

    private fun isDark(c: Int) = (c shr 16 and 0xFF) < DARK && (c shr 8 and 0xFF) < DARK && (c and 0xFF) < DARK
}
