package cc.slokker.goatlauncher.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

/**
 * A "Astronomy Picture of the Day" da NASA, dos últimos 30 dias, do feed RSS oficial, sem chave. O APOD passou do
 * apod.nasa.gov para o science.nasa.gov: o antigo `apod.rss` redireciona para uma página HTML e a API em
 * api.nasa.gov devolve o logótipo da NASA em vez da foto. Os dias de vídeo ficam de fora. Muitas fotos têm direitos
 * do fotógrafo: o crédito vai sempre na legenda.
 */
class ApodFeed(private val http: OkHttpClient, private val userAgent: String) : PhotoFeed {
    override val name = "NASA APOD"

    /** Uma foto nova por dia: duas leituras por dia chegam. */
    override val refreshMs = 12 * 60 * 60_000L

    override fun fetch(id: String): List<OnlinePhoto> {
        val request = Request.Builder().url(FEED_URL).header("User-Agent", userAgent).build()
        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw FeedHttpException(response.code)
            response.body.string()
        }
        return parse(body)
    }

    companion object {
        /** O [Wallpaper.value] deste fundo (só há uma lista). */
        const val ID = "apod"
        const val FEED_URL = "https://science.nasa.gov/feed/apod-basic/"

        /** Largura máxima pedida ao servidor de imagens da NASA (as originais chegam aos 7000+ px). */
        const val WIDTH = 3840
        private const val MIN_WIDTH = 1600
        private val VIDEO = Regex("""<(?:video|iframe|embed|object)\b""", RegexOption.IGNORE_CASE)
        private val PHOTO = Regex("""\.(?:jpe?g|png|webp)$""", RegexOption.IGNORE_CASE)
        private val CREDIT_LABEL = Regex("""^(?:image\s+)?credit(?:\s*(?:&|and)\s*copyright)?\s*:\s*""", RegexOption.IGNORE_CASE)

        /**
         * As fotos do feed: o título, a imagem (`apod:hdurl`) e o crédito (`apod:copyright`, ou `apod:credit`).
         * Ficam de fora os dias de vídeo (o HTML do dia traz um `<video>` ou um `<iframe>`; a imagem é só a capa) e
         * as imagens ao alto ou pequenas, quando o endereço diz o tamanho (`?w=3000&h=1990`).
         */
        fun parse(xml: String): List<OnlinePhoto> {
            val items = mutableListOf<Map<String, String>>()
            val handler = object : DefaultHandler() {
                private var item: MutableMap<String, String>? = null
                private val text = StringBuilder()

                override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
                    if (qName == "item") item = mutableMapOf()
                    text.setLength(0)
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (item != null) text.append(ch, start, length)
                }

                override fun endElement(uri: String?, localName: String?, qName: String) {
                    val current = item ?: return
                    if (qName == "item") {
                        items += current
                        item = null
                    } else if (qName in FIELDS) {
                        current[qName] = text.toString()
                    }
                    text.setLength(0)
                }
            }
            val factory = SAXParserFactory.newInstance()
            // Um feed nunca traz DTD: recusá-la evita entidades externas (XXE). Nem todos os parsers a conhecem.
            runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            factory.newSAXParser().parse(InputSource(StringReader(xml)), handler)
            return items.mapNotNull(::photo).distinctBy { it.url }
        }

        private val FIELDS = setOf("title", "apod:hdurl", "apod:credit", "apod:copyright", "content:encoded")

        private fun photo(item: Map<String, String>): OnlinePhoto? {
            if (VIDEO.containsMatchIn(item["content:encoded"].orEmpty())) return null
            val hd = item["apod:hdurl"]?.trim()?.toHttpUrlOrNull() ?: return null
            val base = hd.newBuilder().query(null).build().toString()
            if (!PHOTO.containsMatchIn(base)) return null
            val w = hd.queryParameter("w")?.toIntOrNull() ?: 0
            val h = hd.queryParameter("h")?.toIntOrNull() ?: 0
            if (w > 0 && h > 0 && (w <= h || w < MIN_WIDTH)) return null
            // O servidor de imagens da NASA redimensiona (e também amplia: as pequenas pedem-se sem tamanho).
            val url = if (w > WIDTH) "$base?w=$WIDTH&fit=clip" else base
            val title = plainText(item["title"].orEmpty())
            val credit = (item["apod:copyright"]?.takeIf { it.isNotBlank() } ?: item["apod:credit"].orEmpty())
                .let(::plainText).replace(CREDIT_LABEL, "").trim()
            return OnlinePhoto(url, title, shorten(credit, 90).ifBlank { "NASA APOD" })
        }
    }
}
