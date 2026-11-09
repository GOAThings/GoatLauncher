package cc.slokker.goatlauncher.data

import android.media.AudioManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Uma resposta da porta [PhoneApps.PORT]: sempre com o tamanho, sem cache e a fechar a ligação. */
internal class LanReply(val status: String, val type: String, val body: ByteArray) {
    fun bytes(): ByteArray =
        ("HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: no-cache\r\nConnection: close\r\n\r\n").toByteArray() + body

    companion object {
        fun ok(type: String, body: ByteArray) = LanReply("200 OK", type, body)
        fun text(status: String, text: String) = LanReply(status, "text/plain", text.toByteArray())
    }
}

/** O volume do sistema, como o comando físico o pede: [direction] é uma das AudioManager.ADJUST_*. */
fun interface Volume {
    fun adjust(direction: Int)
}

/** O que aconteceu a um pedido para abrir uma app (`POST /open/<pacote>`). */
enum class OpenResult {
    /** Abriu (ou vai pedir o código antes, se estiver bloqueada). */
    OPENED,

    /** Não está instalada, ou não tem com que abrir. */
    NOT_FOUND,

    /** O Android não deixa abrir apps a partir do fundo sem o serviço de acessibilidade ligado. */
    NO_BACKGROUND_START,
}

/** Abre uma app a pedido do telemóvel (na app, o AutoStart: com o controlo parental). */
fun interface AppOpener {
    fun open(pkg: String): OpenResult
}

/**
 * O que a porta [PhoneApps.PORT] responde a cada pedido, sem a parte da rede (essa fica no [PhoneApps]): uma tabela de
 * caminhos ([routes]), cada um com o que responde. O GoatRemote (Android e iPhone) depende destes caminhos, métodos e
 * códigos; os testes (PhoneApiTest) citam onde os usa. Um caminho novo é uma linha da tabela e a sua função.
 *
 * [apps]: as apps instaladas, pelo nome (o AppCatalog). [volume] e [opener]: o que o sistema faz. [pngs]: os PNG já
 * feitos, um por app instalada (na app, os do AppCatalog, partilhados com o índice do site).
 */
internal class PhoneRoutes(
    private val apps: suspend () -> List<LauncherApp>,
    private val volume: Volume,
    private val opener: AppOpener,
    internal val pngs: PerAppCache<ImageBitmap, ByteArray>,
) {
    /**
     * Cada parte como uma função, como no PhoneApiTest: [png] faz a imagem de uma app em PNG, [canOpen] diz se a app tem
     * com que abrir, [canStartFromBackground] se o serviço de acessibilidade está ligado e [open] abre-a.
     */
    constructor(
        apps: suspend () -> List<LauncherApp>,
        png: (ImageBitmap) -> ByteArray,
        volume: (direction: Int) -> Unit,
        canOpen: (pkg: String) -> Boolean,
        canStartFromBackground: () -> Boolean,
        open: (pkg: String) -> Unit,
    ) : this(
        apps = apps,
        volume = Volume { volume(it) },
        opener = AppOpener { pkg ->
            when {
                !canOpen(pkg) -> OpenResult.NOT_FOUND
                !canStartFromBackground() -> OpenResult.NO_BACKGROUND_START
                else -> OpenResult.OPENED.also { open(pkg) }
            }
        },
        pngs = PerAppCache(png),
    )

    /** Um caminho exato, ou (terminado em "/") tudo o que começa por ele; [handle] recebe o método e o resto do caminho. */
    private class Route(val path: String, val handle: suspend (method: String, rest: String) -> LanReply) {
        fun matches(p: String) = if (path.endsWith("/")) p.startsWith(path) else p == path
    }

    private val routes = listOf(
        Route("/apps") { _, _ -> appList() },
        Route("/icon/") { _, pkg -> icon(pkg) },
        Route("/volume/") { method, what -> volume(method, what) },
        Route("/open/") { method, pkg -> open(method, pkg) },
    )

    suspend fun reply(request: LanRequest): LanReply {
        if (!request.allowed) return LanReply.text("403 Forbidden", "recusado")
        val route = routes.firstOrNull { it.matches(request.path) } ?: return LanReply.text("404 Not Found", "GoatLauncher")
        return route.handle(request.method, request.path.removePrefix(route.path))
    }

    /** `GET /apps`: o resumo de cada app (ver [summary]), pelo nome. Qualquer método, como sempre foi. */
    private suspend fun appList(): LanReply {
        val json = JsonArray()
        for (app in installed()) json.add(app.summary())
        return LanReply.ok("application/json", json.toString().toByteArray())
    }

    /** `GET /icon/<pacote>`: o banner, ou sem ele o ícone, em PNG. */
    private suspend fun icon(pkg: String): LanReply {
        val app = installed().firstOrNull { it.packageName == pkg }
        val image = app?.banner ?: app?.icon ?: return LanReply.text("404 Not Found", "não há")
        return LanReply.ok("image/png", pngs.get(pkg, image))
    }

    /** As apps instaladas agora; os PNG das que saíram vão com elas. */
    private suspend fun installed(): List<LauncherApp> = apps().also { list -> pngs.retain(list.map { it.packageName }) }

    /** `POST /volume/up|down|mute`. */
    private fun volume(method: String, what: String): LanReply {
        val direction = when (what) {
            "up" -> AudioManager.ADJUST_RAISE
            "down" -> AudioManager.ADJUST_LOWER
            "mute" -> AudioManager.ADJUST_TOGGLE_MUTE
            else -> null
        }
        if (method != "POST" || direction == null) return LanReply.text("404 Not Found", "não há")
        volume.adjust(direction)
        return LanReply.text("200 OK", "ok")
    }

    /** `POST /open/<pacote>`: abre-a com o controlo parental; 409 sem o serviço de acessibilidade. */
    private fun open(method: String, pkg: String): LanReply {
        if (method != "POST") return LanReply.text("404 Not Found", "não há")
        return when (opener.open(pkg)) {
            OpenResult.OPENED -> LanReply.text("200 OK", "ok")
            OpenResult.NOT_FOUND -> LanReply.text("404 Not Found", "não há")
            OpenResult.NO_BACKGROUND_START -> LanReply.text("409 Conflict", "sem acessibilidade")
        }
    }
}

/**
 * Uma app como o GoatRemote (`GET /apps`) e o site da conta (`PUT /api/launcher/apps`, ver AppIndex) a recebem:
 * `{"package", "label", "banner": tem banner de TV, "color": a cor do cartão, "#RRGGBB"}`.
 */
internal fun LauncherApp.summary(): JsonObject = JsonObject().apply {
    addProperty("package", packageName)
    addProperty("label", label)
    addProperty("banner", banner != null)
    addProperty("color", String.format("#%06X", color.toArgb() and 0xFFFFFF))
}
