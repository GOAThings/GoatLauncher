package cc.slokker.goatlauncher.ui.state

import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import cc.slokker.goatlauncher.ui.Strings
import java.util.Locale

/**
 * Textos para os testes no PC (como na GoatTV): cada um é o id do recurso com os argumentos, para se poder comparar sem
 * as traduções (ex.: `strings.reportTooMany` dá sempre o mesmo texto).
 */
object TestStrings {
    fun create(): Strings {
        val res = object : Resources(null, null, null) {
            override fun getString(id: Int): String = "s$id"
            override fun getString(id: Int, vararg formatArgs: Any?): String = "s$id" + formatArgs.joinToString(",", "(", ")")
            override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any?): String =
                "q$id" + formatArgs.joinToString(",", "(", ")")
            override fun getConfiguration(): Configuration = Configuration().apply { @Suppress("DEPRECATION") locale = Locale.ENGLISH }
        }
        val context = object : ContextWrapper(null) {
            override fun getResources(): Resources = res
        }
        return Strings(context)
    }
}
