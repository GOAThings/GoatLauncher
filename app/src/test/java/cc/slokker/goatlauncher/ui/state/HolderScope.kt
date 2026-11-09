package cc.slokker.goatlauncher.ui.state

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * O scope dos holders nos testes: o que lançam corre já (como no Main.immediate do viewModelScope), até esperar por
 * alguma coisa. [settle] espera o que ainda corre; não serve depois de um start() com ciclos que nunca acabam.
 */
internal class HolderScope {
    val job = SupervisorJob()
    val scope = CoroutineScope(job + Dispatchers.Unconfined)

    suspend fun settle() {
        while (true) {
            val running = job.children.filter { it.isActive }.toList()
            if (running.isEmpty()) return
            running.forEach { it.join() }
        }
    }

    fun cancel() = job.cancel()
}

/** Esperas (delay) que só acabam quando o teste quer: [asked] guarda quanto tempo cada uma pediu. */
internal class FakeWaits {
    val asked = mutableListOf<Long>()
    private val pending = ArrayDeque<CompletableDeferred<Unit>>()

    suspend fun wait(ms: Long) {
        asked += ms
        val done = CompletableDeferred<Unit>()
        pending += done
        done.await()
    }

    /** Acaba a espera mais antiga (o que estava à espera continua já). */
    fun release() {
        pending.removeFirst().complete(Unit)
    }

    val waiting: Int get() = pending.count { it.isActive }
}
