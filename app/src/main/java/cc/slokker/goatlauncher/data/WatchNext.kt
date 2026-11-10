package cc.slokker.goatlauncher.data

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Uma entrada do "Continuar a ver" (Watch Next do Android TV), publicada pela app [packageName]. */
@Immutable
data class WatchNextItem(
    val id: Long,
    val packageName: String,
    val title: String,
    val episodeTitle: String?,
    val season: String?,
    val episode: String?,
    val imageUri: String?,
    /** Largura / altura da imagem. */
    val aspectRatio: Float,
    val positionMs: Long,
    val durationMs: Long,
    val type: Int,
    val lastEngagement: Long,
    val intentUri: String?,
    /** Um canal em direto (ex.: GoatTV): a imagem é o logo do canal, nunca uma cena do conteúdo. */
    val isChannel: Boolean = false,
    /** Canal em direto: o programa que está a dar (a descrição), das [airStart] às [airEnd] (0 = sem horas). */
    val airing: String? = null,
    val airStart: Long = 0,
    val airEnd: Long = 0,
    /** Canal em direto do GoatTV: os programas depois do que está a dar (da descrição longa), o primeiro às [airEnd]. */
    val schedule: List<Programme> = emptyList(),
) {
    /**
     * O que está a dar às [now] num canal em direto: o programa publicado, ou um dos seguintes se esse já acabou (a
     * app pode não ter voltado a publicar). null sem programa, ou com todos já passados.
     */
    fun onAir(now: Long): OnAir? {
        if (!isChannel) return null
        val title = airing ?: return null
        if (airEnd <= 0 || now < airEnd) {
            return OnAir(title, progress(airStart, airEnd, now), airEnd.takeIf { it > 0 }, schedule.firstOrNull()?.title)
        }
        val i = schedule.indexOfLast { it.startMs in 1..now }
        val current = schedule.getOrNull(i) ?: return null
        val next = schedule.getOrNull(i + 1)
        // O último da lista não tem hora de fim: fica até a app voltar a publicar.
        return OnAir(current.title, next?.let { progress(current.startMs, it.startMs, now) }, next?.startMs, next?.title)
    }

    /** Os programas que ainda não começaram às [now] (os do painel "A seguir" do destaque). */
    fun upcoming(now: Long): List<Programme> = schedule.filter { it.startMs <= 0 || it.startMs > now }

    private fun progress(start: Long, end: Long, now: Long): Float? =
        if (end > start && start > 0) ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f) else null

    /**
     * A duração e a posição fazem sentido: há as duas e a duração não passa de [MAX_DURATION_MS] (o Nuvio chegou a
     * dar "28 h a faltar" num episódio; mais vale não mostrar a barra e o tempo do que mostrá-los errados).
     */
    private val hasPlayback: Boolean
        get() = durationMs in 1..MAX_DURATION_MS && positionMs > 0

    /** 0..1, ou null sem duração ou posição. */
    val progress: Float?
        get() = if (hasPlayback) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else null

    val remainingMinutes: Long?
        get() = if (hasPlayback) ((durationMs - positionMs).coerceAtLeast(0) + 59_999) / 60_000 else null

    /** O intent que abre a app neste conteúdo, ou null se o `intent_uri` não se ler ou apontar para outra app. */
    fun intent(): Intent? {
        val uri = intentUri ?: return null
        return runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }
            .onFailure { Log.w("WatchNext", "intent_uri inválido de $packageName") }
            .getOrNull()
            ?.takeIf { intent ->
                targetsPublisher(intent.`package`, intent.component?.packageName)
                    .also { if (!it) Log.w("WatchNext", "intent_uri de $packageName aponta para outra app") }
            }
            ?.apply {
                // Primeiro: com seletor, setPackage rebenta.
                selector = null
                // Um link simples (https://…) abriria o seletor ou o browser: vai para a app que o publicou.
                if (`package` == null && component == null) setPackage(packageName)
                // Nunca dar a outras apps acesso a URIs nossos por um intent vindo de fora.
                flags = (flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION).inv()) or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            }
    }

    /** Um cartão só abre a própria app que o publicou. */
    internal fun targetsPublisher(target: String?, componentPackage: String?): Boolean =
        (target == null || target == packageName) && (componentPackage == null || componentPackage == packageName)

    companion object {
        const val TYPE_CONTINUE = TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE

        /** Só do launcher (não existe no Android TV): uma recomendação que a pessoa ainda não começou. */
        const val TYPE_RECOMMENDED = 100

        /** A maior duração que se acredita: 12 h (um jogo ou uma maratona cabem; um episódio de 33 h não). */
        const val MAX_DURATION_MS = 12 * 60 * 60 * 1000L
    }
}

