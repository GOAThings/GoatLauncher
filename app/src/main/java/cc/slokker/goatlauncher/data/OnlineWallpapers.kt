package cc.slokker.goatlauncher.data

import android.content.Context
import okhttp3.OkHttpClient

/** As fontes de fotos online do fundo e qual delas serve cada [Wallpaper]. */
class OnlineWallpapers(context: Context, http: OkHttpClient) {
    /** O nome do ficheiro é o de sempre: a foto atual e a lista guardada continuam ao atualizar a app. */
    val reddit = PhotoPool(context, "reddit_wallpapers", RedditFeed(http))

    /** Uma fonte e o id dentro dela (o subreddit, por exemplo). */
    data class Source(val pool: PhotoPool, val id: String)

    /** A fonte das fotos de [wallpaper]; null se o fundo não for de fotos online. */
    fun source(wallpaper: Wallpaper): Source? = when (wallpaper.type) {
        Wallpaper.TYPE_REDDIT -> Source(reddit, wallpaper.value)
        else -> null
    }

    /** A última foto mostrada de [wallpaper] (para o protetor de ecrã e ao abrir o launcher). */
    suspend fun lastShown(wallpaper: Wallpaper): OnlinePhoto? = source(wallpaper)?.let { it.pool.lastShown(it.id) }
}
