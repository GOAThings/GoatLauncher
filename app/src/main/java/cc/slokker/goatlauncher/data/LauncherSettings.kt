package cc.slokker.goatlauncher.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/*
 * Formato das definições guardadas no disco e na conta (o backend guarda o JSON tal como o recebe, e o site edita-o):
 * "account" (igual em todas as TVs da conta) e "device" (só desta TV). Todas as chaves, cada uma com um valor de
 * exemplo, estão em app/src/test/resources/formats/ (account.json e device.json), e o SettingsFormatTest garante que
 * cada uma se lê e se escreve igual. Os valores possíveis de cada campo estão no companion da classe, junto das
 * constantes (as Definições percorrem as mesmas listas que o fromJson aceita).
 *
 * Um fundo "file" só existe na TV onde foi escolhido; as outras mostram o fundo por defeito.
 * Um fundo "reddit" (o antigo defeito, r/EarthPorn, ou outro subreddit) lê-se como o [Wallpaper.DEFAULT]: o Reddit
 * acabou com o RSS a 13/11/2026 e deixou de ser fonte. Fica gravado assim até à próxima mudança nesta TV.
 * "reddit_min_width" (nome antigo) é a resolução mínima de todas as fotos online.
 *
 * A leitura é tolerante (campos em falta ou com outro tipo ficam no valor por defeito) e a escrita parte
 * do JSON recebido ([raw]), para não apagar campos que uma versão mais nova da app tenha juntado.
 */

data class Wallpaper(val type: String, val value: String) {
    /** Fotos de uma fonte online, uma de cada vez (ver OnlineWallpapers). */
    val isOnlinePhotos: Boolean get() = type == TYPE_WIKIMEDIA || type == TYPE_NASA

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

        /**
         * Imagens de um subreddit, até à 0.9.11 (e o fundo por defeito, r/EarthPorn). O Reddit deixou de dar RSS: um
         * fundo destes, guardado nesta TV ou vindo de outra da conta, lê-se como o [DEFAULT] (ver AccountSettings.fromJson).
         */
        const val LEGACY_TYPE_REDDIT = "reddit"

        /** A imagem do dia do Wikimedia Commons (ver WikimediaFeed); [value] é sempre "potd". */
        const val TYPE_WIKIMEDIA = "wikimedia"

        /** A Astronomy Picture of the Day da NASA (ver ApodFeed); [value] é sempre "apod". */
        const val TYPE_NASA = "nasa"

        /** A imagem do dia do Wikimedia Commons; sem rede (ou antes de chegar) vê-se o fundo "Lago" desenhado. */
        val DEFAULT = Wallpaper(TYPE_WIKIMEDIA, WikimediaFeed.ID)
    }
}

/**
 * Um botão do comando que abre [app], ou que faz uma [action] (aí [app] fica vazio). Identifica-se pelo código
 * Android ([keyCode]) e pelo da tecla física ([scanCode]; 0 = qualquer): o botão Netflix da Shield, por exemplo, é o
 * BUTTON_12 com o scan 393, e assim o botão 12 de um comando de jogos fica de fora.
 */
data class ButtonMap(val keyCode: Int, val scanCode: Int, val app: String, val action: ButtonAction? = null) {
    fun matches(keyCode: Int, scanCode: Int) = this.keyCode == keyCode && (this.scanCode == 0 || this.scanCode == scanCode)

    companion object {
        const val MAX = 5
        const val SHIELD_NETFLIX_KEY = 188 + 11 // KEYCODE_BUTTON_12
        const val SHIELD_NETFLIX_SCAN = 393 // KEY_VIDEO
    }
}

/**
 * O que um botão do comando pode fazer além de abrir uma app. O [id] é o que se grava (`"action"` no botão): nunca
 * muda. Uma versão antiga que não conheça a ação ignora o botão.
 */
enum class ButtonAction(val id: String) {
    /** O ecrã inicial (o GoatLauncher), mesmo que outro launcher esteja como ecrã inicial do sistema. */
    HOME("home"),
    /** A app que estava à frente antes da atual (carregar outra vez volta à atual). */
    PREVIOUS_APP("previous_app"),
    /** A pesquisa do sistema (Google TV, Assistente). */
    SEARCH("search"),
    ANDROID_SETTINGS("android_settings"),
    /** Desligar o ecrã (standby), como o botão de energia. */
    SLEEP("sleep"),
    ;

