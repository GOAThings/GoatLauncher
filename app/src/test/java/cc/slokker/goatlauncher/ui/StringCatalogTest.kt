package cc.slokker.goatlauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Os textos do launcher: `res/values…/strings_ui.xml` (o inglês em `values`), as línguas do seletor e o [Strings].
 * Cada verificação falha com a lista do que está mal. Juntar um texto: a chave nos 16 ficheiros e o acessor no Strings.
 */
class StringCatalogTest {
    private val res = File("src/main/res")
    private val code = File("src/main/java").walk().filter { it.extension == "kt" }.associateWith { it.readText() }
    private val stringsKt = code.entries.single { it.key.name == "Strings.kt" }

    /** Um texto ou um plural (as quantidades e os textos de cada uma). */
    private sealed interface Entry {
        data class Text(val value: String) : Entry
        data class Plural(val items: Map<String, String>) : Entry
    }

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    private fun read(file: File, translatableOnly: Boolean = false): Map<String, Entry> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val texts = root.children("string")
            .filterNot { translatableOnly && it.getAttribute("translatable") == "false" }
            .associate { it.getAttribute("name") to Entry.Text(it.textContent) }
        val plurals = root.children("plurals").associate { p ->
            p.getAttribute("name") to Entry.Plural(p.children("item").associate { it.getAttribute("quantity") to it.textContent })
        }
        return texts + plurals
    }

    private val base = read(File(res, "values/strings_ui.xml"))

    /** As pastas de cada língua (sem o inglês), pelo nome. */
    private val locales: Map<String, Map<String, Entry>> = res.listFiles()!!
        .filter { it.name.startsWith("values-") && File(it, "strings_ui.xml").exists() }
        .associate { it.name to read(File(it, "strings_ui.xml")) }

    private fun placeholders(text: String): List<String> = PLACEHOLDER.findAll(text).map { it.value }.sorted().toList()

    @Test
    fun everyLocaleHasExactlyTheEnglishKeys() {
        val problems = locales.flatMap { (folder, entries) ->
            (base.keys - entries.keys).map { "$folder: falta $it" } + (entries.keys - base.keys).map { "$folder: a mais $it" }
        }
        assertEquals("", problems.joinToString("\n"))
    }

    @Test
    fun placeholdersMatchEnglish() {
        val problems = mutableListOf<String>()
        for ((folder, entries) in locales) for ((key, entry) in entries) {
            when (val en = base[key]) {
                is Entry.Text -> if (entry is Entry.Text && placeholders(entry.value) != placeholders(en.value)) problems += "$folder: $key"
                is Entry.Plural -> if (entry is Entry.Plural) {
                    val expected = placeholders(en.items.getValue("other"))
                    for ((quantity, text) in entry.items) {
                        // O "one" pode trocar o número por uma palavra ("1 app" / "una app"), mas nunca juntar outro.
                        val found = placeholders(text)
                        if (found != expected && !(quantity != "other" && expected.containsAll(found))) problems += "$folder: $key/$quantity"
                    }
                }
                null -> Unit
            }
        }
        assertEquals("", problems.joinToString("\n"))
    }

    @Test
    fun everyPluralHasOther() {
        val problems = (locales + ("values" to base)).flatMap { (folder, entries) ->
            entries.filterValues { it is Entry.Plural && "other" !in it.items }.keys.map { "$folder: $it" }
        }
        assertEquals("", problems.joinToString("\n"))
    }

    @Test
    fun languagesMatchTheFoldersAndTheLocaleConfig() {
        val codes = Strings.LANGUAGES.map { it.first }
        val config = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(File(res, "xml/locales_config.xml")).documentElement.children("locale")
            .map { it.getAttributeNS("http://schemas.android.com/apk/res/android", "name") }
        assertEquals(codes, config)
        // values-in é o indonésio (id), values-zh-rCN o zh-CN; values-pt e values-pt-rPT são os dois o português de Portugal.
        val fromFolders = locales.keys.map { folder ->
            when (val q = folder.removePrefix("values-").replace("-r", "-")) {
                "in" -> "id"
                "pt" -> "pt-PT"
                else -> q
            }
        }.toSet()
        assertEquals((codes - "en").toSet(), fromFolders)
        assertEquals(locales.getValue("values-pt"), locales.getValue("values-pt-rPT"))
    }

    @Test
    fun systemTextsAreTranslatedToo() {
        // strings.xml: os textos que o Android mostra (o serviço de acessibilidade). Só pt-rPT cai no values-pt.
        val keys = read(File(res, "values/strings.xml"), translatableOnly = true).keys
        val problems = locales.keys.filter { it != "values-pt-rPT" }.flatMap { folder ->
            val file = File(res, "$folder/strings.xml")
            val found = if (file.exists()) read(file).keys else emptySet()
            (keys - found).map { "$folder: falta $it" }
        }
        assertEquals("", problems.joinToString("\n"))
    }

    @Test
    fun everyKeyIsUsed() {
        val kotlin = code.values.joinToString("\n")
        val xml = res.walk().filter { it.extension == "xml" && !it.name.startsWith("strings") }.joinToString("\n") { it.readText() } +
            File("src/main/AndroidManifest.xml").readText()
        val unused = base.keys.filterNot { key ->
            Regex("""R\.(string|plurals)\.$key\b""").containsMatchIn(kotlin) || "@string/$key\"" in xml
        }
        assertEquals("", unused.joinToString("\n"))
    }

    @Test
    fun everyAccessorIsUsed() {
        val body = stringsKt.value.substringAfter("class Strings(").substringBefore("    companion object")
        val members = Regex("""^    (?:val|fun) (\w+)""", RegexOption.MULTILINE).findAll(body).map { it.groupValues[1] }.toList()
        val others = code.filterKeys { it != stringsKt.key }.values.joinToString("\n")
        val unused = members.filterNot { Regex("""(\.|::)$it\b""").containsMatchIn(others) }
        assertEquals("", unused.joinToString("\n"))
    }

    private companion object {
        val PLACEHOLDER = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfxX%]""")
    }
}
