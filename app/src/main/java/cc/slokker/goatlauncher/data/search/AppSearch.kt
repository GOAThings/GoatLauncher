package cc.slokker.goatlauncher.data.search

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/*
 * A pesquisa do launcher: em que apps se pode procurar e como, e o que se lê das respostas. Aqui só regras puras (os
 * testes correm na JVM); o que fala com o Android e a rede está em SearchApps.
 */

/** Um resultado da pesquisa numa app: o que se mostra e como se abre nela. */
data class SearchResult(
    val title: String,
    val subtitle: String?,
    val year: String?,
    val imageUri: String?,
    val open: SearchOpen,
)

/**
 * Abrir um resultado: [action] com [data], enviado a [pkg] (à atividade [className], se a houver; é a que declarou a
 * pesquisa, como faz o Android).
 */
data class SearchOpen(val action: String, val data: String?, val pkg: String, val className: String?)

/** O que o `searchable` XML de uma app diz sobre as sugestões (os atributos `android:searchSuggest*`). */
data class Searchable(
    val authority: String,
    val path: String?,
    val selection: String?,
    val intentAction: String?,
    val intentData: String?,
)

/** As apps de catálogo: procuram no Cinemeta e abrem o resultado por um link. */
enum class CatalogApp {
    /** Nuvio: `nuvio://movie/<id>`, `nuvio://series/<id>` (ver o DeepLinkParser do NuvioTV). */
    NUVIO,

    /** Stremio: `stremio:///detail/<tipo>/<id>` (o formato da documentação dos addons; por confirmar numa TV). */
    STREMIO,
    ;

    fun link(type: String, id: String): String = when (this) {
        NUVIO -> "nuvio://$type/$id"
        STREMIO -> "stremio:///detail/$type/$id"
    }

    companion object {
        /** O Stremio da TV (e do telemóvel) tem sempre este pacote. O Nuvio encontra-se pelo link (tem vários pacotes). */
        const val STREMIO_PACKAGE = "com.stremio.one"
        const val NUVIO_SCHEME = "nuvio"
    }
}

/** Uma app em que o botão Pesquisar procura, e como. */
sealed interface SearchTarget {
    val pkg: String

    /** Tem um fornecedor de sugestões: os resultados aparecem no launcher. */
    data class Provider(override val pkg: String, val className: String, val searchable: Searchable) : SearchTarget

    /** Nuvio ou Stremio: resultados do Cinemeta no launcher, abertos na app por um link. */
    data class Catalog(override val pkg: String, val app: CatalogApp) : SearchTarget

    /** Trata ACTION_SEARCH mas sem fornecedor: o texto vai para a app, que mostra os seus resultados. */
    data class Handoff(override val pkg: String, val className: String) : SearchTarget
}

/** Uma atividade que trata ACTION_SEARCH (de [pkg]), com o `searchable` dela se o tiver e se tiver sugestões. */
data class SearchActivity(val pkg: String, val className: String, val searchable: Searchable?)

object AppSearch {
    /** A escolha "pesquisa do sistema" (o Assistente / pesquisa do Google TV), guardada no lugar de um pacote. */
    const val SYSTEM = "system"

    /** Quantos resultados se pedem a cada app. */
    const val LIMIT = 20

    const val MAX_TEXT = 300
    const val MAX_URI = 4096
    private const val MAX_QUERY = 100

    /**
     * As apps da pesquisa, pela ordem das Definições: primeiro as com fornecedor de sugestões (resultados no
     * launcher), depois o Nuvio e o Stremio, depois as que só recebem o texto. Cada app aparece uma vez (a melhor
     * forma ganha); o próprio launcher fica de fora.
     */
    fun classify(activities: List<SearchActivity>, catalogs: List<Pair<String, CatalogApp>>, self: String): List<SearchTarget> {
        val mine = activities.filter { it.pkg != self }
        val providers = mine.filter { it.searchable != null }
            .distinctBy { it.pkg }
            .map { SearchTarget.Provider(it.pkg, it.className, it.searchable!!) }
        val taken = providers.mapTo(HashSet()) { it.pkg }
        val catalog = catalogs.filter { it.first != self && it.first !in taken }
            .distinctBy { it.first }
            .map { SearchTarget.Catalog(it.first, it.second) }
        taken += catalog.map { it.pkg }
        val handoff = mine.filter { it.pkg !in taken }
            .distinctBy { it.pkg }
            .map { SearchTarget.Handoff(it.pkg, it.className) }
        return providers + catalog + handoff
    }

    /**
     * A app escolhida, se ainda serve: [stored] se for [SYSTEM] ou uma das [targets]; senão (nada escolhido, ou a app
     * foi desinstalada) a primeira das [targets], ou a pesquisa do sistema se não houver nenhuma.
     */
    fun resolve(stored: String?, targets: List<SearchTarget>): String = when {
        stored == SYSTEM -> SYSTEM
        stored != null && targets.any { it.pkg == stored } -> stored
        else -> targets.firstOrNull()?.pkg ?: SYSTEM
    }

    /** O texto a procurar: sem espaços a mais, com tamanho limitado. Vazio (ou só 1 letra) não se procura. */
    fun cleanQuery(text: String): String? = text.trim().replace(Regex("\\s+"), " ").take(MAX_QUERY).takeIf { it.length >= 2 }