    companion object {
        fun of(id: String?): ButtonAction? = entries.firstOrNull { it.id == id }

        /** As que esta TV sabe fazer: desligar o ecrã pela acessibilidade só existe no Android 9 (API 28) ou mais novo. */
        fun available(sdk: Int): List<ButtonAction> = entries.filter { it != SLEEP || sdk >= 28 }
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
     * "shelf": destaque em ecrã inteiro (como o top shelf do tvOS); "spotlight": o destaque com os seguintes ao lado;
     * "column": os favoritos numa coluna e o que a app com foco tem à direita; "carousel": cartões grandes ao centro.
     */
    val continueStyle: String = STYLE_ROW,
    /** O tempo (ícone e temperatura) ao lado da saudação, em cima à direita. */
    val showWeather: Boolean = true,
    /** Fundos de fotos online (Wikimedia, NASA): fotos mais estreitas do que isto (px) ficam de fora; 0 = todas (ver [PHOTO_MIN_WIDTHS]). */
    val photoMinWidth: Int = 1920,
    /** De quantos em quantos minutos muda a foto dos fundos que rodam (fotos online e pasta): um dos [WALLPAPER_MINUTES]. */
    val wallpaperMinutes: Int = 30,
    /** Modo ambiente: minutos sem tocar no comando até o ecrã inicial ficar só com o fundo e as horas; 0 = desligado (ver [IDLE_MINUTES]). */
    val idleMinutes: Int = 0,
    /** Com o modo ambiente, o ecrã inicial não deixa a TV adormecer nem começar a proteção de ecrã do sistema. */
    val idleKeepAwake: Boolean = false,
    /** O que mostram o modo ambiente e o protetor de ecrã: um dos [AMBIENT_STYLES] (o fundo e as horas, ou o relógio a ressaltar). */
    val ambientStyle: String = AMBIENT_PHOTOS,
    /** Um tique ao mover com as setas e um clique no OK (ver NavSounds). */
    val navSounds: Boolean = false,
    /** O que a app do YouTube mostra no "Continuar a ver" (com a conta ligada): um dos [YOUTUBE_FEEDS]. */
    val youtubeFeed: String = YT_BOTH,
    /** Língua da app: "auto" (a do sistema) ou um código BCP 47 das 15 do site (ver Strings.LANGUAGES). */
    val language: String = "auto",
    /** Modo noite (botão ao lado da rede): fundo escurecido e, nas fotos online e na pasta, fotos escuras; de dia, claras. */
    val nightMode: Boolean = false,
    /** Dia/noite pela hora da TV (noite das [NIGHT_FROM] às [NIGHT_TO]); carregar no botão desliga-o. */
    val nightAuto: Boolean = false,
    /** Cartão de uma app com foco: um dos FOCUS_EFFECTS (contorno, brilho, vidro, reflexo, aurora, visor, sublinhado, anel, escurecer os outros, só sombra). */
    val focusEffect: String = FOCUS_BORDER,
    /**
     * Animação do efeito de foco: uma das [FOCUS_ANIMATIONS] (ANIM_SHINE: luz a dar a volta ao cartão). Cada efeito só
     * mostra as suas ([focusAnimations]); as outras ficam guardadas mas desenham-se como ANIM_NONE.
     */
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
        addProperty("reddit_min_width", photoMinWidth)
        addProperty("wallpaper_minutes", wallpaperMinutes)
        addProperty("idle_minutes", idleMinutes)
        addProperty("idle_keep_awake", idleKeepAwake)
        addProperty("ambient_style", ambientStyle)
        addProperty("nav_sounds", navSounds)
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
        const val STYLE_SPOTLIGHT = "spotlight"
        const val STYLE_COLUMN = "column"
        const val STYLE_CAROUSEL = "carousel"
        /** Pela ordem das Definições. */
        val STYLES = listOf(STYLE_ROW, STYLE_SHELF, STYLE_SPOTLIGHT, STYLE_COLUMN, STYLE_CAROUSEL)

        const val LAYOUT_ANDROID = "android"
        const val LAYOUT_APPLE_TV = "appletv"
        val LAYOUTS = listOf(LAYOUT_ANDROID, LAYOUT_APPLE_TV)

        /** O fundo e as horas. */
        const val AMBIENT_PHOTOS = "photos"
        /** Ecrã preto com o relógio a ressaltar nas bordas. */
        const val AMBIENT_BOUNCE = "bounce"
        /** Pela ordem em que o OK os percorre nas Definições. */
        val AMBIENT_STYLES = listOf(AMBIENT_PHOTOS, AMBIENT_BOUNCE)

        /** Modo noite automático sem saber onde a TV está: das 20h às 7h. */
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
        /** Todas as que se guardam; cada efeito aceita só algumas ([focusAnimations]), por esta ordem. */
        val FOCUS_ANIMATIONS = listOf(ANIM_NONE, ANIM_PULSE, ANIM_BREATH, ANIM_SHINE)

        /** As animações que cada efeito aceita: o vidro e o "escurecer" não têm (o vidro tem o seu brilho a passar). */
        fun focusAnimations(effect: String): List<String> = when (effect) {
            FOCUS_GLASS, FOCUS_DIM, FOCUS_SHADOW -> emptyList()
            FOCUS_VIEWFINDER, FOCUS_UNDERLINE -> listOf(ANIM_NONE, ANIM_PULSE, ANIM_BREATH)
            else -> FOCUS_ANIMATIONS
        }

        const val COLOR_AUTO = "auto"
        const val BG_GRAPHITE = "graphite"
        const val BG_NONE = "none"

        /** Os intervalos das Definições, pela ordem em que o OK os percorre: 5, 15 e 30 min, 1 h, 3 h e um dia. */
        val WALLPAPER_MINUTES = listOf(5, 15, 30, 60, 180, 24 * 60)

        /**
         * A resolução mínima nas Definições (qualquer, Full HD, 2K, 4K), pela ordem do OK. O site pode gravar outro
         * valor (≥ 0): vale na mesma, e o OK passa daí à primeira.
         */
        val PHOTO_MIN_WIDTHS = listOf(0, 1920, 2560, 3840)

        /**
         * O modo ambiente nas Definições (desligado, 1, 2, 5, 10 e 30 min), pela ordem do OK. O site pode gravar outro
         * valor (≥ 0): vale na mesma, e o OK passa daí à primeira.
         */
        val IDLE_MINUTES = listOf(0, 1, 2, 5, 10, 30)

        const val YT_BOTH = "both"
        const val YT_CONTINUE = "continue"
        const val YT_RECOMMENDED = "recommended"
        /** Pela ordem em que o OK os percorre nas Definições: os dois → só a meio → só recomendações. */
        val YOUTUBE_FEEDS = listOf(YT_BOTH, YT_CONTINUE, YT_RECOMMENDED)

        fun fromJson(json: JsonObject?): AccountSettings {
            if (json == null) return AccountSettings()
            val wp = json.obj("wallpaper")
            val type = wp?.str("type")
            val value = wp?.str("value")
            return AccountSettings(
                wallpaper = if (type != null && type != Wallpaper.LEGACY_TYPE_REDDIT && !value.isNullOrBlank()) Wallpaper(type, value) else Wallpaper.DEFAULT,
                clock24h = json.bool("clock_24h") ?: true,
                showGreeting = json.bool("show_greeting") ?: true,
                showContinueWatching = json.bool("show_continue_watching") ?: true,
                showAppName = json.bool("show_app_name") ?: true,
                layout = json.str("layout")?.takeIf { it in LAYOUTS } ?: LAYOUT_ANDROID,
                // O antigo "downunder" (já não existe) passa a fila de cartões. Os estilos novos foram disposições
                // durante uns dias (só em versões de teste): uma dessas disposições guardada passa a ser o estilo.
                continueStyle = (json.str("continue_style")?.takeIf { it in STYLES } ?: STYLE_ROW).let { style ->
                    json.str("layout")?.takeIf { it in STYLES } ?: style
                },
                showWeather = json.bool("show_weather") ?: true,
                photoMinWidth = json.int("reddit_min_width")?.coerceAtLeast(0) ?: 1920,
                wallpaperMinutes = json.int("wallpaper_minutes")?.takeIf { it in WALLPAPER_MINUTES } ?: 30,
                idleMinutes = json.int("idle_minutes")?.coerceAtLeast(0) ?: 0,
                idleKeepAwake = json.bool("idle_keep_awake") ?: false,
                ambientStyle = json.str("ambient_style")?.takeIf { it in AMBIENT_STYLES } ?: AMBIENT_PHOTOS,
                navSounds = json.bool("nav_sounds") ?: false,
                youtubeFeed = json.str("youtube_feed")?.takeIf { it in YOUTUBE_FEEDS } ?: YT_BOTH,
                language = json.str("language") ?: "auto",
                nightMode = json.bool("night_mode") ?: false,
                nightAuto = json.bool("night_auto") ?: false,
                focusEffect = json.str("focus_effect")?.takeIf { it in FOCUS_EFFECTS } ?: FOCUS_BORDER,
                focusAnimation = json.str("focus_animation")?.takeIf { it in FOCUS_ANIMATIONS } ?: ANIM_NONE,
                focusColor = json.str("focus_color")?.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) } ?: COLOR_AUTO,
                iconBackground = json.str("icon_background")
                    ?.takeIf { it == BG_NONE || it.matches(Regex("#[0-9A-Fa-f]{6}")) } ?: BG_GRAPHITE,
                raw = json,
            )
        }
    }
}

