package cc.slokker.goatlauncher.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.util.Xml
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/** Uma imagem de um subreddit para o fundo. */
data class RedditImage(val url: String, val title: String)

/**
 * Fundos a partir de um subreddit (como no Projectivy). Lê o feed RSS do "top da semana": o JSON do Reddit
 * pede conta, o RSS não. O Reddit limita pedidos seguidos, por isso a lista só se volta a pedir a cada
 * [REFRESH_MS] e fica guardada (o fundo funciona sem rede com a última imagem, que o Coil guarda em disco).
 */
class RedditWallpapers(private val context: Context, private val http: OkHttpClient) {
    private val prefs = context.getSharedPreferences("reddit_wallpapers", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<RedditImage>>() {}.type

    /**
     * A imagem a mostrar para [subreddit]: a mesma até passar [ROTATE_MS] (ou [next] = true), depois outra ao acaso.
     * [skip] são URLs a não usar (ex.: imagens ao alto, que se descobrem ao abrir).
     */
    suspend fun current(subreddit: String, next: Boolean = false, skip: Set<String> = emptySet()): RedditImage? =
        withContext(Dispatchers.IO) {
            val sub = subreddit.lowercase()
            val list = images(sub).filterNot { it.url in skip }
            if (list.isEmpty()) return@withContext null
            val shownUrl = prefs.getString(key(sub, "shown_url"), null)
            val shownAt = prefs.getLong(key(sub, "shown_at"), 0)
            val shown = list.firstOrNull { it.url == shownUrl }
            val due = next || shown == null || System.currentTimeMillis() - shownAt > ROTATE_MS
            // Sem rede não se troca: a imagem nova não estaria na cache e o fundo ficava vazio.
            if (!due || (shown != null && !online())) return@withContext shown
            val pick = (list - setOfNotNull(shown)).randomOrNull() ?: shown
            prefs.edit {
                putString(key(sub, "shown_url"), pick?.url)
                putLong(key(sub, "shown_at"), System.currentTimeMillis())
            }
            pick
        }

    /** A última imagem mostrada de [subreddit] (ao abrir o launcher aparece logo, já está na cache). */
    suspend fun lastShown(subreddit: String): RedditImage? = withContext(Dispatchers.IO) {
        val sub = subreddit.lowercase()
        val url = prefs.getString(key(sub, "shown_url"), null) ?: return@withContext null
        images(sub, allowFetch = false).firstOrNull { it.url == url } ?: RedditImage(url, "")
    }

    /** Milissegundos até a imagem atual de [subreddit] dever mudar. */
    fun msUntilRotation(subreddit: String): Long {
        val shownAt = prefs.getLong(key(subreddit.lowercase(), "shown_at"), 0)
        return (shownAt + ROTATE_MS - System.currentTimeMillis()).coerceIn(MIN_WAIT_MS, ROTATE_MS)
    }

    private fun images(sub: String, allowFetch: Boolean = true): List<RedditImage> {
        val cached: List<RedditImage> = prefs.getString(key(sub, "list"), null)
            ?.let { runCatching { gson.fromJson<List<RedditImage>>(it, listType) }.getOrNull() }
            .orEmpty()
        val fetchedAt = prefs.getLong(key(sub, "fetched_at"), 0)
        if (cached.isNotEmpty() && System.currentTimeMillis() - fetchedAt < REFRESH_MS) return cached
        if (!allowFetch || !online()) return cached
        val fresh = runCatching { fetch(sub) }
            .onFailure { Log.w(TAG, "r/$sub: ${it.message}") }
            .getOrNull()
        if (fresh.isNullOrEmpty()) {
            // Falhou (ou o Reddit limitou): tenta de novo mais tarde, sem martelar.
            prefs.edit { putLong(key(sub, "fetched_at"), System.currentTimeMillis() - REFRESH_MS + RETRY_MS) }
            return cached
        }
        prefs.edit {
            putString(key(sub, "list"), gson.toJson(fresh))
            putLong(key(sub, "fetched_at"), System.currentTimeMillis())
        }
        return fresh
    }

    private fun fetch(sub: String): List<RedditImage> {
        val request = Request.Builder()
            .url("https://www.reddit.com/r/$sub/top/.rss?t=week&limit=50")
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
    private fun parse(xml: String): List<RedditImage> {
        val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
        val out = mutableListOf<RedditImage>()
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
                    if (url != null && landscapeOrUnknown(title)) out += RedditImage(url, title)
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

    private fun online(): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    private fun key(sub: String, name: String) = "$sub.$name"

    companion object {
        private const val TAG = "RedditWallpapers"
        private const val USER_AGENT = "android:cc.slokker.goatlauncher:0.1 (Android TV launcher)"
        private const val REFRESH_MS = 6 * 60 * 60_000L
        private const val RETRY_MS = 15 * 60_000L
        const val ROTATE_MS = 30 * 60_000L
        private const val MIN_WAIT_MS = 60_000L

        private val IMAGE_LINK = Regex("""https://(?:i\.redd\.it|i\.imgur\.com)/[A-Za-z0-9_-]+\.(?:jpe?g|png|webp)""")
        private val RESOLUTION = Regex("""[\[(]\s*(\d{3,5})\s*[x×X]\s*(\d{3,5})\s*[\])]""")

        /** Subreddits de fotos sugeridos nas definições (o resto escreve-se à mão). */
        val SUGGESTED = listOf("EarthPorn", "SkyPorn", "CityPorn", "spaceporn", "wallpaper", "MinimalWallpaper")
    }
}
