package cc.slokker.goatlauncher.ui.state

/** O envio de um relatório (Definições → Enviar relatório). */
sealed interface ReportState {
    data object Idle : ReportState
    data object Sending : ReportState
    data class Sent(val id: String) : ReportState
    data class Failed(val message: String) : ReportState
}