/** Um sítio escolhido à mão para o tempo e o dia/noite (em vez do que o IP diz). [name] é para mostrar. */
data class Place(val name: String, val lat: Double, val lon: Double)

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
    /**
     * App que abre ao ligar a TV (e, com [autostartWake], ao acordar), [autostartDelay] segundos depois (um dos
     * [AUTOSTART_DELAYS] nas Definições; o site pode gravar outro entre 0 e [AUTOSTART_DELAY_MAX]).
     */
    val autostart: String? = null,
    val autostartWake: Boolean = false,
    val autostartDelay: Int = 5,
    /** Botões do comando que abrem uma app ou fazem uma ação (pelo HomeGuardService), no máximo [ButtonMap.MAX]. */
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
    /** Onde a TV está, para o tempo e o dia/noite; null = pelo IP (pode falhar com VPN). Cada TV tem a sua. */
    val location: Place? = null,
    val raw: JsonObject = JsonObject(),
) {
    /**
     * Estas definições com o controlo parental de [local]: o código, as apps bloqueadas, a proteção e as horas (as
     * chaves [PARENTAL_KEYS]; o SettingsRepositoryTest confirma que são só essas).
     */
    fun withParentalOf(local: DeviceSettings) = copy(
        pin = local.pin,
        locked = local.locked,
        lockSettings = local.lockSettings,
        lockFrom = local.lockFrom,
        lockTo = local.lockTo,
    )

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
                    if (b.action != null) addProperty("action", b.action.id) else addProperty("app", b.app)
                })
            }
        })
        if (pin != null) addProperty("pin", pin) else remove("pin")
        add("locked", JsonArray().apply { locked.sorted().forEach(::add) })
        addProperty("lock_settings", lockSettings)
        if (lockFrom != null && lockTo != null) add("lock_hours", JsonArray().apply { add(lockFrom); add(lockTo) }) else remove("lock_hours")
        if (location != null) {
            add("location", JsonObject().apply {
                addProperty("name", location.name)
                addProperty("lat", location.lat)
                addProperty("lon", location.lon)
            })
        } else {
            remove("location")
        }
    }

    companion object {
        /**
         * O controlo parental no JSON: ao ligar a TV com um código, estas ficam as desta TV e o resto vem da conta (ver
         * SettingsRepository.link e [withParentalOf]).
         */
        val PARENTAL_KEYS = setOf("pin", "locked", "lock_settings", "lock_hours")

        /** A espera do arranque automático nas Definições (segundos), pela ordem do OK. */
        val AUTOSTART_DELAYS = listOf(0, 3, 5, 10, 20)

        /** A maior espera aceite (de outro sítio, ex.: o site); acima disto lê-se este valor. */
        const val AUTOSTART_DELAY_MAX = 120

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
                autostartDelay = json.int("autostart_delay")?.coerceIn(0, AUTOSTART_DELAY_MAX) ?: 5,
                buttons = json.buttons(),
                pin = json.str("pin")?.takeIf { it.contains(':') },
                locked = json.strings("locked").toSet(),
                lockSettings = json.bool("lock_settings") ?: false,
                lockFrom = json.ints("lock_hours").takeIf { it.size == 2 }?.get(0)?.coerceIn(0, 23),
                lockTo = json.ints("lock_hours").takeIf { it.size == 2 }?.get(1)?.coerceIn(0, 23),
                location = json.obj("location")?.let { o ->
                    val lat = o.get("lat")?.takeIf { it.isJsonPrimitive }?.asDouble
                    val lon = o.get("lon")?.takeIf { it.isJsonPrimitive }?.asDouble
                    if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) null
                    else Place(o.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(), lat, lon)
                },
                raw = json,
            )
        }
    }
}

/**
 * O valor depois de [current] nesta lista, como o OK das Definições os percorre: volta ao primeiro depois do último, e
 * também quando [current] não está na lista (um valor gravado pelo site, por exemplo).
 */
fun <T> List<T>.after(current: T): T = getOrElse(indexOf(current) + 1) { first() }

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
            // Uma ação que esta versão não conheça (de uma mais nova) é ignorada, como um botão sem app.
            ButtonAction.of(o.str("action"))?.let { return@mapNotNull ButtonMap(key, o.int("scan") ?: 0, "", it) }
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
