package cc.slokker.goatlauncher.data

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Uma entrada do "Continuar a ver" (Watch Next do Android TV), publicada pela app [packageName]. */
@Immutable
data class WatchNextItem(
    val id: Long,
    val packageName: String,
    val title: String,
    val episodeTitle: String?,
    val season: String?,
    val episode: String?,
    val imageUri: String?,
    /** Largura / altura da imagem. */
    val aspectRatio: Float,
    val positionMs: Long,
    val durationMs: Long,
    val type: Int,
    val lastEngagement: Long,
    val intentUri: String?,
) {
    /** 0..1, ou null sem duração ou posição. */
    val progress: Float?
        get() = if (durationMs > 0 && positionMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else null

    val remainingMinutes: Long?
        get() = if (durationMs > 0 && positionMs > 0) ((durationMs - positionMs).coerceAtLeast(0) + 59_999) / 60_000 else null

    /** O intent que abre a app neste conteúdo, ou null se o `intent_uri` não se ler. */
    fun intent(): Intent? {
        val uri = intentUri ?: return null
        return runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }
            .onFailure { Log.w("WatchNext", "intent_uri inválido de $packageName: $uri") }
            .getOrNull()
            ?.apply {
                // Um link simples (https://…) abriria o seletor ou o browser: vai para a app que o publicou.
                if (`package` == null && component == null) setPackage(packageName)
                // Nunca dar a outras apps acesso a URIs nossos por um intent vindo de fora.
                flags = (flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION).inv()) or
                    Intent.FLAG_ACTIVITY_NEW_TASK
                selector = null
            }
    }

    companion object {
        const val TYPE_CONTINUE = TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE
    }
}

/**
 * Lê o Watch Next do TvProvider (o mesmo que o Google TV e o Projectivy usam) e agrupa-o por app.
 * Precisa de READ_TV_LISTINGS (ver o manifesto); sem ela, o sistema só devolve os programas da própria app.
 * O TvProvider não aceita `selection` a quem não é do sistema: lê-se tudo e filtra-se aqui.
 */
class WatchNextSource(private val context: Context) {

