package cc.slokker.goatlauncher.data.search

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.net.Uri
import android.os.CancellationSignal
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser

/**
 * A pesquisa do launcher nas outras apps: que apps servem ([targets]), procurar numa ([search]) e abrir um resultado
 * ou passar-lhe o texto ([open], [handoff]). As regras (ordem, XML, colunas, Cinemeta) estão no [AppSearch].
 *
 * Cada pedido a outra app ou à rede tem um prazo ([TIMEOUT_MS]) e qualquer erro conta como "sem resultados": o
 * fornecedor pode não existir, estar protegido (SecurityException) ou ser lento.
 */
class SearchApps(private val context: Context, private val http: OkHttpClient, private val userAgent: String) {
    private val pm get() = context.packageManager

    /** As apps em que se pode procurar, pela ordem das Definições (ver [AppSearch.classify]). Lê o PackageManager. */
    suspend fun targets(): List<SearchTarget> = withContext(Dispatchers.IO) {
        runCatching { AppSearch.classify(searchActivities(), catalogApps(), context.packageName) }
            .onFailure { Log.w(TAG, "Apps com pesquisa", it) }
            .getOrDefault(emptyList())
    }

    /** As atividades que tratam ACTION_SEARCH, cada uma com o `searchable` dela (se tiver sugestões). */
    private fun searchActivities(): List<SearchActivity> =
        pm.queryIntentActivities(Intent(Intent.ACTION_SEARCH), PackageManager.GET_META_DATA).mapNotNull { info ->
            val a = info.activityInfo ?: return@mapNotNull null
            if (!a.exported || !a.enabled) return@mapNotNull null
            SearchActivity(a.packageName, a.name, runCatching { searchableOf(a) }.getOrNull())
        }

    /** Lê o `android.app.searchable` da atividade: os atributos, com as referências @string já resolvidas. */
    private fun searchableOf(a: ActivityInfo): Searchable? {
        val resId = a.metaData?.getInt(SEARCHABLE_META) ?: return null
        if (resId == 0) return null
        val res = pm.getResourcesForApplication(a.applicationInfo)
        val xml = res.getXml(resId)
        try {
            while (true) {
                when (xml.next()) {
                    XmlPullParser.END_DOCUMENT -> return null
                    XmlPullParser.START_TAG -> if (xml.name == "searchable") {
                        return AppSearch.searchable { name -> attribute(xml, res, name) }
                    }
                }
            }
        } finally {
            xml.close()
        }
    }

    private fun attribute(xml: XmlResourceParser, res: android.content.res.Resources, name: String): String? {
        val ref = xml.getAttributeResourceValue(ANDROID_NS, name, 0)
        if (ref != 0) return runCatching { res.getString(ref) }.getOrNull()
        return xml.getAttributeValue(ANDROID_NS, name)
    }

