package cc.slokker.goatlauncher.data

import android.content.Context
import android.os.Build
import android.os.Process
import cc.slokker.goatlauncher.BuildConfig
import java.io.File
import java.util.Date

/**
 * Relatórios para a zona admin do site (`POST /api/reports`): o logcat desta app, o último crash e o que a pessoa
 * escreveu. Antes de sair da TV, os tokens (conta e YouTube), os emails, os URLs, os servidores e os IPs passam a
 * `***` (ver [redact]). Igual ao da GoatTV.
 */
object BugReport {
    internal const val CRASH_FILE = "last_crash.txt"
    /** O servidor aceita até 64 KB de registo. */
    private const val MAX_BYTES = 60_000

    /** Guarda o crash num ficheiro (vai no próximo relatório) e deixa o Android tratar dele como sempre. */
    fun installCrashHandler(context: Context) {
        val file = File(context.filesDir, CRASH_FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { file.writeText("${Date()} · ${BuildConfig.VERSION_NAME} · ${thread.name}\n${e.stackTraceToString()}") }
            previous?.uncaughtException(thread, e)
        }
    }

    /** O aparelho, para o campo `device`. */
    val device: String get() = "Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}".take(120)

    /** O registo a enviar: [header], o último crash e o logcat (as linhas mais recentes), sem os [secrets]. */
    fun collect(context: Context, header: String, secrets: Collection<String>): String {
        val crash = File(context.filesDir, CRASH_FILE).takeIf { it.exists() }?.let { runCatching { it.readText().take(15_000) }.getOrNull() }
        val head = buildString {
            append(header).append('\n')
            if (crash != null) append("\n--- Last crash ---\n").append(crash.trimEnd()).append('\n')
            append("\n--- Log ---\n")
        }
        return trim(redact(head, secrets), redact(logcat(), secrets))
    }

