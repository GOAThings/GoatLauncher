package cc.slokker.goatlauncher.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * A "Imagem do dia" do Wikimedia Commons (as páginas `Template:Potd/AAAA-MM-DD`) dos últimos [POOL_DAYS] dias, num
 * só pedido à API do Commons, sem chave. Cada foto traz o autor e a licença (quase todas CC BY-SA ou CC BY: a
 * atribuição é obrigatória, por isso vai na legenda). O Wikimedia pede uma identificação da app com um contacto
 * ([userAgent]); sem ela, recusa (403) até as próprias imagens.
 */
class WikimediaFeed(private val http: OkHttpClient, private val userAgent: String) : PhotoFeed {
    override val name = "Wikimedia"

    /** A imagem do dia muda uma vez por dia: duas leituras por dia chegam. */
    override val refreshMs = 12 * 60 * 60_000L

    override fun fetch(id: String): List<OnlinePhoto> {
        val request = Request.Builder()
            .url(apiUrl(System.currentTimeMillis(), Locale.getDefault().language))
            .header("User-Agent", userAgent)
            .build()
        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw FeedHttpException(response.code)
            response.body.string()
        }
        return parse(body)
    }

    companion object {
        /** O [Wallpaper.value] deste fundo (só há uma lista). */
        const val ID = "potd"
        const val POOL_DAYS = 45

        /** Largura pedida ao Commons: a de um ecrã 4K (as originais chegam aos 8000+ px e a 30 MB). */
        const val WIDTH = 3840
        private const val API = "https://commons.wikimedia.org/w/api.php"
        private const val MIN_WIDTH = 1600

        /** Mais largas do que isto (panorâmicas) perdiam quase tudo ao encher o ecrã. */
        private const val MAX_ASPECT = 2.6
        private val PHOTO_MIMES = setOf("image/jpeg", "image/png", "image/webp", "image/tiff")

        /**
         * O pedido: as imagens usadas nas páginas da imagem do dia de cada um dos últimos [POOL_DAYS] dias (em UTC,
         * como no Commons), com o tamanho, o endereço em [WIDTH] px e o autor, a licença e a descrição em [language].
         */
        fun apiUrl(nowMs: Long, language: String): HttpUrl {
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val titles = (0 until POOL_DAYS).joinToString("|") { "Template:Potd/" + day.format(nowMs - it * 86_400_000L) }
            return API.toHttpUrl().newBuilder()
                .addQueryParameter("action", "query")
                .addQueryParameter("format", "json")
                .addQueryParameter("formatversion", "2")
                .addQueryParameter("generator", "images")
                .addQueryParameter("gimlimit", "max")
                .addQueryParameter("titles", titles)
                .addQueryParameter("prop", "imageinfo")
                .addQueryParameter("iiprop", "url|size|mime|extmetadata")
                .addQueryParameter("iiurlwidth", WIDTH.toString())
                .addQueryParameter("iiextmetadatafilter", "Artist|LicenseShortName|ObjectName|ImageDescription")
                .addQueryParameter("iiextmetadatalanguage", language.ifBlank { "en" })
                .build()
        }

        /**
         * As fotos da resposta. Ficam de fora os ficheiros sem `imageinfo` (apagados ou com outro nome), os que não
         * são fotos (SVG, vídeo, som) e as fotos ao alto, pequenas ou panorâmicas.
         */
        fun parse(json: String): List<OnlinePhoto> {
            val pages = JsonParser.parseString(json).asJsonObject.obj("query")?.get("pages")
                ?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
            return pages.mapNotNull { e ->
                val page = e.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val info = page.get("imageinfo")?.takeIf { it.isJsonArray }?.asJsonArray?.firstOrNull()
                    ?.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                if (info.str("mime") !in PHOTO_MIMES) return@mapNotNull null
                val w = info.int("width") ?: return@mapNotNull null
                val h = info.int("height") ?: return@mapNotNull null
                if (w <= h || w < MIN_WIDTH || w > h * MAX_ASPECT) return@mapNotNull null
                // A miniatura em WIDTH px (numa original mais pequena, o Commons devolve a original).
                val url = (info.str("thumburl") ?: info.str("url"))?.substringBefore('?') ?: return@mapNotNull null
                val meta = info.obj("extmetadata")
                val description = meta?.meta("ImageDescription")?.let(::plainText)?.let(::firstSentence)
                val title = description?.takeIf { it.isNotBlank() }
                    ?: meta?.meta("ObjectName")?.let(::plainText)?.takeIf { it.isNotBlank() }
                    ?: page.str("title").orEmpty().removePrefix("File:").substringBeforeLast('.')
                val artist = meta?.meta("Artist")?.let(::plainText)?.takeIf { it.isNotBlank() }?.let { shorten(it, 60) }
                val license = meta?.meta("LicenseShortName")?.let(::plainText)?.takeIf { it.isNotBlank() }
                OnlinePhoto(url, shorten(title, 140), listOfNotNull(artist, license).joinToString(" · ").ifEmpty { null })
            }.distinctBy { it.url }
        }

        /** A primeira frase de uma descrição (muitas continuam com a história do sítio). */
        private fun firstSentence(text: String): String {
            // Um ponto depois de duas minúsculas, de um número ou de ")": "U.S. Post Office" não acaba a frase.
            val end = Regex("""(?<=\p{Ll}{2}|\d|\))[.!?](?=\s|$)""").find(text, 20)?.range?.first ?: return text
            return text.substring(0, end + 1).removeSuffix(".")
        }

        private fun JsonObject.obj(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
        private fun JsonObject.str(name: String): String? =
            get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        private fun JsonObject.int(name: String): Int? =
            get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt

        /** Um campo de `extmetadata`: `{"Artist": {"value": "…"}}`. */
        private fun JsonObject.meta(name: String): String? = obj(name)?.str("value")
    }
}
