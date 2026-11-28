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
 * As apps de Jellyfin/Emby (ex.: RealJF) publicam no Watch Next a imagem "Thumb" do filme (arte em paisagem, muitas
 * vezes com o título e selos desenhados por cima, ex.: "BLOCKBUSTER") ou a "Primary" (o cartaz ao alto). O "Backdrop"
 * do mesmo conteúdo, no mesmo servidor, é a cena limpa em 16:9: é esse que fica, quando o servidor o tem. Os episódios
 * não costumam ter (a "Primary" deles já é uma cena do episódio): ficam com o que a app deu.
 *
 * Cada imagem só se confirma uma vez (um HEAD), e a resposta fica guardada enquanto o processo vive: o Watch Next
 * volta a ler-se a cada mudança.
 */
internal class Backdrops(http: OkHttpClient) {
    private val http = http.newBuilder().callTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS).build()

    /** Imagem da app → o fundo, ou "" (o servidor não o tem). Sem entrada: ainda por saber (ou falhou a rede). */
    private val known = ConcurrentHashMap<String, String>()

    suspend fun apply(byApp: Map<String, List<WatchNextItem>>): Map<String, List<WatchNextItem>> {
        val wanted = byApp.values.flatten().mapNotNull { item -> item.imageUri?.takeIf { !item.isChannel && !known.containsKey(it) } }
            .distinct()
            .mapNotNull { original -> backdropOf(original)?.let { original to it } }
        if (wanted.isNotEmpty()) {
            coroutineScope {
                wanted.map { (original, backdrop) -> async { probe(backdrop)?.let { ok -> known[original] = if (ok) backdrop else "" } } }.awaitAll()
            }
        }
        return byApp.mapValues { (_, items) ->
            items.map { item ->
                val backdrop = item.imageUri?.takeIf { !item.isChannel }?.let { known[it] }
                if (backdrop.isNullOrEmpty()) item else item.copy(imageUri = backdrop, aspectRatio = 16f / 9f)
            }
        }
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
        private val ITEM_IMAGE = Regex("""^(https?://[^?#]+/Items/[^/?#]+/Images/)(?:Thumb|Primary)(?:/\d+)?(?:\?([^#]*))?$""", RegexOption.IGNORE_CASE)

        /** Os parâmetros que dão acesso à imagem (os servidores que a pedem): passam para o fundo. */
        private val AUTH_PARAMS = setOf("api_key", "apikey")

        /**
         * O URL do fundo do mesmo conteúdo, ou null se [uri] não é uma imagem de Jellyfin/Emby. Sem a `tag` (é a da
         * imagem da app, não a do fundo) e no tamanho do ecrã, para o destaque.
         */
        fun backdropOf(uri: String): String? {
            val m = ITEM_IMAGE.matchEntire(uri) ?: return null
            val auth = m.groupValues[2].split('&').filter { it.substringBefore('=').lowercase() in AUTH_PARAMS }
            return m.groupValues[1] + "Backdrop?" + (auth + "maxWidth=1920" + "quality=90").joinToString("&")
        }
    }
}
