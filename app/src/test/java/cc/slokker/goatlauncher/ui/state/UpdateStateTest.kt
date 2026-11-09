package cc.slokker.goatlauncher.ui.state

import cc.slokker.goatlauncher.data.AppRelease
import org.junit.Assert.assertEquals
import org.junit.Test

/** As atualizações quando a pessoa cancela a confirmação do instalador do Android (STATUS_FAILURE_ABORTED). */
class UpdateStateTest {
    private val release = AppRelease("0.9.12", "Notas", "https://example.invalid/a.apk", 1)

    @Test
    fun cancellingTheInstallPromptMakesTheReleaseAvailableAgain() {
        // Antes ficava em Installing para sempre: a linha das Definições não fazia nada e as verificações paravam.
        assertEquals(UpdateState.Available(release), UpdateState.Installing(release).cancelled())
    }

    @Test
    fun aCancelOutsideAnInstallChangesNothing() {
        // Ex.: a reinstalação que repara o serviço de acessibilidade (repairGuard) também passa pelo instalador.
        for (state in listOf(
            UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate, UpdateState.Available(release),
            UpdateState.Downloading(release, 0.5f), UpdateState.Failed("x"),
        )) assertEquals(state, state.cancelled())
    }
}
