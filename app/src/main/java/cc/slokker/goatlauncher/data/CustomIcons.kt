package cc.slokker.goatlauncher.data

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File

/** Um icon pack instalado (formato ADW/Nova: `appfilter.xml` e `drawable.xml`), como os que o Projectivy usa. */
@Immutable
data class IconPack(val packageName: String, val label: String)

/** Os ícones de um pack: os feitos para a app em causa primeiro ([suggested]) e a lista completa ([all]). */
@Immutable
data class PackIcons(val suggested: List<String>, val all: List<String>)

/**
 * Ícones escolhidos pela pessoa. Cada um é guardado nas definições desta TV como um texto:
 *  - `pack:<pacote do pack>/<drawable>`: um ícone de um icon pack (lido do pack sempre que é preciso);
 *  - `file:<nome>`: uma imagem da box, copiada para a app (sobrevive a tirar o disco ou a reiniciar).
 */
class CustomIcons(private val context: Context) {
    private val pm = context.packageManager
    private val dir = File(context.filesDir, "icons")

    /** Ícones já desenhados, por spec (os da grelha do pack também passam por aqui). */
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun iconPacks(): List<IconPack> =
        PACK_ACTIONS
            .flatMap { action -> runCatching { pm.queryIntentActivities(Intent(action), 0) }.getOrElse { emptyList() } }
            .distinctBy { it.activityInfo.packageName }
            .map { IconPack(it.activityInfo.packageName, it.activityInfo.applicationInfo.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }

    /** Lê o `appfilter.xml` (o mapa app → ícone) e o `drawable.xml` (a lista de ícones) do pack. */
    suspend fun packIcons(pack: String, forPackage: String): PackIcons = withContext(Dispatchers.IO) {
        runCatching {
            val res = pm.getResourcesForApplication(pack)
            val suggested = LinkedHashSet<String>()
            val mapped = LinkedHashSet<String>()
            readXml(res, pack, "appfilter") { p ->
                if (p.name == "item") {
                    val drawable = p.getAttributeValue(null, "drawable") ?: return@readXml
                    mapped += drawable
                    val component = p.getAttributeValue(null, "component").orEmpty()
                    if (component.startsWith("ComponentInfo{$forPackage/")) suggested += drawable
                }
            }
            val listed = LinkedHashSet<String>()
            readXml(res, pack, "drawable") { p ->
                if (p.name == "item") p.getAttributeValue(null, "drawable")?.let { listed += it }
            }
            val all = (listed.ifEmpty { mapped }).filter { res.getIdentifier(it, "drawable", pack) != 0 }
            PackIcons(suggested.filter { res.getIdentifier(it, "drawable", pack) != 0 }, all)
        }.getOrElse {
            Log.w(TAG, "Ler o icon pack $pack", it)
            PackIcons(emptyList(), emptyList())
        }
    }

    /** O desenho de um spec (`pack:` ou `file:`), ou null se já não existir (pack desinstalado, ficheiro apagado). */
    suspend fun load(spec: String): ImageBitmap? {
        cache.get(spec)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                when {
                    spec.startsWith(PACK) -> {
                        val (pack, name) = spec.removePrefix(PACK).split('/', limit = 2)
                        val res = pm.getResourcesForApplication(pack)
                        val id = res.getIdentifier(name, "drawable", pack).takeIf { it != 0 } ?: return@runCatching null
                        val d = ResourcesCompat.getDrawable(res, id, null) ?: return@runCatching null
                        val w = d.intrinsicWidth.takeIf { it > 0 } ?: MAX_W
                        val h = d.intrinsicHeight.takeIf { it > 0 } ?: MAX_H
                        val scale = minOf(MAX_W.toFloat() / w, MAX_H.toFloat() / h, 2f)
                        d.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                    }
                    spec.startsWith(FILE) -> BitmapFactory.decodeFile(File(dir, spec.removePrefix(FILE)).path)
                    else -> null
                }?.asImageBitmap()
            }.onFailure { Log.w(TAG, "Ícone $spec", it) }.getOrNull()?.also { cache.put(spec, it) }
        }
    }

    /** Guarda [bitmap] (ex.: o que o icon pack devolveu, ou uma imagem da box) como ícone de [forPackage]. */
    suspend fun saveBitmap(forPackage: String, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        dir.mkdirs()
        // Nome novo a cada escolha: o Compose e a cache não confundem a imagem antiga com a nova.
        val name = "$forPackage-${System.currentTimeMillis()}.png"
        File(dir, name).outputStream().use { ImageImport.fit(bitmap, MAX_W, MAX_H).compress(Bitmap.CompressFormat.PNG, 100, it) }
        FILE + name
    }

    /** Apaga a cópia de um ícone `file:` que deixou de ser usado. */
    fun discard(spec: String?) {
        if (spec == null || !spec.startsWith(FILE)) return
        cache.remove(spec)
        runCatching { File(dir, spec.removePrefix(FILE)).delete() }
    }

    private fun readXml(res: Resources, pack: String, name: String, onTag: (XmlPullParser) -> Unit) {
        val parser: XmlPullParser? = res.getIdentifier(name, "xml", pack).takeIf { it != 0 }?.let { res.getXml(it) }
            ?: runCatching {
                XmlPullParserFactory.newInstance().newPullParser().apply { setInput(res.assets.open("$name.xml"), null) }
            }.getOrNull()
        parser ?: return
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) onTag(parser)
        }
    }

    companion object {
        private const val TAG = "CustomIcons"
        const val PACK = "pack:"
        const val FILE = "file:"

        /** Ação para abrir o seletor do próprio pack (devolve um Bitmap em "icon" ou um ShortcutIconResource). */
        const val ACTION_PICK_ICON = "org.adw.launcher.icons.ACTION_PICK_ICON"

        private val PACK_ACTIONS = listOf(ACTION_PICK_ICON, "org.adw.launcher.THEMES", "com.novalauncher.THEME")

        // Banners de TV (16:9) a 2× de 320×180; ícones quadrados ficam dentro do mesmo limite.
        private const val MAX_W = 640
        private const val MAX_H = 360
    }
}
