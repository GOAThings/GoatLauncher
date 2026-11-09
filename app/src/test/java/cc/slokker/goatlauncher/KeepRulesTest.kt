package cc.slokker.goatlauncher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * No release o R8 tira (ou muda o nome a) campos que só o Gson escreve por reflexão. Cada classe lida com
 * `gson.fromJson(…, X::class.java)` tem de estar nas regras de `keepRules/rules.keep`, senão chega cheia de nulls (foi o
 * que aconteceu ao Updater: o release nunca via versões novas).
 */
class KeepRulesTest {
    @Test
    fun everyClassGsonReadsByReflectionIsKept() {
        val rules = File("src/main/keepRules/rules.keep").readLines().filter { it.startsWith("-keep class ") }
            .map { it.removePrefix("-keep class ").substringBefore(' ') }
        fun kept(name: String) = rules.any { rule -> if (rule.endsWith(".**")) name.startsWith(rule.removeSuffix("**")) else rule == name }
        val missing = File("src/main/java").walk().filter { it.extension == "kt" }.flatMap { file ->
            val text = file.readText()
            val pkg = Regex("""^package (\S+)""", RegexOption.MULTILINE).find(text)!!.groupValues[1]
            val outer = file.nameWithoutExtension
            Regex("""fromJson\(.*?(\w+)::class\.java\)""").findAll(text).map { m ->
                val cls = m.groupValues[1]
                // As classes aninhadas (ex.: Updater.GithubRelease) ficam "Externa$Interna" no dex.
                if (Regex("""class $cls\b""").find(text) != null && Regex("""^(class|object) $cls\b""", RegexOption.MULTILINE).find(text) == null) {
                    "$pkg.$outer\$$cls"
                } else {
                    "$pkg.$cls"
                }
            }
        }.filterNot(::kept).toList()
        assertEquals("", missing.joinToString("\n"))
    }
}