    /**
     * O `searchable` a partir dos atributos do XML ([attr] já com as referências @string resolvidas). Sem autoridade de
     * sugestões não há fornecedor (null).
     */
    fun searchable(attr: (String) -> String?): Searchable? {
        fun clean(name: String) = attr(name)?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_URI }
        val authority = clean("searchSuggestAuthority")?.takeIf { AUTHORITY.matches(it) } ?: return null
        return Searchable(
            authority = authority,
            path = clean("searchSuggestPath")?.trim('/'),
            // A seleção conta mesmo sendo só um espaço e "?" (é o que diz que a pesquisa vai em selectionArgs).
            selection = attr("searchSuggestSelection")?.takeIf { it.isNotEmpty() },
            intentAction = clean("searchSuggestIntentAction"),
            intentData = clean("searchSuggestIntentData"),
        )
    }

    /** O endereço das sugestões: `content://<autoridade>/<caminho>/search_suggest_query/<texto>?limit=N`. */
    fun suggestUri(s: Searchable, query: String, limit: Int = LIMIT): String = buildString {
        append("content://").append(s.authority).append('/')
        s.path?.takeIf { it.isNotEmpty() }?.let { append(it).append('/') }
        append("search_suggest_query/").append(encode(query)).append("?limit=").append(limit)
    }

    /**
     * Uma linha das sugestões ([get] lê uma coluna pelo nome), como o Android a abriria: a ação da linha ou a do
     * `searchable` (VIEW por defeito); o data da linha, ou o do `searchable` mais "/" e o id da linha. Sem título,
     * fica de fora.
     */
    fun suggestion(get: (String) -> String?, target: SearchTarget.Provider): SearchResult? {
        fun text(col: String) = get(col)?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TEXT)
        fun uri(col: String) = get(col)?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_URI }
        val title = text(COL_TEXT_1) ?: return null
        val s = target.searchable
        val action = text(COL_INTENT_ACTION) ?: s.intentAction ?: ACTION_VIEW
        val id = uri(COL_INTENT_DATA_ID)
        val data = uri(COL_INTENT_DATA) ?: s.intentData?.let { base -> if (id != null) "$base/${encode(id)}" else base }
        // Só imagens com endereço (um ícone pode vir como o número de um recurso da app, que daqui não se lê).
        val image = (uri(COL_CARD_IMAGE) ?: uri(COL_ICON_1))?.takeIf { it.contains("://") }
        return SearchResult(
            title = title,
            subtitle = text(COL_TEXT_2),
            year = text(COL_YEAR)?.take(16),
            imageUri = image,
            open = SearchOpen(action, data, target.pkg, target.className),
        )
    }

    /** Os endereços do Cinemeta (sem chave) para filmes e séries. */
    fun cinemetaUrls(query: String): List<Pair<String, String>> = listOf("movie", "series").map { type ->
        type to "https://v3-cinemeta.strem.io/catalog/$type/top/search=${encode(query)}.json"
    }

    /** As respostas do Cinemeta (`{"metas":[…]}`) de um [type] ("movie" ou "series"); o que não se lê fica de fora. */
    fun cinemeta(json: String, type: String, app: CatalogApp, pkg: String): List<SearchResult> {
        val metas = runCatching { JsonParser.parseString(json).asJsonObject.getAsJsonArray("metas") }.getOrNull() ?: return emptyList()
        return metas.asSequence()
            .mapNotNull { (it as? JsonObject) }
            .mapNotNull { m ->
                val id = m.str("imdb_id")?.takeIf { IMDB.matches(it) } ?: m.str("id")?.takeIf { IMDB.matches(it) } ?: return@mapNotNull null
                val name = m.str("name")?.take(MAX_TEXT) ?: return@mapNotNull null
                SearchResult(
                    title = name,
                    subtitle = null,
                    year = (m.str("releaseInfo") ?: m.str("year"))?.take(16),
                    imageUri = m.str("poster")?.takeIf { it.startsWith("https://") && it.length <= MAX_URI },
                    open = SearchOpen(ACTION_VIEW, app.link(type, id), pkg, null),
                )
            }
            .take(LIMIT)
            .toList()
    }

    /** Filmes e séries alternados, pela ordem do Cinemeta, sem repetidos. */
    fun interleave(a: List<SearchResult>, b: List<SearchResult>): List<SearchResult> =
        (0 until maxOf(a.size, b.size)).flatMap { listOfNotNull(a.getOrNull(it), b.getOrNull(it)) }
            .distinctBy { it.open.data }
            .take(LIMIT)

    /** Codifica para um segmento de URI (como Uri.encode: só letras, números e `-_.~` ficam). */
    fun encode(s: String): String = buildString {
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xff
            if (c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c.toChar() in "-_.~") {
                append(c.toChar())
            } else {
                append('%').append("%02X".format(c))
            }
        }
    }

    private fun JsonObject.str(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }

    private val AUTHORITY = Regex("[A-Za-z0-9._-]{1,255}")
    private val IMDB = Regex("tt\\d{1,10}")

    const val ACTION_VIEW = "android.intent.action.VIEW"

    // As colunas do SearchManager (os valores, para os testes não precisarem do Android).
    const val COL_TEXT_1 = "suggest_text_1"
    const val COL_TEXT_2 = "suggest_text_2"
    const val COL_ICON_1 = "suggest_icon_1"
    const val COL_CARD_IMAGE = "suggest_result_card_image"
    const val COL_YEAR = "suggest_production_year"
    const val COL_INTENT_ACTION = "suggest_intent_action"
    const val COL_INTENT_DATA = "suggest_intent_data"
    const val COL_INTENT_DATA_ID = "suggest_intent_data_id"
}
