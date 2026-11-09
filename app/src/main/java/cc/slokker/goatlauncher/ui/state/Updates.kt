package cc.slokker.goatlauncher.ui.state

import cc.slokker.goatlauncher.data.AppRelease

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    data class Installing(val release: AppRelease) : UpdateState
    data class Failed(val message: String) : UpdateState

    /**
     * O estado depois de a pessoa cancelar a confirmação do Android: a versão volta a estar disponível. Ficar em
     * [Installing] deixava as Definições em "A instalar…" e parava as verificações automáticas.
     */
    fun cancelled(): UpdateState = if (this is Installing) Available(release) else this
}
