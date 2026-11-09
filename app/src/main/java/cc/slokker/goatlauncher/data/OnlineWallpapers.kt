package cc.slokker.goatlauncher.data

import android.content.Context
import okhttp3.OkHttpClient
import java.io.File

/**
 * As fontes de fotos online do fundo e qual delas serve cada [Wallpaper]. [userAgent] identifica a app com um
 * contacto, como pedem o Wikimedia e a NASA.
 */
class OnlineWallpapers(context: Context, http: OkHttpClient, userAgent: String) {
    val wikimedia = PhotoPool(context, "wikimedia_wallpapers", WikimediaFeed(http, userAgent))
    val nasa = PhotoPool(context, "nasa_wallpapers", ApodFeed(http, userAgent))

    init {
        // O Reddit deixou de ser fonte: apaga a lista e a foto atual que ele guardava (até à 0.9.11). Já ninguém abre
        // estas preferências, por isso apagar o ficheiro é seguro; depois da primeira vez não há nada para apagar. As
        // fotos que ficaram na cache do Coil saem sozinhas, como as outras antigas.
        val prefs = File(context.applicationInfo.dataDir, "shared_prefs")
        listOf("$OLD_REDDIT_PREFS.xml", "$OLD_REDDIT_PREFS.xml.bak").forEach { File(prefs, it).delete() }
    }

    /** Uma fonte e o id dentro dela. */
    data class Source(val pool: PhotoPool, val id: String)

    /** A fonte das fotos de [wallpaper]; null se o fundo não for de fotos online. */
    fun source(wallpaper: Wallpaper): Source? = when (wallpaper.type) {
        Wallpaper.TYPE_WIKIMEDIA -> Source(wikimedia, WikimediaFeed.ID)
        Wallpaper.TYPE_NASA -> Source(nasa, ApodFeed.ID)
        else -> null
    }

    /** A última foto mostrada de [wallpaper] (para o protetor de ecrã e ao abrir o launcher). */
    suspend fun lastShown(wallpaper: Wallpaper): OnlinePhoto? = source(wallpaper)?.let { it.pool.lastShown(it.id) }

    private companion object {
        const val OLD_REDDIT_PREFS = "reddit_wallpapers"
    }
}
