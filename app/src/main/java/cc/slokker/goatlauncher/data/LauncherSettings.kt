package cc.slokker.goatlauncher.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/*
 * Formato das definições guardadas na conta (o backend guarda o JSON tal como o recebe):
 *
 *   "account": { "wallpaper": {"type": "builtin" | "file" | "url" | "reddit", "value": "lake" | "wall-123.jpg" | "https://…" | "EarthPorn"},
 *                "clock_24h": true, "show_greeting": true, "show_continue_watching": true, "show_app_name": true,
 *                "layout": "android" | "appletv", "continue_style": "row" | "shelf", "show_weather": true,
 *                "reddit_min_width": 1920, "idle_minutes": 0, "idle_keep_awake": false,
 *                "youtube_feed": "both" | "continue" | "recommended", "language": "auto" | "pt-PT" | …,
 *                "night_mode": false, "night_auto": false,
 *                "focus_effect": "border" | "glow" | "shadow", "focus_animation": "none" | "pulse" | "breath" | "shine",
 *                "focus_color": "auto" | "#38BDF8",
 *                "icon_background": "graphite" | "none" | "#38BDF8" }
 *   "device":  { "apps": ["com.netflix.ninja", …], "hidden": ["com.android.vending"],
 *                "icons": {"com.netflix.ninja": "pack:dev.pack/netflix" | "file:com.netflix.ninja-123.png"},
 *                "names": {"io.gh.reisxd.tizentube.cobalt": "YouTube"}, "no_continue": ["com.netflix.ninja"],
 *                "autostart": "cc.slokker.itv", "autostart_wake": false, "autostart_delay": 5,
 *                "buttons": [{"key": 199, "scan": 393, "app": "com.nuvio.tv"}],
 *                "pin": "<sal>:<sha-256>", "locked": ["com.netflix.ninja"], "lock_settings": true, "lock_hours": [20, 8] }
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

        /**
         * As imagens de uma pasta da box (ou de um disco USB/de rede), uma de cada vez, a mudar de tempos a tempos.
         * [value] é o caminho da pasta; noutra TV da conta não existe e fica o fundo por defeito.
         */
        const val TYPE_FOLDER = "folder"

        /** Imagens de um subreddit, a mudar de tempos a tempos (ver RedditWallpapers). [value] é o subreddit. */
        const val TYPE_REDDIT = "reddit"
        /** Fotos do r/EarthPorn; sem rede (ou antes de chegarem) vê-se o fundo "Lago" desenhado. */
        val DEFAULT = Wallpaper(TYPE_REDDIT, "EarthPorn")
    }
}

/**
 * Um botão do comando que abre [app]. Identifica-se pelo código Android ([keyCode]) e pelo da tecla física
 * ([scanCode]; 0 = qualquer): o botão Netflix da Shield, por exemplo, é o BUTTON_12 com o scan 393, e assim o
 * botão 12 de um comando de jogos fica de fora.
 */
data class ButtonMap(val keyCode: Int, val scanCode: Int, val app: String) {
    fun matches(keyCode: Int, scanCode: Int) = this.keyCode == keyCode && (this.scanCode == 0 || this.scanCode == scanCode)

    companion object {
        const val MAX = 5
        const val SHIELD_NETFLIX_KEY = 188 + 11 // KEYCODE_BUTTON_12
        const val SHIELD_NETFLIX_SCAN = 393 // KEY_VIDEO
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
    /**
     * Disposição do ecrã inicial. "android": separadores Início/Apps e o "Continuar a ver" em [continueStyle];
     * "appletv": sem separador Apps (as apps ficam por baixo da doca dos favoritos) e cartões grandes, como no tvOS.
     */
    val layout: String = LAYOUT_ANDROID,
    /**
     * Só na disposição Android. "row": fila de cartões (do favorito com foco) por cima dos favoritos;
     * "shelf": destaque em ecrã inteiro (como o top shelf do tvOS).
     */
    val continueStyle: String = STYLE_ROW,
    /** O tempo (ícone e temperatura) ao lado da saudação, em cima à direita. */
    val showWeather: Boolean = true,
    /** Fundo do Reddit: fotos mais estreitas do que isto (px) ficam de fora; 0 = todas. */
    val redditMinWidth: Int = 1920,
    /** Modo ambiente: minutos sem tocar no comando até o ecrã inicial ficar só com o fundo e as horas; 0 = desligado. */
    val idleMinutes: Int = 0,
    /** Com o modo ambiente, o ecrã inicial não deixa a TV adormecer nem começar a proteção de ecrã do sistema. */
    val idleKeepAwake: Boolean = false,
    /** O que a app do YouTube mostra no "Continuar a ver" (com a conta ligada): YT_BOTH, YT_CONTINUE ou YT_RECOMMENDED. */
    val youtubeFeed: String = YT_BOTH,
    /** Língua da app: "auto" (a do sistema) ou um código BCP 47 das 15 do site (ver Strings.LANGUAGES). */
    val language: String = "auto",
    /** Modo noite (botão ao lado da rede): fundo escurecido e, no Reddit, fotos escuras; de dia, fotos claras. */
    val nightMode: Boolean = false,
    /** Dia/noite pela hora da TV (noite das [NIGHT_FROM] às [NIGHT_TO]); carregar no botão desliga-o. */
    val nightAuto: Boolean = false,
    /** Cartão de uma app com foco: um dos FOCUS_EFFECTS (contorno, brilho, vidro, reflexo, aurora, visor, sublinhado, anel, escurecer os outros, só sombra). */
    val focusEffect: String = FOCUS_BORDER,
    /** Animação do efeito de foco: ANIM_NONE, ANIM_PULSE, ANIM_BREATH ou ANIM_SHINE (luz a dar a volta ao cartão). */
    val focusAnimation: String = ANIM_NONE,
    /** Cor do efeito de foco: COLOR_AUTO (contorno branco, brilho da cor da app) ou "#RRGGBB". */
    val focusColor: String = COLOR_AUTO,
    /** Fundo dos ícones escolhidos com fundo transparente: BG_GRAPHITE, BG_NONE (vê-se o fundo do ecrã) ou "#RRGGBB". */
    val iconBackground: String = BG_GRAPHITE,
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
        addProperty("layout", layout)
        addProperty("continue_style", continueStyle)
        addProperty("show_weather", showWeather)
        addProperty("reddit_min_width", redditMinWidth)
        addProperty("idle_minutes", idleMinutes)
        addProperty("idle_keep_awake", idleKeepAwake)
        addProperty("youtube_feed", youtubeFeed)
        addProperty("language", language)
        addProperty("night_mode", nightMode)
        addProperty("night_auto", nightAuto)
        addProperty("focus_effect", focusEffect)
        addProperty("focus_animation", focusAnimation)
        addProperty("focus_color", focusColor)
        addProperty("icon_background", iconBackground)
    }

