package cc.slokker.goatlauncher.data

/**
 * Um valor calculado a partir da imagem de cada app (o PNG, o hash do PNG…), guardado enquanto essa imagem for a mesma:
 * o InstalledApps só desenha outra quando a app é atualizada. Uma app atualizada substitui o seu valor e uma
 * desinstalada sai no [retain] seguinte, por isso nunca há mais do que um valor por app instalada.
 */
internal class PerAppCache<S : Any, V>(private val compute: (S) -> V) {
    private val entries = HashMap<String, Pair<S, V>>()

    /** O valor de [source] (a imagem atual de [pkg]), calculado só se ainda não estiver guardado. */
    fun get(pkg: String, source: S): V = synchronized(entries) {
        val cached = entries[pkg]
        if (cached != null && cached.first === source) return cached.second
        compute(source).also { entries[pkg] = source to it }
    }

    /** Esquece as apps que já não estão instaladas. */
    fun retain(pkgs: Collection<String>) {
        val keep = pkgs.toHashSet()
        synchronized(entries) { entries.keys.retainAll(keep) }
    }

    val size: Int get() = synchronized(entries) { entries.size }
}