/** O programa de um canal em direto: [title], a parte já dada ([progress]), a hora a que acaba e o seguinte. */
data class OnAir(val title: String, val progress: Float?, val endsAt: Long?, val next: String?)

/** Um programa que ainda vai dar num canal, a começar às [startMs] (0 = não se sabe). */
@Immutable
data class Programme(val title: String, val startMs: Long)

/**
 * O que o GoatTV publica no Watch Next só para o launcher. É um contrato entre os dois repositórios: quem o produz é o
 * `scheduleText` de `itv/app/src/main/java/cc/slokker/itv/data/HomeScreen.kt` (com `HOME_UPCOMING` programas, ver
 * AppViewModel), e o GoatTvScheduleTest tem uma cópia dele. Mudar um lado obriga a mudar o outro (e o teste).
 */
internal object GoatTvContract {
    /** As builds do GoatTV que põem os programas seguintes na descrição longa (itv app/build.gradle.kts: debug, staging). */
    val PACKAGES = setOf("cc.slokker.itv", "cc.slokker.itv.debug", "cc.slokker.itv.staging")

    /** O GoatTV põe lá até cinco títulos, um por linha. */
    const val MAX_SCHEDULE = 1_500
}

/**
 * A descrição longa do GoatTV (ver [GoatTvContract]): o título do programa seguinte (começa às [nextStart], o fim do
 * atual) e depois uma linha "21:30 Título" por cada um dos outros, na hora deste aparelho. Uma linha que não seja
 * assim acaba a lista (uma versão antiga do GoatTV só põe o título do seguinte).
 */