    /** O Nuvio (qualquer pacote que abra `nuvio://`) e o Stremio, se estiverem instalados. */
    private fun catalogApps(): List<Pair<String, CatalogApp>> {
        val nuvio = pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse(CatalogApp.NUVIO.link("movie", "tt0"))), 0)
            .map { it.activityInfo.packageName to CatalogApp.NUVIO }
        val stremio = listOfNotNull(
            (CatalogApp.STREMIO_PACKAGE to CatalogApp.STREMIO)
                .takeIf { runCatching { pm.getPackageInfo(CatalogApp.STREMIO_PACKAGE, 0) }.isSuccess },
        )
        return nuvio + stremio
    }

    /** Procura [query] em [target]. Erros e demoras dão uma lista vazia ([failed] diz se falhou). */
    suspend fun search(target: SearchTarget, query: String): Results = try {
        withTimeout(TIMEOUT_MS) {
            when (target) {
                is SearchTarget.Provider -> Results(suggestions(target, query))
                is SearchTarget.Catalog -> Results(cinemeta(target, query))
                is SearchTarget.Handoff -> Results(emptyList())
            }
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        // O prazo acabou (TimeoutCancellationException) conta como falha; uma pesquisa nova cancela esta (passa).
        if (e is kotlinx.coroutines.TimeoutCancellationException) Results(emptyList(), failed = true) else throw e
    } catch (e: Exception) {
        Log.w(TAG, "Pesquisa em ${target.pkg}: ${e.javaClass.simpleName}")
        Results(emptyList(), failed = true)
    }

    class Results(val items: List<SearchResult>, val failed: Boolean = false)

    private suspend fun suggestions(target: SearchTarget.Provider, query: String): List<SearchResult> {
        val s = target.searchable
        val uri = Uri.parse(AppSearch.suggestUri(s, query))
        val args = if (s.selection != null) arrayOf(query) else null
        val cancel = CancellationSignal()
        return try {
            withContext(Dispatchers.IO) {
                context.contentResolver.query(uri, null, s.selection, args, null, cancel)?.use { c ->
                    val out = ArrayList<SearchResult>()
                    while (out.size < AppSearch.LIMIT && c.moveToNext()) {
                        val row = runCatching {
                            AppSearch.suggestion({ col ->
                                val i = c.getColumnIndex(col)
                                if (i < 0 || c.isNull(i)) null else c.getString(i)
                            }, target)
                        }.getOrNull()
                        row?.let(out::add)
                    }
                    out
                }.orEmpty()
            }
        } finally {
            // Saiu antes de acabar (prazo, ou pesquisa nova): o fornecedor também pára, se souber.
            runCatching { cancel.cancel() }
        }
    }

    private suspend fun cinemeta(target: SearchTarget.Catalog, query: String): List<SearchResult> = coroutineScope {
        val lists = AppSearch.cinemetaUrls(query).map { (type, url) ->
            async(Dispatchers.IO) {
                val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
                http.newCall(request).execute().use { res ->
                    if (!res.isSuccessful) error("HTTP ${res.code}")
                    val body = res.body?.source()?.let { src ->
                        src.request(MAX_BODY + 1)
                        if (src.buffer.size > MAX_BODY) error("resposta grande demais")
                        src.buffer.readUtf8()
                    }.orEmpty()
                    AppSearch.cinemeta(body, type, target.app, target.pkg)
                }
            }
        }.map { it.await() }
        AppSearch.interleave(lists[0], lists[1])
    }

    /** Abre [result] na app dele. Devolve false se a app recusar ou já não existir. */
    fun open(result: SearchOpen): Boolean {
        val intent = Intent(result.action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        result.data?.let { intent.data = Uri.parse(it) }
        if (result.className != null) intent.component = ComponentName(result.pkg, result.className) else intent.setPackage(result.pkg)
        return start(intent)
    }

    /** Passa [query] à app [target], que mostra os seus resultados (ACTION_SEARCH com o texto). */
    fun handoff(target: SearchTarget, query: String): Boolean {
        val intent = Intent(Intent.ACTION_SEARCH)
            .putExtra(SearchManager.QUERY, query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // A atividade que declarou a pesquisa: muitas (ex.: a do Jellyfin) não têm a categoria DEFAULT, e um intent
        // só com o pacote não chegava lá.
        when (target) {
            is SearchTarget.Provider -> intent.component = ComponentName(target.pkg, target.className)
            is SearchTarget.Handoff -> intent.component = ComponentName(target.pkg, target.className)
            is SearchTarget.Catalog -> intent.setPackage(target.pkg)
        }
        return start(intent)
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Não abriu: ${e.javaClass.simpleName}")
        false
    }

    private companion object {
        const val TAG = "AppSearch"
        const val TIMEOUT_MS = 6_000L
        const val MAX_BODY = 1_000_000L
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        /** O meta-data da atividade que aponta para o XML da pesquisa. */
        const val SEARCHABLE_META = "android.app.searchable"
    }
}
