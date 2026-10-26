package cc.slokker.goatlauncher.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/** Um pedido de ligação em curso: a pessoa escreve [userCode] em [verificationUrl] (ex.: google.com/device). */
data class YouTubeLogin(val userCode: String, val verificationUrl: String, val deviceCode: String, val intervalSec: Int, val expiresAt: Long)

/**
 * As recomendações da conta YouTube da pessoa, para o "Continuar a ver". Usa a mesma API que a app de TV do
 * YouTube (e a TizenTube): ligação por código em google.com/device (a TV nunca vê a password) e o feed
 * "Início" da TV. Não é uma API pública: se o YouTube a mudar, isto deixa de dar resultados (sem partir nada).
 *
 * As credenciais da app de TV do YouTube não vêm na app: lêem-se de youtube.com/tv, como faz o próprio site.
 */
class YouTubeSource(context: Context, private val http: OkHttpClient) {
    private val prefs = context.getSharedPreferences("youtube", Context.MODE_PRIVATE)
    private val json = "application/json".toMediaType()

    val linked: Boolean get() = prefs.contains("refresh")

    /** Os tokens e o segredo guardados, para os tirar dos relatórios (BugReport). */
    fun secrets(): List<String> = listOfNotNull(prefs.getString("refresh", null), prefs.getString("access", null), prefs.getString("client_secret", null))

    fun unlink() = prefs.edit {
        remove("refresh")
        remove("access")
        remove("access_until")
    }

    suspend fun startLogin(): YouTubeLogin = withContext(Dispatchers.IO) {
        val (clientId, _) = client()
        val body = JsonObject().apply {
            addProperty("client_id", clientId)
            addProperty("scope", SCOPE)
            addProperty("device_id", deviceId())
            addProperty("device_model", "ytlr::")
        }
        val res = post("$BASE/o/oauth2/device/code", body)
        YouTubeLogin(
            userCode = res.get("user_code").asString,
            verificationUrl = res.get("verification_url")?.asString ?: "https://www.google.com/device",
            deviceCode = res.get("device_code").asString,
            intervalSec = res.get("interval")?.asInt ?: 5,
            expiresAt = System.currentTimeMillis() + (res.get("expires_in")?.asLong ?: 1800) * 1000,
        )
    }

    /** true: ligada; null: ainda à espera da pessoa; false: o código expirou ou foi recusado. */
    suspend fun poll(login: YouTubeLogin): Boolean? = withContext(Dispatchers.IO) {
        val (clientId, secret) = client()
        val body = JsonObject().apply {
            addProperty("client_id", clientId)
            addProperty("client_secret", secret)
            addProperty("code", login.deviceCode)
            addProperty("grant_type", "http://oauth.net/grant_type/device/1.0")
        }
        val res = post("$BASE/o/oauth2/token", body, allowError = true)
        when {
            res.has("refresh_token") -> {
                saveTokens(res)
                true
            }
            res.get("error")?.asString in setOf("authorization_pending", "slow_down") -> null
            else -> false
        }
    }