    private val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    /** Por pacote, o mais recente primeiro. */
    suspend fun load(): Map<String, List<WatchNextItem>> = withContext(Dispatchers.IO) {
        if (!supported) return@withContext emptyMap()
        runCatching {
            context.contentResolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, PROJECTION, null, null, null)
                ?.use { c -> buildList { while (c.moveToNext()) read(c)?.let(::add) } }
                .orEmpty()
                .sortedByDescending { it.lastEngagement }
                // Entradas repetidas (ex.: a app perdeu o id da que publicou e criou outra): fica a mais recente.
                .distinctBy { it.packageName + "\u0000" + (it.intentUri ?: it.title) }
                .groupBy { it.packageName }
                .also { all ->
                    Log.d(TAG, "${all.values.sumOf { l -> l.size }} entradas de ${all.keys}")
                    // Que imagens publica cada app (para perceber de onde vem a qualidade dos cartões).
                    all.forEach { (pkg, items) -> items.firstOrNull()?.let { Log.d(TAG, "$pkg: ${it.imageUri}") } }
                }
        }.getOrElse {
            Log.w(TAG, "Ler o Watch Next", it)
            emptyMap()
        }
    }

    /** Chama [onChange] quando alguma app mexe no Watch Next. Devolve a função que deixa de observar. */
    fun observe(onChange: () -> Unit): () -> Unit {
        if (!supported) return {}
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
            override fun onChange(selfChange: Boolean, uri: Uri?) = onChange()
        }
        return runCatching {
            context.contentResolver.registerContentObserver(TvContractCompat.WatchNextPrograms.CONTENT_URI, true, observer)
            fun() { context.contentResolver.unregisterContentObserver(observer) }
        }.getOrElse {
            Log.w(TAG, "Observar o Watch Next", it)
            fun() {}
        }
    }

    private fun read(c: Cursor): WatchNextItem? {
        if (c.int(COL_BROWSABLE) == 0) return null // a pessoa tirou-o do "A seguir"
        val pkg = c.str(COL_PACKAGE) ?: return null
        val title = c.str(COL_TITLE) ?: c.str(COL_EPISODE_TITLE) ?: return null
        val poster = c.str(COL_POSTER)
        val thumb = c.str(COL_THUMB)
        return WatchNextItem(
            id = c.long(COL_ID) ?: return null,
            packageName = pkg,
            title = title,
            episodeTitle = c.str(COL_EPISODE_TITLE),
            season = c.str(COL_SEASON),
            episode = c.str(COL_EPISODE),
            imageUri = sharper(poster ?: thumb),
            aspectRatio = ratio(if (poster != null) c.int(COL_POSTER_RATIO) else c.int(COL_THUMB_RATIO)),
            positionMs = c.long(COL_POSITION) ?: 0,
            durationMs = c.long(COL_DURATION) ?: 0,
            type = c.int(COL_TYPE) ?: WatchNextItem.TYPE_CONTINUE,
            lastEngagement = c.long(COL_ENGAGEMENT) ?: 0,
            intentUri = c.str(COL_INTENT),
        )
    }

    /**
     * Muitas apps (ex.: Nuvio) publicam imagens do TMDB num tamanho pequeno (`/t/p/w300/…`), que no cartão
     * e sobretudo no destaque de ecrã inteiro ficam esborratadas. Pede-se a versão grande da mesma imagem.
     */
    private fun sharper(uri: String?): String? = uri?.replace(TMDB_SIZE, "image.tmdb.org/t/p/w1280/")

    /** Constantes ASPECT_RATIO_* do TvContract. Por defeito 16:9, o formato do ecrã. */
    private fun ratio(constant: Int?): Float = when (constant) {
        TvContractCompat.PreviewPrograms.ASPECT_RATIO_3_2 -> 3f / 2f
        TvContractCompat.PreviewPrograms.ASPECT_RATIO_4_3 -> 4f / 3f
        TvContractCompat.PreviewPrograms.ASPECT_RATIO_1_1 -> 1f
        TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3 -> 2f / 3f
        TvContractCompat.PreviewPrograms.ASPECT_RATIO_MOVIE_POSTER -> 1f / 1.441f
        else -> 16f / 9f
    }

    private fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)?.takeIf { it.isNotBlank() }
    private fun Cursor.long(i: Int): Long? = if (isNull(i)) null else getLong(i)
    private fun Cursor.int(i: Int): Int? = if (isNull(i)) null else getInt(i)

    private companion object {
        const val TAG = "WatchNext"
        private val TMDB_SIZE = Regex("""image\.tmdb\.org/t/p/(?:w\d{2,3}|h\d{2,3})/""")

        // Nomes das colunas do TvContract (estáveis desde o Android 8).
        val PROJECTION = arrayOf(
            "_id",
            "package_name",
            "title",
            "episode_title",
            "season_display_number",
            "episode_display_number",
            "poster_art_uri",
            "poster_art_aspect_ratio",
            "thumbnail_uri",
            "thumbnail_aspect_ratio",
            "last_playback_position_millis",
            "duration_millis",
            "watch_next_type",
            "last_engagement_time_utc_millis",
            "intent_uri",
            "browsable",
        )
        const val COL_ID = 0
        const val COL_PACKAGE = 1
        const val COL_TITLE = 2
        const val COL_EPISODE_TITLE = 3
        const val COL_SEASON = 4
        const val COL_EPISODE = 5
        const val COL_POSTER = 6
        const val COL_POSTER_RATIO = 7
        const val COL_THUMB = 8
        const val COL_THUMB_RATIO = 9
        const val COL_POSITION = 10
        const val COL_DURATION = 11
        const val COL_TYPE = 12
        const val COL_ENGAGEMENT = 13
        const val COL_INTENT = 14
        const val COL_BROWSABLE = 15
    }
}
