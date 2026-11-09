package cc.slokker.goatlauncher.data

import android.content.Context
import okhttp3.OkHttpClient

/**
 * As fontes de fotos online do fundo e qual delas serve cada [Wallpaper]. [userAgent] identifica a app com um
 * contacto, como pedem o Wikimedia e a NASA (o Reddit tem a sua, ver RedditFeed).
 */
class OnlineWallpapers(context: Context, http: OkHttpClient, userAgent: String) {
    /** O nome do ficheiro é o de sempre: a foto atual e a lista guardada continuam ao atualizar a app. */
    val reddit = PhotoPool(context, "reddit_wallpapers", RedditFeed(http))
    val wikimedia = PhotoPool(context, "wikimedia_wallpapers", WikimediaFeed(http, userAgent))
    val nasa = PhotoPool(context, "nasa_wallpapers", ApodFeed(http, userAgent))

    /** Uma fonte e o id dentro dela (o subreddit, por exemplo). */
    data class Source(val pool: PhotoPool, val id: String)

    /** A fonte das fotos de [wallpaper]; null se o fundo não for de fotos online. */
    fun source(wallpaper: Wallpaper): Source? = when (wallpaper.type) {
        Wallpaper.TYPE_REDDIT -> Source(reddit, wallpaper.value)
        Wallpaper.TYPE_WIKIMEDIA -> Source(wikimedia, WikimediaFeed.ID)
        Wallpaper.TYPE_NASA -> Source(nasa, ApodFeed.ID)
        else -> null
    }

    /** A última foto mostrada de [wallpaper] (para o protetor de ecrã e ao abrir o launcher). */
    suspend fun lastShown(wallpaper: Wallpaper): OnlinePhoto? = source(wallpaper)?.let { it.pool.lastShown(it.id) }
}