    companion object {
        const val STYLE_ROW = "row"
        const val STYLE_SHELF = "shelf"

        const val LAYOUT_ANDROID = "android"
        const val LAYOUT_APPLE_TV = "appletv"

        /** Modo noite automático: das 20h às 7h. */
        const val NIGHT_FROM = 20
        const val NIGHT_TO = 7

        const val FOCUS_BORDER = "border"
        const val FOCUS_GLOW = "glow"
        const val FOCUS_SHADOW = "shadow"
        const val FOCUS_GLASS = "glass"
        const val FOCUS_REFLECTION = "reflection"
        const val FOCUS_AURORA = "aurora"
        const val FOCUS_VIEWFINDER = "viewfinder"
        const val FOCUS_UNDERLINE = "underline"
        const val FOCUS_RING = "ring"
        const val FOCUS_DIM = "dim"
        /** Pela ordem em que o OK os percorre nas Definições. */
        val FOCUS_EFFECTS = listOf(
            FOCUS_BORDER, FOCUS_GLOW, FOCUS_GLASS, FOCUS_REFLECTION, FOCUS_AURORA, FOCUS_VIEWFINDER, FOCUS_UNDERLINE, FOCUS_RING,
            FOCUS_DIM, FOCUS_SHADOW,
        )
        const val ANIM_NONE = "none"
        const val ANIM_PULSE = "pulse"
        const val ANIM_BREATH = "breath"
        const val ANIM_SHINE = "shine"
        const val COLOR_AUTO = "auto"
        const val BG_GRAPHITE = "graphite"
        const val BG_NONE = "none"

        const val YT_BOTH = "both"
        const val YT_CONTINUE = "continue"
        const val YT_RECOMMENDED = "recommended"

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
                layout = json.str("layout")?.takeIf { it == LAYOUT_APPLE_TV } ?: LAYOUT_ANDROID,
                // O antigo "downunder" (já não existe) passa a fila de cartões.
                continueStyle = json.str("continue_style")?.takeIf { it == STYLE_SHELF } ?: STYLE_ROW,
                showWeather = json.bool("show_weather") ?: true,
                redditMinWidth = json.int("reddit_min_width")?.coerceAtLeast(0) ?: 1920,
                idleMinutes = json.int("idle_minutes")?.coerceAtLeast(0) ?: 0,
                idleKeepAwake = json.bool("idle_keep_awake") ?: false,
                youtubeFeed = json.str("youtube_feed")?.takeIf { it == YT_CONTINUE || it == YT_RECOMMENDED } ?: YT_BOTH,
                language = json.str("language") ?: "auto",
                nightMode = json.bool("night_mode") ?: false,
                nightAuto = json.bool("night_auto") ?: false,
                focusEffect = json.str("focus_effect")?.takeIf { it in FOCUS_EFFECTS } ?: FOCUS_BORDER,
                focusAnimation = json.str("focus_animation")?.takeIf { it == ANIM_PULSE || it == ANIM_BREATH || it == ANIM_SHINE } ?: ANIM_NONE,
                focusColor = json.str("focus_color")?.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) } ?: COLOR_AUTO,
                iconBackground = json.str("icon_background")
                    ?.takeIf { it == BG_NONE || it.matches(Regex("#[0-9A-Fa-f]{6}")) } ?: BG_GRAPHITE,
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
    /** Nomes escolhidos pela pessoa, por pacote (em vez do nome da app). */
    val names: Map<String, String> = emptyMap(),
    /** Apps cujo "Continuar a ver" (ou recomendações do YouTube) não aparece no Início. */
    val noContinue: Set<String> = emptySet(),
    /** App que abre ao ligar a TV (e, com [autostartWake], ao acordar), [autostartDelay] segundos depois. */
    val autostart: String? = null,
    val autostartWake: Boolean = false,
    val autostartDelay: Int = 5,
    /** Botões do comando que abrem uma app (pelo HomeGuardService), no máximo [ButtonMap.MAX]. */
    val buttons: List<ButtonMap> = emptyList(),
    /** Controlo parental (ver ParentalLock): o código guardado com sal e hash; null = desligado. */
    val pin: String? = null,
    /** Apps que pedem o código para abrir. */
    val locked: Set<String> = emptySet(),
    /** As Definições e as mudanças na organização (menu das apps) pedem o código. */
    val lockSettings: Boolean = false,
    /** As apps só ficam bloqueadas entre estas horas (ex.: 20 → 8); null = sempre. */
    val lockFrom: Int? = null,
    val lockTo: Int? = null,
    val raw: JsonObject = JsonObject(),
) {
    fun toJson(): JsonObject = raw.deepCopy().apply {
        add("apps", JsonArray().apply { apps.forEach(::add) })
        add("hidden", JsonArray().apply { hidden.sorted().forEach(::add) })
        add("icons", JsonObject().apply { icons.toSortedMap().forEach { (pkg, spec) -> addProperty(pkg, spec) } })
        add("names", JsonObject().apply { names.toSortedMap().forEach { (pkg, name) -> addProperty(pkg, name) } })
        add("no_continue", JsonArray().apply { noContinue.sorted().forEach(::add) })
        if (autostart != null) addProperty("autostart", autostart) else remove("autostart")
        addProperty("autostart_wake", autostartWake)
        addProperty("autostart_delay", autostartDelay)
        remove("netflix_button")
        add("buttons", JsonArray().apply {
            buttons.forEach { b ->
                add(JsonObject().apply {
                    addProperty("key", b.keyCode)
                    addProperty("scan", b.scanCode)
                    addProperty("app", b.app)
                })
            }
        })
        if (pin != null) addProperty("pin", pin) else remove("pin")
        add("locked", JsonArray().apply { locked.sorted().forEach(::add) })
        addProperty("lock_settings", lockSettings)
        if (lockFrom != null && lockTo != null) add("lock_hours", JsonArray().apply { add(lockFrom); add(lockTo) }) else remove("lock_hours")
    }

    companion object {
        fun fromJson(json: JsonObject?): DeviceSettings {
            if (json == null) return DeviceSettings()
            return DeviceSettings(
                apps = json.strings("apps").distinct(),
                hidden = json.strings("hidden").toSet(),
                icons = json.stringMap("icons"),
                names = json.stringMap("names"),
                noContinue = json.strings("no_continue").toSet(),
                autostart = json.str("autostart")?.takeIf { it.isNotBlank() },
                autostartWake = json.bool("autostart_wake") ?: false,
                autostartDelay = json.int("autostart_delay")?.coerceIn(0, 120) ?: 5,
                buttons = json.buttons(),
                pin = json.str("pin")?.takeIf { it.contains(':') },
                locked = json.strings("locked").toSet(),
                lockSettings = json.bool("lock_settings") ?: false,
                lockFrom = json.ints("lock_hours").takeIf { it.size == 2 }?.get(0)?.coerceIn(0, 23),
                lockTo = json.ints("lock_hours").takeIf { it.size == 2 }?.get(1)?.coerceIn(0, 23),
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

/** Os botões; o antigo "netflix_button" (só o botão Netflix da Shield) passa a ser o primeiro. */
private fun JsonObject.buttons(): List<ButtonMap> {
    val list = get("buttons")?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { e ->
            val o = e.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val key = o.int("key") ?: return@mapNotNull null
            val app = o.str("app")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ButtonMap(key, o.int("scan") ?: 0, app)
        }
        .orEmpty()
    if (list.isNotEmpty() || has("buttons")) return list.take(ButtonMap.MAX)
    val netflix = str("netflix_button")?.takeIf { it.isNotBlank() } ?: return emptyList()
    return listOf(ButtonMap(ButtonMap.SHIELD_NETFLIX_KEY, ButtonMap.SHIELD_NETFLIX_SCAN, netflix))
}

private fun JsonObject.int(name: String): Int? =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt

private fun JsonObject.ints(name: String): List<Int> =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt }
        .orEmpty()

private fun JsonObject.stringMap(name: String): Map<String, String> =
    obj(name)?.entrySet()
        ?.mapNotNull { (k, v) -> v.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.let { k to it.asString } }
        ?.toMap()
        .orEmpty()

private fun JsonObject.strings(name: String): List<String> =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString }
        .orEmpty()