internal fun parseSchedule(text: String, nextStart: Long, now: Long): List<Programme> {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val first = lines.firstOrNull() ?: return emptyList()
    val out = mutableListOf(Programme(first, nextStart))
    var after = nextStart.takeIf { it > 0 } ?: now
    for (line in lines.drop(1)) {
        val m = SCHEDULE_LINE.matchEntire(line) ?: break
        val cal = Calendar.getInstance().apply {
            timeInMillis = after
            set(Calendar.HOUR_OF_DAY, m.groupValues[1].toInt())
            set(Calendar.MINUTE, m.groupValues[2].toInt())
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Passa da meia-noite: é no dia seguinte.
        if (cal.timeInMillis < after) cal.add(Calendar.DAY_OF_MONTH, 1)
        after = cal.timeInMillis
        out += Programme(m.groupValues[3], after)
    }
    return out
}

private val SCHEDULE_LINE = Regex("""(\d{1,2}):(\d{2}) (.+)""")

/** O que entra do Watch Next: as entradas vêm de outras apps, e uma não pode pesar nem estragar as das outras. */
internal object WatchNextRows {
    const val MAX_SCANNED = 5000
    const val MAX_PER_APP = 40
    const val MAX_TOTAL = 400

    /**
     * Lê até [MAX_SCANNED] entradas. A que falhar ([onBadRow]) fica de fora, e as outras seguem. De cada app guardam-se
     * só as mais recentes, por isso muitas entradas de uma app não tiram o lugar às das outras.
     */
    fun read(count: Int, moveTo: (Int) -> Boolean, row: () -> WatchNextItem?, onBadRow: (Throwable) -> Unit): List<WatchNextItem> {
        val byApp = HashMap<String, MutableList<WatchNextItem>>()
        for (position in 0 until count.coerceAtMost(MAX_SCANNED)) {
            val item = runCatching { if (moveTo(position)) row() else null }.onFailure(onBadRow).getOrNull() ?: continue
            val rows = byApp.getOrPut(item.packageName) { ArrayList() }
            rows += item
            if (rows.size >= 2 * MAX_PER_APP) {
                rows.sortByDescending { it.lastEngagement }
                rows.subList(MAX_PER_APP, rows.size).clear()
            }
        }
        return byApp.values.flatten()
    }

    /**
     * Os canais das apps ([previews], os programas que cada app publica nas suas filas do ecrã inicial do sistema)
     * enchem o "Continuar a ver" das apps que não têm nada no Watch Next ([watchNext]): uma app com Watch Next fica só
     * com ele. Os programas entram como recomendações (ainda por começar), pela ordem da app, até [MAX_PER_APP].
     */
    fun withChannels(watchNext: Map<String, List<WatchNextItem>>, previews: List<WatchNextItem>): Map<String, List<WatchNextItem>> {
        val fill = previews
            .filter { watchNext[it.packageName].isNullOrEmpty() }
            .distinctBy { it.packageName + "\u0000" + (it.intentUri ?: it.title) }
            .groupBy { it.packageName }
            .mapValues { (_, items) -> items.take(MAX_PER_APP) }
        return if (fill.isEmpty()) watchNext else watchNext + fill
    }

    /** Por pacote, o mais recente primeiro, sem repetidas, até [MAX_PER_APP] por app e [MAX_TOTAL] ao todo. */
    fun newestByApp(rows: List<WatchNextItem>): Map<String, List<WatchNextItem>> {
        val perApp = HashMap<String, Int>()
        return rows
            .sortedByDescending { it.lastEngagement }
            // Entradas repetidas (ex.: a app perdeu o id da que publicou e criou outra): fica a mais recente.
            .distinctBy { it.packageName + "\u0000" + (it.intentUri ?: it.title) }
            .filter { item ->
                val seen = perApp[item.packageName] ?: 0
                perApp[item.packageName] = seen + 1
                seen < MAX_PER_APP
            }
            .take(MAX_TOTAL)
            .groupBy { it.packageName }
    }
}

/** O Watch Next do sistema: o [WatchNextSource] (outro nos testes, ver ContinueWatching). */
interface WatchNextFeed {
    /** Por pacote, o mais recente primeiro. null se não deu para ler (não quer dizer que não haja nada). */
    suspend fun load(): Map<String, List<WatchNextItem>>?

    /** Chama [onChange] quando alguma app mexe no Watch Next. Devolve a função que deixa de observar. */
    fun observe(onChange: () -> Unit): () -> Unit
}

/**
 * Lê o Watch Next do TvProvider (o mesmo que o Google TV e o Projectivy usam) e agrupa-o por app. As apps sem nada no
 * Watch Next ficam com os programas dos seus canais (as filas que publicam para o ecrã inicial do sistema), se o
 * sistema os mostrar a outras apps (ver [WatchNextRows.withChannels]).
 * Precisa de READ_TV_LISTINGS (ver o manifesto); sem ela, o sistema só devolve os programas da própria app.
 * O TvProvider não aceita `selection` a quem não é do sistema: lê-se tudo e filtra-se aqui.
 */
class WatchNextSource(private val context: Context) : WatchNextFeed {

    private val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    override suspend fun load(): Map<String, List<WatchNextItem>>? = withContext(Dispatchers.IO) {
        if (!supported) return@withContext emptyMap()
        runCatching {
            var bad = 0
            context.contentResolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, PROJECTION, null, null, null)
                ?.use { c ->
                    WatchNextRows.read(c.count, c::moveToPosition, { read(c, WATCH_NEXT) }) {
                        if (bad++ == 0) Log.w(TAG, "Entrada que não se lê: ${it.javaClass.simpleName}")
                    }
                }
                .orEmpty()
                .let(WatchNextRows::newestByApp)
                .let { WatchNextRows.withChannels(it, previews()) }
                .also { all ->
                    Log.d(TAG, "${all.values.sumOf { l -> l.size }} entradas de ${all.keys}")
                    // De onde vêm as imagens de cada app (para perceber a qualidade dos cartões). O resto do URI é da app.
                    all.forEach { (pkg, items) -> items.firstOrNull()?.let { Log.d(TAG, "$pkg: ${BugReport.originOf(it.imageUri)}") } }
                }
        }.getOrElse {
            Log.w(TAG, "Ler o Watch Next", it)
            null
        }
    }

    /** Os programas dos canais das apps; vazio se o sistema não os mostrar (ou não deixar ler). */
    private fun previews(): List<WatchNextItem> = runCatching {
        var bad = 0
        context.contentResolver.query(TvContractCompat.PreviewPrograms.CONTENT_URI, PREVIEW_PROJECTION, null, null, null)
            ?.use { c ->
                WatchNextRows.read(c.count, c::moveToPosition, { read(c, PREVIEW) }) {
                    if (bad++ == 0) Log.w(TAG, "Programa de canal que não se lê: ${it.javaClass.simpleName}")
                }
            }
            .orEmpty()
            .also { Log.d(TAG, "${it.size} programas de canais de ${it.map { p -> p.packageName }.toSet()}") }
    }.getOrElse {
        Log.w(TAG, "Ler os canais das apps", it)
        emptyList()
    }

    override fun observe(onChange: () -> Unit): () -> Unit {
        if (!supported) return {}
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
            override fun onChange(selfChange: Boolean, uri: Uri?) = onChange()
        }
        return runCatching {
            context.contentResolver.registerContentObserver(TvContractCompat.WatchNextPrograms.CONTENT_URI, true, observer)
            // Os canais das apps também enchem o "Continuar a ver" (ver load).
            runCatching { context.contentResolver.registerContentObserver(TvContractCompat.PreviewPrograms.CONTENT_URI, true, observer) }
            fun() { context.contentResolver.unregisterContentObserver(observer) }
        }.getOrElse {
            Log.w(TAG, "Observar o Watch Next", it)
            fun() {}
        }
    }

    /**
     * Uma linha do Watch Next ou de um canal ([cols] diz de qual). Os programas dos canais não têm tipo nem a última
     * vez que se viram: entram como recomendações, pela ordem da app.
     */
    private fun read(c: Cursor, cols: Columns): WatchNextItem? {
        if (c.int(cols.browsable) == 0) return null // a pessoa tirou-o do "A seguir" (ou o canal escondeu-o)
        val pkg = c.whole(cols.pkg, MAX_PACKAGE) ?: return null
        val title = c.text(cols.title) ?: c.text(cols.episodeTitle) ?: return null
        val poster = c.whole(cols.poster, MAX_IMAGE_URI)
        val thumb = c.whole(cols.thumb, MAX_IMAGE_URI)
        return WatchNextItem(
            id = c.long(cols.id) ?: return null,
            packageName = pkg,
            title = title,
            episodeTitle = c.text(cols.episodeTitle),
            season = c.text(cols.season),
            episode = c.text(cols.episode),
            imageUri = sharper(poster ?: thumb),
            aspectRatio = ratio(if (poster != null) c.int(cols.posterRatio) else c.int(cols.thumbRatio)),
            positionMs = c.long(cols.position) ?: 0,
            durationMs = c.long(cols.duration) ?: 0,
            type = c.int(cols.type) ?: cols.defaultType,
            lastEngagement = c.long(cols.engagement) ?: 0,
            intentUri = c.whole(cols.intent, MAX_INTENT_URI),
            isChannel = c.int(cols.programType) == TvContractCompat.WatchNextPrograms.TYPE_CHANNEL || c.int(cols.live) == 1,
            airing = c.text(cols.description),
            airStart = c.long(cols.start) ?: 0,
            airEnd = c.long(cols.end) ?: 0,
            // Só o GoatTV põe lá os programas seguintes; nas outras apps é uma sinopse.
            schedule = c.str(cols.longDescription)?.take(GoatTvContract.MAX_SCHEDULE)?.takeIf { pkg in GoatTvContract.PACKAGES }
                ?.let { parseSchedule(it, c.long(cols.end) ?: 0, System.currentTimeMillis()) }.orEmpty(),
        )
    }

    /**
     * Muitas apps (ex.: Nuvio) publicam imagens do TMDB num tamanho pequeno (`/t/p/w300/…`), que no cartão
     * e sobretudo no destaque de ecrã inteiro ficam esborratadas. Pede-se a versão grande da mesma imagem.
     */
    private fun sharper(uri: String?): String? = uri?.replace(TMDB_SIZE, "image.tmdb.org/t/p/w1280/")

    /** Constantes ASPECT_RATIO_* do TvContract. Por defeito 16:9, o formato do ecrã. */
    private fun ratio(constant: Int?): Float = when (constant) {
        TvContractCompat.PreviewPrograms.ASPECT_RATIO_3_2 -> 3f / 2f
        TvContractCompat.PreviewPrograms.ASPECT_RATIO_4_3 -> 4f / 3f
        TvContractCompat.PreviewPrograms.ASPECT_RATIO_1_1 -> 1f
        TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3 -> 2f / 3f
        TvContractCompat.PreviewPrograms.ASPECT_RATIO_MOVIE_POSTER -> 1f / 1.441f
        else -> 16f / 9f
    }

    // Uma coluna que esta tabela não tem (-1, ver [Columns]) lê-se como vazia.
    private fun Cursor.str(i: Int): String? = if (i < 0 || isNull(i)) null else getString(i)?.takeIf { it.isNotBlank() }

    /** Texto para mostrar, cortado no máximo. */
    private fun Cursor.text(i: Int): String? = str(i)?.take(MAX_TEXT)

    /** Um pacote ou URI cortado já não serve: inteiro, ou nada. */
    private fun Cursor.whole(i: Int, max: Int): String? = str(i)?.takeIf { it.length <= max }

    private fun Cursor.long(i: Int): Long? = if (i < 0 || isNull(i)) null else getLong(i)
    private fun Cursor.int(i: Int): Int? = if (i < 0 || isNull(i)) null else getInt(i)

    private companion object {
        const val TAG = "WatchNext"
        const val MAX_TEXT = 300
        const val MAX_PACKAGE = 255
        const val MAX_IMAGE_URI = 4096
        const val MAX_INTENT_URI = 8192
        private val TMDB_SIZE = Regex("""image\.tmdb\.org/t/p/(?:w\d{2,3}|h\d{2,3})/""")

        // Nomes das colunas do TvContract (estáveis desde o Android 8).
        val PROJECTION = arrayOf(
            "_id",
            "package_name",
            "title",
            "episode_title",
            "season_display_number",
            "episode_display_number",
            "poster_art_uri",
            "poster_art_aspect_ratio",
            "thumbnail_uri",
            "thumbnail_aspect_ratio",
            "last_playback_position_millis",
            "duration_millis",
            "watch_next_type",
            "last_engagement_time_utc_millis",
            "intent_uri",
            "browsable",
            "type",
            "live",
            "short_description",
            "long_description",
            "start_time_utc_millis",
            "end_time_utc_millis",
        )

        /**
         * Os programas dos canais (preview_program): as colunas do Watch Next menos o tipo e a última vez que se viu,
         * que essa tabela não tem.
         */
        val PREVIEW_PROJECTION = PROJECTION.filterNot { it == "watch_next_type" || it == "last_engagement_time_utc_millis" }.toTypedArray()

        val WATCH_NEXT = Columns(PROJECTION, WatchNextItem.TYPE_CONTINUE)
        val PREVIEW = Columns(PREVIEW_PROJECTION, WatchNextItem.TYPE_RECOMMENDED)
    }
}

