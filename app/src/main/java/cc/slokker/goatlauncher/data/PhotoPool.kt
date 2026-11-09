package cc.slokker.goatlauncher.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.edit
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Uma foto online para o fundo. [credit]: o autor (e a licença), para a legenda do modo ambiente. */
data class OnlinePhoto(val url: String, val title: String, val credit: String? = null)

/** De onde vêm as fotos de um [PhotoPool]: um pedido que devolve a lista, ou lança se falhar. */
interface PhotoFeed {
    /** Nome para o registo (logcat). */
    val name: String

    /** De quanto em quanto tempo se volta a pedir a lista. */
    val refreshMs: Long

    /** As fotos de [id] (cada fonte tem um só, ver OnlineWallpapers), já sem as que não servem de fundo (ao alto, vídeos…). */
    fun fetch(id: String): List<OnlinePhoto>
}

/** A fonte respondeu com o código HTTP [code] (ver [PhotoPool]). */
class FeedHttpException(val code: Int) : Exception("HTTP $code")

/** Texto simples a partir do HTML que as fontes põem nos créditos e descrições: sem etiquetas nem entidades. */
internal fun plainText(html: String): String = html
    .replace(Regex("""<br\s*/?>|</(?:p|div|li)>""", RegexOption.IGNORE_CASE), " ")
    .replace(Regex("<[^>]*>"), "")
    .replace(Regex("""&#(\d+);""")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: "" }
    .replace(Regex("""&#x([0-9a-fA-F]+);""")) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: "" }
    .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")
    .replace("&amp;", "&")
    .replace(' ', ' ')
    .replace(Regex("""\s+"""), " ")
    // As ligações deixam espaços antes da pontuação ("Orion , Spain", "( Ardea herodias )").
    .replace(Regex("""\s+([,.;:)])"""), "$1")
    .replace(Regex("""\(\s+"""), "(")
    .trim()

/** [text] com no máximo [max] caracteres, cortado numa palavra e com "…". */
internal fun shorten(text: String, max: Int): String {
    if (text.length <= max) return text
    val cut = text.take(max).substringBeforeLast(' ').ifBlank { text.take(max) }
    return cut.trimEnd(',', ';', ':', ' ', '-', '·') + "…"
}

/**
 * Fotos para o fundo a partir de uma fonte online ([feed]), uma de cada vez: a mesma até passar o intervalo, depois
 * outra ao acaso. A lista fica guardada em [prefsName] e só se volta a pedir a cada [PhotoFeed.refreshMs] (o fundo
 * funciona sem rede com a última imagem, que o Coil guarda em disco). Cada id tem a sua lista e a sua foto atual.
 */
class PhotoPool(private val context: Context, prefsName: String, private val feed: PhotoFeed) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /**
     * A foto a mostrar para [id]: a mesma até passar [rotateMs] (ou [next] = true), depois outra ao acaso.
     * [skip] são URLs a não usar (ex.: imagens ao alto, que se descobrem ao abrir).
     */
    suspend fun current(id: String, rotateMs: Long, next: Boolean = false, skip: Set<String> = emptySet()): OnlinePhoto? =
        withContext(Dispatchers.IO) {
            val sub = id.lowercase()
            val list = images(sub).filterNot { it.url in skip }
            if (list.isEmpty()) return@withContext null
            val shownUrl = prefs.getString(key(sub, "shown_url"), null)
            val shownAt = prefs.getLong(key(sub, "shown_at"), 0)
            val shown = list.firstOrNull { it.url == shownUrl }
            val due = next || shown == null || System.currentTimeMillis() - shownAt > rotateMs
            // Sem rede não se troca: a imagem nova não estaria na cache e o fundo ficava vazio.
            if (!due || (shown != null && !online())) return@withContext shown
            val pick = (list - setOfNotNull(shown)).randomOrNull() ?: shown
            prefs.edit {
                putString(key(sub, "shown_url"), pick?.url)
                putLong(key(sub, "shown_at"), System.currentTimeMillis())
            }
            pick
        }

    /** A última foto mostrada de [id] (ao abrir o launcher aparece logo, já está na cache). */
    suspend fun lastShown(id: String): OnlinePhoto? = withContext(Dispatchers.IO) {
        val sub = id.lowercase()
        val url = prefs.getString(key(sub, "shown_url"), null) ?: return@withContext null
        images(sub, allowFetch = false).firstOrNull { it.url == url } ?: OnlinePhoto(url, "")
    }

    /** Milissegundos até a foto atual de [id] dever mudar. */
    fun msUntilRotation(id: String, rotateMs: Long): Long {
        val shownAt = prefs.getLong(key(id.lowercase(), "shown_at"), 0)
        return (shownAt + rotateMs - System.currentTimeMillis()).coerceIn(MIN_WAIT_MS, rotateMs)
    }

    private fun images(sub: String, allowFetch: Boolean = true): List<OnlinePhoto> {
        val cached = readList(prefs.getString(key(sub, "list"), null))
        // Depois de uma falha, fetched_at fica a RETRY_MS do pedido seguinte, também sem lista: antes, uma fonte
        // que falhava sem lista guardada era pedida de novo a cada tentativa de escolher uma foto (até 10 seguidas).
        val fetchedAt = prefs.getLong(key(sub, "fetched_at"), 0)
        if (System.currentTimeMillis() - fetchedAt < feed.refreshMs) return cached
        if (!allowFetch || !online()) return cached
        val fresh = runCatching { feed.fetch(sub) }
            .onFailure { Log.w(TAG, "${feed.name} $sub: ${it.message}") }
            .getOrNull()
        if (fresh.isNullOrEmpty()) {
            // Falhou (ou a fonte limitou): tenta de novo mais tarde, sem martelar.
            prefs.edit { putLong(key(sub, "fetched_at"), System.currentTimeMillis() - feed.refreshMs + RETRY_MS) }
            return cached
        }
        prefs.edit {
            putString(key(sub, "list"), writeList(fresh))
            putLong(key(sub, "fetched_at"), System.currentTimeMillis())
        }
        return fresh
    }

    private fun online(): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    private fun key(sub: String, name: String) = "$sub.$name"

    companion object {
        private const val TAG = "PhotoPool"
        private const val RETRY_MS = 15 * 60_000L
        private const val MIN_WAIT_MS = 60_000L

        /**
         * A lista guardada: `[{"url", "title", "credit"?}]`.
         * Uma lista que não se leia conta como vazia (é só uma cache: volta a pedir-se).
         */
        internal fun readList(json: String?): List<OnlinePhoto> = runCatching {
            JsonParser.parseString(json ?: return emptyList()).asJsonArray.mapNotNull { e ->
                val o = e.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val url = o.get("url")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                OnlinePhoto(
                    url,
                    o.get("title")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    o.get("credit")?.takeIf { it.isJsonPrimitive }?.asString,
                )
            }
        }.getOrDefault(emptyList())

        internal fun writeList(list: List<OnlinePhoto>): String = JsonArray().apply {
            list.forEach { p ->
                add(JsonObject().apply {
                    addProperty("url", p.url)
                    addProperty("title", p.title)
                    p.credit?.let { addProperty("credit", it) }
                })
            }
        }.toString()
    }
}
