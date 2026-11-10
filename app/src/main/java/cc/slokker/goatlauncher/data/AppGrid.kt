package cc.slokker.goatlauncher.data

/**
 * A grelha das apps, tal como se vê no separador Apps e na Apple TV: os [FAVORITES] primeiros visíveis (os favoritos
 * do Início) e depois todas as outras, também as escondidas. É a regra única: o ecrã só diz que app mexe e para onde;
 * as posições contam-se aqui, sobre a ordem guardada.
 */
object AppGrid {
    /** Quantos favoritos tem a fila do Início. */
    const val FAVORITES = 5

    /** A grelha a partir de [order] (todas as apps, pela ordem guardada) e das [hidden]. */
    fun of(order: List<String>, hidden: Set<String>): List<String> {
        val fav = order.filterNot { it in hidden }.take(FAVORITES)
        return fav + order.filterNot { it in fav }
    }

    /**
     * Põe [pkg] no lugar [to] da grelha (limitado às pontas): as do meio chegam-se (descer um favorito traz para os
     * favoritos a primeira app de baixo). Uma app escondida que entre nos favoritos volta a aparecer no Início.
     * Devolve a ordem nova e as escondidas, ou null se [pkg] não está na grelha.
     */
    fun moveTo(order: List<String>, hidden: Set<String>, pkg: String, to: Int): Pair<List<String>, Set<String>>? {
        val grid = of(order, hidden).toMutableList()
        val from = grid.indexOf(pkg)
        if (from < 0) return null
        val target = to.coerceIn(grid.indices)
        if (target != from) {
            grid.removeAt(from)
            grid.add(target, pkg)
        }
        return grid to hidden - grid.take(FAVORITES).toSet()
    }

    /** Move [pkg] [delta] lugares na grelha. */
    fun move(order: List<String>, hidden: Set<String>, pkg: String, delta: Int): Pair<List<String>, Set<String>>? {
        val from = of(order, hidden).indexOf(pkg)
        if (from < 0) return null
        return moveTo(order, hidden, pkg, from + delta)
    }

    /** Tirar dos favoritos: desce para o início da lista de baixo e a primeira de lá sobe para o lugar vago. */
    fun unfavorite(order: List<String>, hidden: Set<String>, pkg: String) = moveTo(order, hidden, pkg, FAVORITES)

    /** Pôr nos favoritos: entra no último lugar (a que lá estava desce para o início da lista de baixo). */
    fun favorite(order: List<String>, hidden: Set<String>, pkg: String): Pair<List<String>, Set<String>>? {
        val favCount = order.count { it !in hidden }.coerceAtMost(FAVORITES)
        return moveTo(order, hidden, pkg, minOf(favCount, FAVORITES - 1))
    }

    /** Só se pode tirar um favorito se houver quem entre no lugar (a primeira app de baixo). */
    fun canUnfavorite(gridSize: Int) = gridSize > FAVORITES
}