    /**
     * Recomendações do Início do YouTube (como na app de TV), até [max], com o pacote da app que as abre.
     * Os vídeos já começados vêm primeiro, com o progresso.
     */
    /**
     * O que aparece no "Continuar a ver" da app do YouTube, conforme [feed] (AccountSettings.YT_*): os vídeos
     * começados e por acabar (do histórico), as recomendações do Início, ou os dois (começados primeiro).
     */
    suspend fun recommendations(packageName: String, feed: String, max: Int = 20): List<WatchNextItem> = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext emptyList()
        val home = if (feed != AccountSettings.YT_CONTINUE) browse("default", token, packageName) else null
        val started = if (feed != AccountSettings.YT_RECOMMENDED) {
            // Do histórico, só os que ficaram a meio; se o histórico vier num formato que não se percebe, os
            // começados do Início servem na mesma.
            browse("FEhistory", token, packageName).filter { (it.progress ?: 0f) in 0.01f..0.95f }
                .ifEmpty { (home ?: browse("default", token, packageName)).filter { (it.progress ?: 0f) in 0.01f..0.95f } }
        } else {
            emptyList()
        }
        val now = System.currentTimeMillis()
        (started + home.orEmpty().filter { feed == AccountSettings.YT_RECOMMENDED || (it.progress ?: 0f) == 0f })
            .distinctBy { it.intentUri }
            .take(max)
            .mapIndexed { i, item ->
                val isStarted = (item.progress ?: 0f) > 0f
                // Sem data de visualização: ficam pela ordem do YouTube, depois do que as outras apps publicaram.
                item.copy(
                    lastEngagement = if (isStarted) now - 60_000L * (i + 1) else i.toLong() * -1,
                    type = if (isStarted) WatchNextItem.TYPE_CONTINUE else WatchNextItem.TYPE_RECOMMENDED,
                )
            }
            .map { async { it.copy(imageUri = wideThumb(it.intentUri.orEmpty().substringAfter("v=")) ?: it.imageUri) } }
            .awaitAll()
    }

    /** Os vídeos de uma página da app de TV do YouTube ("default" = Início, "FEhistory" = histórico). */
    private fun browse(browseId: String, token: String, packageName: String): List<WatchNextItem> {
        val body = JsonObject().apply {
            add("context", JsonObject().apply {
                add("client", JsonObject().apply {
                    addProperty("clientName", "TVHTML5")
                    addProperty("clientVersion", prefs.getString("client_version", null) ?: FALLBACK_VERSION)
                    addProperty("hl", "pt")
                    addProperty("gl", "PT")
                })
            })
            addProperty("browseId", browseId)
        }
        val res = runCatching { post("$BASE/youtubei/v1/browse?prettyPrint=false", body, bearer = token) }
            .onFailure { Log.w(TAG, "$browseId: ${it.message}") }
            .getOrNull() ?: return emptyList()
        val tiles = mutableListOf<JsonObject>()
        collect(res, "tileRenderer", tiles)
        if (tiles.isEmpty()) Log.w(TAG, "$browseId sem tiles: chaves ${res.keySet()}, renderers ${renderers(res)}")
        Log.d(TAG, "$browseId: ${tiles.size} tiles")
        return tiles.mapNotNull { tile(it, packageName) }.distinctBy { it.intentUri }
    }

    /** A miniatura 16:9 em 1280x720; a da API de TV é 4:3 com barras pretas e não enche o destaque. */
    private fun wideThumb(videoId: String): String? {
        val url = "https://i.ytimg.com/vi/$videoId/hq720.jpg"
        val req = Request.Builder().url(url).head().build()
        return runCatching { http.newCall(req).execute().use { if (it.isSuccessful) url else null } }.getOrNull()
    }

    private fun tile(t: JsonObject, packageName: String): WatchNextItem? {
        val videoId = mutableListOf<JsonObject>().also { collect(t, "watchEndpoint", it) }
            .firstNotNullOfOrNull { it.get("videoId").asStringOrNull() } ?: return null
        val meta = t.path("metadata", "tileMetadataRenderer")
        val title = text(meta?.get("title")) ?: return null
        val header = t.path("header", "tileHeaderRenderer")
        val thumbs = header?.path("thumbnail")?.get("thumbnails") as? JsonArray
        val thumb = thumbs?.mapNotNull { it as? JsonObject }?.maxByOrNull { it.get("width")?.asInt ?: 0 }?.get("url")?.asString
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        val overlays = header?.get("thumbnailOverlays") as? JsonArray
        val percent = overlays?.firstNotNullOfOrNull { it.asJsonObject.path("thumbnailOverlayResumePlaybackRenderer")?.get("percentDurationWatched")?.asInt }
        val duration = overlays?.firstNotNullOfOrNull { text(it.asJsonObject.path("thumbnailOverlayTimeStatusRenderer")?.get("text")) }?.let(::parseDuration)
        // A 1.ª linha dos metadados é o canal.
        val lines = meta?.get("lines") as? JsonArray
        val channel = lines?.firstOrNull()?.asJsonObject?.path("lineRenderer")?.get("items")?.let { it as? JsonArray }
            ?.firstOrNull()?.asJsonObject?.path("lineItemRenderer")?.get("text")?.let(::text)
        return WatchNextItem(
            id = videoId.hashCode().toLong(),
            packageName = packageName,
            title = title,
            episodeTitle = channel,
            season = null,
            episode = null,
            imageUri = thumb.substringBefore('?'),
            aspectRatio = 16f / 9f,
            positionMs = if (percent != null && duration != null) duration * percent / 100 else 0,
            durationMs = duration ?: 0,
            type = WatchNextItem.TYPE_CONTINUE,
            lastEngagement = 0,
            intentUri = "https://www.youtube.com/watch?v=$videoId",
        )
    }

    // ---- Tokens e credenciais ----

    private suspend fun accessToken(): String? {
        val refresh = prefs.getString("refresh", null) ?: return null
        val access = prefs.getString("access", null)
        if (access != null && System.currentTimeMillis() < prefs.getLong("access_until", 0)) return access
        val (clientId, secret) = client()
        val body = JsonObject().apply {
            addProperty("client_id", clientId)
            addProperty("client_secret", secret)
            addProperty("refresh_token", refresh)
            addProperty("grant_type", "refresh_token")
        }
        val res = post("$BASE/o/oauth2/token", body, allowError = true)
        if (!res.has("access_token")) {
            // invalid_grant: a pessoa tirou o acesso na conta Google. Fica desligada.
            if (res.get("error")?.asString == "invalid_grant") unlink()
            Log.w(TAG, "Renovar token: ${res.get("error")}")
            return null
        }
        saveTokens(res)
        return res.get("access_token").asString
    }

    private fun saveTokens(res: JsonObject) = prefs.edit {
        res.get("refresh_token")?.asString?.let { putString("refresh", it) }
        putString("access", res.get("access_token").asString)
        putLong("access_until", System.currentTimeMillis() + ((res.get("expires_in")?.asLong ?: 3600) - 120) * 1000)
    }

    /** As credenciais da app de TV do YouTube (lidas de youtube.com/tv, guardadas uma semana). */
    private fun client(): Pair<String, String> {
        val id = prefs.getString("client_id", null)
        val secret = prefs.getString("client_secret", null)
        if (id != null && secret != null && System.currentTimeMillis() - prefs.getLong("client_at", 0) < CLIENT_MS) return id to secret
        val html = http.newCall(Request.Builder().url("$BASE/tv").header("User-Agent", TV_UA).build()).execute().use { it.body.string() }
        val m = CLIENT.find(html) ?: error("Não encontrei as credenciais da app de TV do YouTube")
        val version = Regex("\"clientVersion\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
        prefs.edit {
            putString("client_id", m.groupValues[1])
            putString("client_secret", m.groupValues[2])
            putLong("client_at", System.currentTimeMillis())
            if (version != null) putString("client_version", version)
        }
        return m.groupValues[1] to m.groupValues[2]
    }

    private fun deviceId(): String = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
        prefs.edit { putString("device_id", it) }
    }

    private fun post(url: String, body: JsonObject, bearer: String? = null, allowError: Boolean = false): JsonObject {
        val req = Request.Builder().url(url).header("User-Agent", TV_UA).post(body.toString().toRequestBody(json))
            .apply { if (bearer != null) header("Authorization", "Bearer $bearer") }
            .build()
        return http.newCall(req).execute().use { res ->
            val text = res.body.string()
            if (!res.isSuccessful && !allowError) error("HTTP ${res.code}")
            JsonParser.parseString(text).asJsonObject
        }
    }

    private companion object {
        const val TAG = "YouTube"
        const val BASE = "https://www.youtube.com"
        const val SCOPE = "http://gdata.youtube.com https://www.googleapis.com/auth/youtube-paid-content"
        const val TV_UA = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"
        const val FALLBACK_VERSION = "7.20250101.00.00"
        const val CLIENT_MS = 7 * 24 * 60 * 60_000L
        val CLIENT = Regex("""clientId:"([^"]+\.apps\.googleusercontent\.com)",[A-Za-z_$]{1,3}:"([^"]+)"""")

        fun JsonObject.path(vararg keys: String): JsonObject? {
            var o: JsonObject? = this
            for (k in keys) o = o?.get(k)?.takeIf { it.isJsonObject }?.asJsonObject
            return o
        }

        fun JsonElement?.asStringOrNull() = this?.takeIf { it.isJsonPrimitive }?.asString

        /** Texto de um campo do YouTube: {"simpleText": …} ou {"runs": [{"text": …}, …]}. */
        fun text(e: JsonElement?): String? {
            val o = e?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            o.get("simpleText").asStringOrNull()?.let { return it }
            val runs = o.get("runs") as? JsonArray ?: return null
            return runs.joinToString("") { it.asJsonObject.get("text").asStringOrNull().orEmpty() }.takeIf { it.isNotBlank() }
        }

        /** "1:02:03" ou "12:34" → ms. */
        fun parseDuration(s: String): Long? {
            val parts = s.trim().split(':').map { it.toLongOrNull() ?: return null }
            return parts.fold(0L) { acc, p -> acc * 60 + p } * 1000
        }

        /** Os tipos de objeto mais comuns na resposta (para perceber o formato quando não há tiles). */
        fun renderers(e: JsonElement): List<Pair<String, Int>> {
            val counts = HashMap<String, Int>()
            fun walk(x: JsonElement) {
                when {
                    x.isJsonObject -> x.asJsonObject.entrySet().forEach { (k, v) ->
                        if (k.endsWith("Renderer") || k.endsWith("ViewModel") || k.endsWith("Endpoint")) counts[k] = (counts[k] ?: 0) + 1
                        walk(v)
                    }
                    x.isJsonArray -> x.asJsonArray.forEach(::walk)
                }
            }
            walk(e)
            return counts.entries.sortedByDescending { it.value }.take(20).map { it.key to it.value }
        }

        /** Junta todos os objetos [key] em qualquer profundidade. */
        fun collect(e: JsonElement, key: String, out: MutableList<JsonObject>) {
            when {
                e.isJsonObject -> e.asJsonObject.entrySet().forEach { (k, v) ->
                    if (k == key && v.isJsonObject) out += v.asJsonObject else collect(v, key, out)
                }
                e.isJsonArray -> e.asJsonArray.forEach { collect(it, key, out) }
            }
        }
    }
}
