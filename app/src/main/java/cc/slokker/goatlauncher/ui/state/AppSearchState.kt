package cc.slokker.goatlauncher.ui.state

import cc.slokker.goatlauncher.data.DeviceSettings
import cc.slokker.goatlauncher.data.search.AppSearch
import cc.slokker.goatlauncher.data.search.SearchResult
import cc.slokker.goatlauncher.data.search.SearchTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** O que o ecrã da pesquisa mostra: o texto, e para ele os resultados da app escolhida. */
data class SearchUi(
    val query: String = "",
    /** A pesquisa está a correr (para o texto [query]). */
    val loading: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    /** A app não respondeu (erro, recusa ou demora). */
    val failed: Boolean = false,
    /** Já se procurou este texto (para dizer "nada encontrado" só depois de procurar). */
    val searched: Boolean = false,
)

/**
 * A pesquisa do launcher: em que apps se pode procurar ([targets], lidas outra vez quando as apps instaladas mudam), a
 * escolhida nas Definições ([choice], ver AppSearch.resolve) e a pesquisa em curso ([ui]): o texto espera [DEBOUNCE_MS]
 * sem mudar antes de ir à app, e um texto novo cancela a pesquisa anterior.
 */
class AppSearchState(
    private val scope: CoroutineScope,
    private val readTargets: suspend () -> List<SearchTarget>,
    private val search: suspend (SearchTarget, String) -> Pair<List<SearchResult>, Boolean>,
    device: StateFlow<DeviceSettings>,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val _targets = MutableStateFlow<List<SearchTarget>?>(null)

    /** As apps em que se pode procurar (null antes da primeira leitura). */
    val targets: StateFlow<List<SearchTarget>?> = _targets.asStateFlow()

    /** O pacote escolhido (ou [AppSearch.SYSTEM]), já com a regra de quando falta. */
    val choice: StateFlow<String> = combine(_targets, device) { t, d -> AppSearch.resolve(d.searchApp, t.orEmpty()) }
        .stateIn(scope, SharingStarted.Eagerly, AppSearch.SYSTEM)

    /** A app onde se procura agora (null = a pesquisa do sistema). */
    val target: SearchTarget? get() = _targets.value?.firstOrNull { it.pkg == choice.value }

    private val _ui = MutableStateFlow(SearchUi())
    val ui: StateFlow<SearchUi> = _ui.asStateFlow()

    private var job: Job? = null
    private var refreshJob: Job? = null

    /** Lê outra vez as apps (ao arrancar e quando as apps instaladas mudam). */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch { _targets.value = readTargets() }
    }

    /** O texto mudou: procura-o (depois de uma pausa) na app escolhida. */
    fun setQuery(text: String) {
        job?.cancel()
        val query = AppSearch.cleanQuery(text)
        val target = target
        if (query == null || target == null || target is SearchTarget.Handoff) {
            _ui.value = SearchUi(query = text)
            return
        }
        _ui.value = _ui.value.copy(query = text, loading = true)
        job = scope.launch {
            wait(DEBOUNCE_MS)
            val (results, failed) = search(target, query)
            _ui.value = SearchUi(query = text, results = results, failed = failed, searched = true)
        }
    }

    /** O ecrã fechou: esquece o texto e pára o que estava a correr. */
    fun clear() {
        job?.cancel()
        _ui.value = SearchUi()
    }

    companion object {
        const val DEBOUNCE_MS = 400L
    }
}
