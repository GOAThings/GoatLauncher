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

/**
 * O que a porta [PhoneApps.PORT] responde a cada pedido, sem a parte da rede (essa fica no [PhoneApps]). O GoatRemote
 * (Android e iPhone) depende destes caminhos, métodos e códigos; os testes (PhoneApiTest) citam onde os usa.
 *
 * [apps]: as apps instaladas, pelo nome. [png]: a imagem de uma app em PNG. [volume]: uma das AudioManager.ADJUST_*.
 * [canOpen]: a app tem com que abrir. [canStartFromBackground]: o serviço de acessibilidade está ligado. [open]: abre-a
 * (com o controlo parental).
 */
internal class PhoneRoutes(
    private val apps: suspend () -> List<LauncherApp>,
    private val png: (ImageBitmap) -> ByteArray,
    private val volume: (direction: Int) -> Unit,
    private val canOpen: (pkg: String) -> Boolean,
    private val canStartFromBackground: () -> Boolean,
    private val open: (pkg: String) -> Unit,
) {
    /** Os PNG já feitos, um por app instalada (antes ficava um a mais por cada atualização, e os das desinstaladas). */
    internal val pngs = PerAppCache(png)

    suspend fun reply(request: LanRequest): LanReply {
        if (!request.allowed) return LanReply.text("403 Forbidden", "recusado")
        val method = request.method
        val path = request.path
        if (path.startsWith("/volume/")) return volume(method, path.removePrefix("/volume/"))
        if (path.startsWith("/open/")) return open(method, path.removePrefix("/open/"))
        return when {
            path == "/apps" -> {
                val json = JsonArray()
                for (app in installed()) json.add(app.summary())
                LanReply.ok("application/json", json.toString().toByteArray())
            }
            path.startsWith("/icon/") -> {
                val pkg = path.removePrefix("/icon/")
                val app = installed().firstOrNull { it.packageName == pkg }
                val image = app?.banner ?: app?.icon ?: return LanReply.text("404 Not Found", "não há")
                LanReply.ok("image/png", pngs.get(pkg, image))
            }
            else -> LanReply.text("404 Not Found", "GoatLauncher")
        }
    }

    /** As apps instaladas agora; os PNG das que saíram vão com elas. */
    private suspend fun installed(): List<LauncherApp> = apps().also { list -> pngs.retain(list.map { it.packageName }) }

    private fun volume(method: String, what: String): LanReply {
        val direction = when (what) {
            "up" -> AudioManager.ADJUST_RAISE
            "down" -> AudioManager.ADJUST_LOWER
            "mute" -> AudioManager.ADJUST_TOGGLE_MUTE
            else -> null
        }
        if (method != "POST" || direction == null) return LanReply.text("404 Not Found", "não há")
        volume(direction)
        return LanReply.text("200 OK", "ok")
    }

    private fun open(method: String, pkg: String): LanReply = when {
        method != "POST" || !canOpen(pkg) -> LanReply.text("404 Not Found", "não há")
        !canStartFromBackground() -> LanReply.text("409 Conflict", "sem acessibilidade")
        else -> {
            open(pkg)
            LanReply.text("200 OK", "ok")
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