    /** As linhas desta app (sem a permissão READ_LOGS, o Android já só dá as da própria app). */
    private fun logcat(): String = runCatching {
        val command = mutableListOf("logcat", "-d", "-v", "time", "-t", "4000")
        if (Build.VERSION.SDK_INT >= 24) command += "--pid=${Process.myPid()}"
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }
    }.getOrElse { "logcat: $it" }

    /** [head] inteiro e, do [log], as últimas linhas que cabem. */
    internal fun trim(head: String, log: String, max: Int = MAX_BYTES): String {
        val budget = max - head.toByteArray().size
        var tail = log
        while (tail.toByteArray().size > budget && tail.contains('\n')) tail = tail.substringAfter('\n')
        if (tail.toByteArray().size > budget) tail = ""
        return head + tail
    }

    /** De um URI de outra app, só o que se pode registar: o esquema e o servidor. */
    fun originOf(uri: String?): String {
        val match = uri?.let { ORIGIN.find(it) } ?: return "?"
        val scheme = match.groupValues[1]
        val host = match.groups[2]?.value
        return if (host == null) "$scheme:" else "$scheme://$host"
    }

    /**
     * Troca por `***` os [secrets] e o que tem cara de credencial ou de endereço: URLs inteiros, parâmetros, caminhos
     * Xtream e Stalker, MACs, user:pass@, tokens, emails, servidores e IPs (v4 e v6). As regras são as do GoatTV
     * (BugReportRules, no itv-shared), mais os emails, o IPv6 e os nomes de pacotes, que aqui contam muito.
     */
    fun redact(text: String, secrets: Collection<String>): String {
        var out = text
        for (secret in secrets.filter { it.length >= 3 }.distinct().sortedByDescending { it.length }) out = out.replace(secret, "***")
        // Os links podem ter credenciais em qualquer parte do caminho ou do servidor: sai o URL inteiro.
        out = URL.replace(out) { "${it.groupValues[1]}://***" }
        out = QUERY.replace(out) { "${it.groupValues[1]}=***" }
        out = XTREAM_PATH.replace(out) { "/${it.groupValues[1]}/***/***/" }
        out = STALKER_PATH.replace(out, "/stalker/***/***/")
        out = MAC.replace(out, "***")
        out = USER_INFO.replace(out) { "${it.groupValues[1]}***@" }
        out = AUTH.replace(out) { "${it.groupValues[1]} ***" }
        // O email antes dos servidores, senão ficava `nome@***`.
        out = EMAIL.replace(out, "***")
        out = HOST.replace(out) { if (looksLikeCode(it.value)) it.value else "***" }
        // Um `::` sozinho (Kotlin, C++) não é endereço: só sai se tiver algum número.
        out = IPV6.replace(out) { if (it.value.any(Char::isLetterOrDigit)) "***" else it.value }
        out = IPV4.replace(out, "***")
        return out
    }

    /**
     * Um nome com pontos que é código, um ficheiro ou um pacote (fica, para o relatório servir) e não um servidor. Na
     * dúvida, é servidor.
     */
    private fun looksLikeCode(name: String): Boolean {
        val labels = name.split('.')
        val last = labels.last().lowercase()
        if (last in FILE_ENDINGS) return true
        // com.netflix.ninja, org.xbmc.kodi: os pacotes das apps são o que mais aparece no registo do launcher.
        if (labels.size >= 3 && labels[0] in PACKAGE_ROOTS) return true
        if (last.length == 2 || last in COMMON_TLDS || '-' in name) return false
        return name.any { it.isUpperCase() } || labels[0] in CODE_ROOTS || "${labels[0]}.${labels[1]}" in CODE_ROOTS
    }

    private val URL = Regex("\\b([a-zA-Z][a-zA-Z0-9+.-]*)://[^\\s\"'<>]+")
    private val QUERY = Regex("(?i)\\b(mac|sn|device_id2?|[\\w.-]{0,40}(?:password|passwd|pass|pwd|user|token|auth|key|secret|signature|sig|credential)[\\w.-]{0,40})=([^&\\s\"']+)")
    private val XTREAM_PATH = Regex("/(live|movie|series|timeshift)/[^/\\s]+/[^/\\s]+/")
    private val STALKER_PATH = Regex("/stalker/[^/\\s]+/[^/\\s]+/")
    private val MAC = Regex("(?i)\\b[0-9a-f]{2}(?:(?::|%3A)[0-9a-f]{2}){5}\\b")
    private val USER_INFO = Regex("([a-zA-Z][a-zA-Z0-9+.-]*://)[^/\\s@]+@")
    private val AUTH = Regex("(?i)(authorization:?|bearer)\\s+(?:(?:bearer|basic|token)\\s+)?[^\\s,]+")
    private val EMAIL = Regex("(?<![A-Za-z0-9_.+-])[A-Za-z0-9_.+-]+@(?:[A-Za-z0-9-]+\\.)+[A-Za-z][A-Za-z0-9-]+")
    private val HOST = Regex("(?<![\\w.-])(?:[A-Za-z0-9_-]+\\.)+[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9](?![\\w-])")
    private val IPV4 = Regex("(?<![\\w.])\\d{1,3}(?:\\.\\d{1,3}){3}(?![\\w.])")
    private const val H = "[0-9A-Fa-f]{1,4}"
    private const val V4 = "\\d{1,3}(?:\\.\\d{1,3}){3}"
    /**
     * Os IPv6 inteiros (8 grupos) ou com `::`, também com um IPv4 no fim (`::ffff:1.2.3.4`) e com a interface
     * (`fe80::1%wlan0`). Uma hora (`01:12:03`) não tem `::` nem 8 grupos, por isso fica.
     */
    private val IPV6 = Regex(
        "(?<![A-Za-z0-9_:.%])(?:" +
            "(?:(?:$H:){6}|(?:$H(?::$H){0,5})?::(?:$H:){0,5})$V4(?![A-Za-z0-9_]|\\.\\d)" +
            "|(?:(?:$H:){7}$H|(?:$H(?::$H){0,6})?::(?:$H(?::$H){0,6})?)(?:%[A-Za-z0-9_.-]+|(?![A-Za-z0-9_:%]|\\.\\d))" +
            ")",
    )
    private val FILE_ENDINGS = setOf("kt", "java", "swift", "cpp", "dex", "jar", "apk", "ipa", "dylib", "xml", "json", "png", "jpg", "webp", "gz", "ts", "m3u", "m3u8", "mp4", "mkv", "php", "html", "js", "css", "txt", "log")
    private val COMMON_TLDS = setOf("com", "net", "org", "info", "biz", "xyz", "top", "site", "online", "club", "live", "pro", "stream", "app", "dev", "tech", "store", "shop", "cloud", "link", "icu", "vip", "fun", "space", "website", "one", "world", "today", "media", "example", "invalid", "test", "local", "lan")
    private val CODE_ROOTS = setOf("java", "javax", "kotlin", "kotlinx", "android", "androidx", "dalvik", "libcore", "sun", "okhttp3", "okio", "retrofit2", "coil", "coil3", "com.android", "com.google", "com.squareup", "com.apple", "org.videolan", "org.json", "cc.slokker")
    /** Os começos dos pacotes das apps (`com.`, `org.`…), que um servidor quase nunca tem. */
    private val PACKAGE_ROOTS = setOf("com", "org", "net", "io")
    private val ORIGIN = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):(?://(?:[^/?#@]*@)?(\\[[^\\]/?#]*]|[^/?#:]*))?")
}
