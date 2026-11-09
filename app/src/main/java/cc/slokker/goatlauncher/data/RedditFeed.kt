package cc.slokker.goatlauncher.data

import android.util.Xml
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * Fotos de um subreddit (como no Projectivy). Lê o feed RSS do "top da semana": o JSON do Reddit pede conta, o RSS
 * não. O Reddit limita pedidos seguidos, por isso a lista só se volta a pedir a cada [refreshMs] (ver [PhotoPool]).
 */
class RedditFeed(private val http: OkHttpClient) : PhotoFeed {
    override val name = "r/"
    override val refreshMs = 6 * 60 * 60_000L

    override fun fetch(id: String): List<OnlinePhoto> {
        val request = Request.Builder()
            .url("https://www.reddit.com/r/$id/top/.rss?t=week&limit=50")
            // O Reddit recusa identificações de browser genéricas; pede uma da app.
            .header("User-Agent", USER_AGENT)
            .build()
        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body.string()
        }
        return parse(body)
    }

    /** Entradas do feed Atom: o título e a imagem (i.redd.it / imgur) que vem no HTML do conteúdo. */
    private fun parse(xml: String): List<OnlinePhoto> {
        val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
        val out = mutableListOf<OnlinePhoto>()
        var inEntry = false
        var title = ""
        var content = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "entry" -> {
                        inEntry = true
                        title = ""
                        content = ""
                    }
                    "title" -> if (inEntry) title = parser.nextText()
                    "content" -> if (inEntry) content = parser.nextText()
                }
                XmlPullParser.END_TAG -> if (parser.name == "entry") {
                    inEntry = false
                    val url = IMAGE_LINK.find(content)?.value
                    if (url != null && landscapeOrUnknown(title)) out += OnlinePhoto(url, title)
                }
            }
        }
        return out.distinctBy { it.url }
    }

    /** Muitos subreddits de fotos põem a resolução no título ("[4032x3024]"): fora as fotos ao alto e pequenas. */
    private fun landscapeOrUnknown(title: String): Boolean {
        val m = RESOLUTION.find(title) ?: return true
        val w = m.groupValues[1].toInt()
        val h = m.groupValues[2].toInt()
        return w > h && w >= 1600
    }

    companion object {
        private const val USER_AGENT = "android:cc.slokker.goatlauncher:0.1 (Android TV launcher)"

        private val IMAGE_LINK = Regex("""https://(?:i\.redd\.it|i\.imgur\.com)/[A-Za-z0-9_-]+\.(?:jpe?g|png|webp)""")
        private val RESOLUTION = Regex("""[\[(]\s*(\d{3,5})\s*[x×X]\s*(\d{3,5})\s*[\])]""")

        /** Subreddits de fotos sugeridos nas definições (o resto escreve-se à mão). */
        val SUGGESTED = listOf("EarthPorn", "SkyPorn", "CityPorn", "spaceporn", "wallpaper", "MinimalWallpaper")
    }
}