/**
 * A posição de cada coluna numa projeção, pelo nome (acrescentar uma coluna a meio não troca as outras). As que têm de
 * existir falham logo; as que a tabela pode não ter ([type], [engagement]) ficam -1. [defaultType]: o tipo sem coluna.
 */
private class Columns(projection: Array<String>, val defaultType: Int) {
    private val names = projection.toList()
    private fun col(name: String): Int = names.indexOf(name).also { check(it >= 0) { "Coluna fora da PROJECTION: $name" } }
    private fun optional(name: String): Int = names.indexOf(name)

    val id = col("_id")
    val pkg = col("package_name")
    val title = col("title")
    val episodeTitle = col("episode_title")
    val season = col("season_display_number")
    val episode = col("episode_display_number")
    val poster = col("poster_art_uri")
    val posterRatio = col("poster_art_aspect_ratio")
    val thumb = col("thumbnail_uri")
    val thumbRatio = col("thumbnail_aspect_ratio")
    val position = col("last_playback_position_millis")
    val duration = col("duration_millis")
    val type = optional("watch_next_type")
    val engagement = optional("last_engagement_time_utc_millis")
    val intent = col("intent_uri")
    val browsable = col("browsable")
    val programType = col("type")
    val live = col("live")
    val description = col("short_description")
    val longDescription = col("long_description")
    val start = col("start_time_utc_millis")
    val end = col("end_time_utc_millis")
}
