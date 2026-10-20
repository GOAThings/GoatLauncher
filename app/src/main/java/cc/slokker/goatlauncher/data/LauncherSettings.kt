package cc.slokker.goatlauncher.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/*
 * Formato das definições guardadas na conta (o backend guarda o JSON tal como o recebe):
 *
 *   "account": { "wallpaper": {"type": "builtin" | "file" | "url" | "reddit", "value": "lake" | "wall-123.jpg" | "https://…" | "EarthPorn"},
 *                "clock_24h": true, "show_greeting": true, "show_continue_watching": true, "show_app_name": true,
 *                "continue_style": "row" | "shelf" }
 *   "device":  { "apps": ["com.netflix.ninja", …], "hidden": ["com.android.vending"],
 *                "icons": {"com.netflix.ninja": "pack:dev.pack/netflix" | "file:com.netflix.ninja-123.png"} }
 *
 * Um fundo "file" só existe na TV onde foi escolhido; as outras mostram o fundo por defeito.
 *
 * A leitura é tolerante (campos em falta ou com outro tipo ficam no valor por defeito) e a escrita parte
 * do JSON recebido ([raw]), para não apagar campos que uma versão mais nova da app tenha juntado.
 */

data class Wallpaper(val type: String, val value: String) {
    companion object {
        const val TYPE_BUILTIN = "builtin"
        const val TYPE_URL = "url"

        /** Imagem da box copiada para a app (ver ImageImport). */
        const val TYPE_FILE = "file"

        /** Imagens de um subreddit, a mudar de tempos a tempos (ver RedditWallpapers). [value] é o subreddit. */
        const val TYPE_REDDIT = "reddit"
        /** Fotos do r/EarthPorn; sem rede (ou antes de chegarem) vê-se o fundo "Lago" desenhado. */
        val DEFAULT = Wallpaper(TYPE_REDDIT, "EarthPorn")
    }
}

/** Igual em todas as TVs da conta. */
data class AccountSettings(
    val wallpaper: Wallpaper = Wallpaper.DEFAULT,
    val clock24h: Boolean = true,
    val showGreeting: Boolean = true,
    val showContinueWatching: Boolean = true,
    /** O nome de cada app por baixo do banner. */
    val showAppName: Boolean = true,
    /** "row": fila de cartões por cima dos favoritos; "shelf": destaque em ecrã inteiro (como o top shelf do tvOS). */
    val continueStyle: String = STYLE_ROW,
    val raw: JsonObject = JsonObject(),
) {
    fun toJson(): JsonObject = raw.deepCopy().apply {
        add("wallpaper", JsonObject().apply {
            addProperty("type", wallpaper.type)
            addProperty("value", wallpaper.value)
        })
        addProperty("clock_24h", clock24h)
        addProperty("show_greeting", showGreeting)
        addProperty("show_continue_watching", showContinueWatching)
        addProperty("show_app_name", showAppName)
        addProperty("continue_style", continueStyle)
    }

    companion object {
        const val STYLE_ROW = "row"
        const val STYLE_SHELF = "shelf"

        fun fromJson(json: JsonObject?): AccountSettings {
            if (json == null) return AccountSettings()
            val wp = json.obj("wallpaper")
            val type = wp?.str("type")
            val value = wp?.str("value")
            return AccountSettings(
                wallpaper = if (type != null && !value.isNullOrBlank()) Wallpaper(type, value) else Wallpaper.DEFAULT,
                clock24h = json.bool("clock_24h") ?: true,
                showGreeting = json.bool("show_greeting") ?: true,
                showContinueWatching = json.bool("show_continue_watching") ?: true,
                showAppName = json.bool("show_app_name") ?: true,
                continueStyle = json.str("continue_style")?.takeIf { it == STYLE_SHELF } ?: STYLE_ROW,
                raw = json,
            )
        }
    }
}

/** Só desta TV. [apps] é a ordem da fila; as apps instaladas que lá não estão vão para o fim. */
data class DeviceSettings(
    val apps: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    /** Ícones escolhidos pela pessoa, por pacote (ver CustomIcons para o formato). */
    val icons: Map<String, String> = emptyMap(),
    val raw: JsonObject = JsonObject(),
) {
    fun toJson(): JsonObject = raw.deepCopy().apply {
        add("apps", JsonArray().apply { apps.forEach(::add) })
        add("hidden", JsonArray().apply { hidden.sorted().forEach(::add) })
        add("icons", JsonObject().apply { icons.toSortedMap().forEach { (pkg, spec) -> addProperty(pkg, spec) } })
    }

    companion object {
        fun fromJson(json: JsonObject?): DeviceSettings {
            if (json == null) return DeviceSettings()
            return DeviceSettings(
                apps = json.strings("apps").distinct(),
                hidden = json.strings("hidden").toSet(),
                icons = json.obj("icons")?.entrySet()
                    ?.mapNotNull { (pkg, v) -> v.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.let { pkg to it.asString } }
                    ?.toMap()
                    .orEmpty(),
                raw = json,
            )
        }
    }
}

/** null se não for um objeto JSON com pelo menos um campo (o que a conta devolve antes da primeira gravação). */
fun JsonElement?.nonEmptyObject(): JsonObject? =
    this?.takeIf { it.isJsonObject }?.asJsonObject?.takeIf { it.size() > 0 }

private fun JsonObject.obj(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.str(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

private fun JsonObject.bool(name: String): Boolean? =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean

private fun JsonObject.strings(name: String): List<String> =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString }
        .orEmpty()
