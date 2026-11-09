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

/** O que os holders lançam corre já (como no Main.immediate), até esperar por alguma coisa; [settle] espera o resto. */
internal class HolderScope {
    val job = kotlinx.coroutines.SupervisorJob()
    val scope = kotlinx.coroutines.CoroutineScope(job + kotlinx.coroutines.Dispatchers.Unconfined)

    suspend fun settle() {
        while (true) {
            val running = job.children.filter { it.isActive }.toList()
            if (running.isEmpty()) return
            running.forEach { it.join() }
        }
    }

    fun cancel() = job.cancel()
}
