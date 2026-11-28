package cc.slokker.goatlauncher.data

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * As apps de Jellyfin/Emby (ex.: RealJF) publicam a imagem "Thumb" do filme (arte em paisagem, muitas vezes com o título
 * e selos desenhados por cima, ex.: "BLOCKBUSTER") ou a "Primary" (o cartaz ao alto; num episódio, uma cena dele).
 * - Filmes: o "Backdrop" do mesmo filme, no mesmo servidor (a cena limpa, em 16:9), passa a ser a imagem.
 * - Episódios: a cena do episódio fica nos cartões; o fundo da série fica para o que enche o ecrã ([WatchNextItem.wideImageUri]):
 *   as cenas dos episódios são pequenas (400–640 px) e no destaque ficavam esborratadas.
 *
 * Cada URL só se confirma uma vez (um HEAD), e a resposta fica guardada enquanto o processo vive: o Watch Next volta a
 * ler-se a cada mudança. O que o servidor não tem fica como a app publicou.
 */
internal class Backdrops(http: OkHttpClient) {
    private val http = http.newBuilder().callTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS).build()

    /** URL do fundo → se o servidor o tem. Sem entrada: ainda por saber (ou falhou a rede: tenta-se outra vez). */
    private val known = ConcurrentHashMap<String, Boolean>()

    suspend fun apply(byApp: Map<String, List<WatchNextItem>>): Map<String, List<WatchNextItem>> {
        val wanted = byApp.values.flatten().mapNotNull(::candidate).distinct().filter { !known.containsKey(it) }
        if (wanted.isNotEmpty()) {
            coroutineScope { wanted.map { url -> async { probe(url)?.let { known[url] = it } } }.awaitAll() }
        }
        return byApp.mapValues { (_, items) ->
            items.map { item ->
                val url = candidate(item)?.takeIf { known[it] == true } ?: return@map item
                if (item.isEpisode) item.copy(wideImageUri = url) else item.copy(imageUri = url, aspectRatio = 16f / 9f)
            }
        }
    }

    /** O fundo a experimentar para [item]: o da série, se é um episódio; o do próprio conteúdo, se não. */
    private fun candidate(item: WatchNextItem): String? {
        if (item.isChannel) return null
        val uri = item.imageUri ?: return null
        return if (item.isEpisode) showBackdropOf(uri) else backdropOf(uri)
    }

    /** true se existe, false se o servidor diz que não (4xx), null se não deu para saber (fica para a próxima). */
    private suspend fun probe(url: String): Boolean? = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
        try {
            http.newCall(Request.Builder().url(url).head().build()).execute().use { res ->
                when {
                    res.isSuccessful -> res.header("Content-Type").orEmpty().startsWith("image/")
                    res.code in 400..499 -> false
                    else -> null
                }
            }
        } catch (e: IOException) {
            Log.d(TAG, "Fundo por confirmar: ${e.javaClass.simpleName}")
            null
        } catch (e: IllegalArgumentException) {
            false // URL que o OkHttp não aceita
        }
    }

    companion object {
        private const val TAG = "Backdrops"
        private const val PROBE_TIMEOUT_MS = 4_000L

        /** `…/Items/{id}/Images/Thumb` ou `…/Primary` (com ou sem índice e parâmetros), de um servidor Jellyfin ou Emby. */
        private val ITEM_IMAGE = Regex("""^(https?://[^?#]+/Items/)([^/?#]+)/Images/(?:Thumb|Primary)(?:/\d+)?(?:\?([^#]*))?$""", RegexOption.IGNORE_CASE)

        /**
         * Os ids que o AIOMetadata dá aos episódios: o da série com o tipo `a14…` em vez de `a12…` e a temporada e o
         * episódio nos 6 primeiros dígitos do último bloco (na série, `ffffff`). Um Jellyfin normal tem ids ao acaso:
         * não há como saber a série sem sessão, e o episódio fica com a cena dele.
         */
        private val AIOMETADATA_EPISODE = Regex("""^a14([0-9a-f]{5}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-)[0-9a-f]{6}([0-9a-f]{6})$""", RegexOption.IGNORE_CASE)

        /** Os parâmetros que dão acesso à imagem (os servidores que a pedem): passam para o fundo. */
        private val AUTH_PARAMS = setOf("api_key", "apikey")

        /**
         * O URL do fundo do mesmo conteúdo, ou null se [uri] não é uma imagem de Jellyfin/Emby. Sem a `tag` (é a da
         * imagem da app, não a do fundo) e no tamanho do ecrã, para o destaque.
         */
        fun backdropOf(uri: String): String? {
            val m = ITEM_IMAGE.matchEntire(uri) ?: return null
            return backdrop(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        }

        /** O URL do fundo da série do episódio de [uri], ou null se não se sabe qual é (ver [AIOMETADATA_EPISODE]). */
        fun showBackdropOf(uri: String): String? {
            val m = ITEM_IMAGE.matchEntire(uri) ?: return null
            val ep = AIOMETADATA_EPISODE.matchEntire(m.groupValues[2]) ?: return null
            return backdrop(m.groupValues[1], "a12${ep.groupValues[1]}ffffff${ep.groupValues[2]}", m.groupValues[3])
        }

        private fun backdrop(items: String, id: String, query: String): String {
            val auth = query.split('&').filter { it.substringBefore('=').lowercase() in AUTH_PARAMS }
            return "$items$id/Images/Backdrop?" + (auth + "maxWidth=1920" + "quality=90").joinToString("&")
        }
    }
}
