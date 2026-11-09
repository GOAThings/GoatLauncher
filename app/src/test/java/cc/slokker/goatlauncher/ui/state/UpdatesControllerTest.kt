package cc.slokker.goatlauncher.ui.state

import cc.slokker.goatlauncher.data.AppRelease
import cc.slokker.goatlauncher.data.AppUpdates
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/** Procurar, descarregar e instalar versões novas, e a reparação do serviço do comando, com um Updater falso. */
class UpdatesControllerTest {
    private val test = HolderScope()
    private val strings = TestStrings.create()
    private val toasts = mutableListOf<String>()
    private val waits = FakeWaits()
    private var now = 10 * 60 * 60_000L
    private var guardStuck = false
    private var sinceStart = 60_000L

    private val release = AppRelease("0.9.12", "Notas", "https://example.invalid/a.apk", 1)
    private val updater = FakeUpdater()

    private val updates = UpdatesController(
        scope = test.scope,
        updater = updater,
        strings = { strings },
        toast = { toasts += it },
        isGuardStuck = { guardStuck },
        sinceProcessStart = { sinceStart },
        ownApk = { File("/data/app/base.apk") },
        clock = { now },
        wait = waits::wait,
    )

    @After
    fun tearDown() = test.cancel()

    @Test
    fun aManualCheckFindsTheNewerReleaseWithoutAToast() = runBlocking {
        updater.latest = { release }
        updates.check(manual = true)
        test.settle()
        assertEquals(UpdateState.Available(release), updates.state.value)
        assertEquals(emptyList<String>(), toasts)
    }

    @Test
    fun theAutomaticCheckSaysSoOnceAndKeepsTheStateOnErrors() = runBlocking {
        updater.latest = { release }
        updates.check(manual = false)
        test.settle()
        updates.check(manual = false)
        test.settle()
        assertEquals(listOf(strings.updateAvailable("0.9.12")), toasts)
        // Sem rede, uma verificação automática não apaga o que se sabia; uma pedida diz porquê.
        updater.latest = { throw IOException("sem rede") }
        updates.check(manual = false)
        test.settle()
        assertEquals(UpdateState.Available(release), updates.state.value)
        updates.check(manual = true)
        test.settle()
        assertEquals(UpdateState.Failed("sem rede"), updates.state.value)
    }

    @Test
    fun anOlderOrEqualReleaseIsUpToDate() = runBlocking {
        updater.latest = { AppRelease("0.9.11", "", "u", 1) }
        updates.check(manual = true)
        test.settle()
        assertEquals(UpdateState.UpToDate, updates.state.value)
        updater.latest = { null }
        updates.check(manual = true)
        test.settle()
        assertEquals(UpdateState.UpToDate, updates.state.value)
    }

    @Test
    fun aDebugBuildDoesNotCheck() = runBlocking {
        updater.enabled = false
        updates.check(manual = true)
        updates.check(manual = false)
        test.settle()
        assertEquals(UpdateState.Idle, updates.state.value)
        assertEquals(0, updater.latestCalls)
        assertEquals(listOf(strings.updatesOnlyRelease), toasts)
    }

    @Test
    fun installingDownloadsThenHandsTheApkToTheInstaller() = runBlocking {
        updater.latest = { release }
        updates.check(manual = true)
        test.settle()
        val seen = mutableListOf<UpdateState>()
        updater.onDownload = { seen += updates.state.value }
        updates.install()
        test.settle()
        assertEquals(listOf(UpdateState.Downloading(release, 0f), UpdateState.Downloading(release, 0.5f)), seen)
        assertEquals(UpdateState.Installing(release), updates.state.value)
        assertEquals(listOf(File("/cache/a.apk")), updater.installed)
        // Enquanto instala, uma verificação não mexe no estado.
        updates.check(manual = true)
        assertEquals(UpdateState.Installing(release), updates.state.value)
    }

    @Test
    fun withoutInstallPermissionItOpensTheAndroidScreen() = runBlocking {
        updater.latest = { release }
        updates.check(manual = true)
        test.settle()
        updater.canInstall = false
        updates.install()
        test.settle()
        assertEquals(listOf(strings.allowInstall), toasts)
        assertEquals(1, updater.permissionScreens)
        assertEquals(UpdateState.Available(release), updates.state.value)
    }

    @Test
    fun theInstallerAnswersArriveThroughStart() = runBlocking {
        updater.latest = { release }
        updates.start()
        // O falso responde logo: tudo corre já (o start deixa ciclos à espera, por isso sem settle).
        updates.check(manual = true)
        updates.install()
        assertEquals(UpdateState.Installing(release), updates.state.value)
        // Cancelar a confirmação do Android volta a pôr a versão disponível (B1).
        updater.cancels.emit(Unit)
        assertEquals(UpdateState.Available(release), updates.state.value)
        updater.failures.emit("INSTALL_FAILED_X")
        assertEquals(UpdateState.Failed("INSTALL_FAILED_X"), updates.state.value)
    }

    @Test
    fun theAutomaticChecksRun20sAfterStartThenEvery12h() = runBlocking {
        updater.latest = { release }
        updates.start()
        assertEquals(listOf(20_000L), waits.asked)
        assertEquals(0, updater.latestCalls)
        waits.release()
        assertEquals(1, updater.latestCalls)
        assertEquals(listOf(20_000L, 12 * 60 * 60_000L), waits.asked)
        waits.release()
        assertEquals(2, updater.latestCalls)
    }

    @Test
    fun aStuckGuardIsRepairedOncePerHour() = runBlocking {
        guardStuck = true
        updates.checkGuard()
        test.settle()
        assertTrue(updates.guardStuck.value)
        assertEquals(listOf(strings.guardRepairing), toasts)
        assertEquals(listOf(File("/data/app/base.apk")), updater.installed)
        // De volta ao ecrã inicial pouco depois (a pessoa cancelou): não volta a pedir.
        now += 30 * 60_000L
        updates.checkGuard()
        test.settle()
        assertEquals(1, updater.installed.size)
        now += 31 * 60_000L
        updates.checkGuard()
        test.settle()
        assertEquals(2, updater.installed.size)
    }

    @Test
    fun aStuckGuardWithoutInstallPermissionOnlyWarns() = runBlocking {
        guardStuck = true
        updater.canInstall = false
        updates.checkGuard()
        test.settle()
        assertEquals(listOf(strings.guardStuckToast), toasts)
        assertEquals(emptyList<File>(), updater.installed)
    }

    @Test
    fun rightAfterStartTheGuardCheckWaitsForTheServiceToBind() = runBlocking {
        sinceStart = 5_000L
        guardStuck = true
        updates.checkGuard()
        assertEquals(listOf(15_000L), waits.asked)
        assertEquals(false, updates.guardStuck.value)
        waits.release()
        assertTrue(updates.guardStuck.value)
    }

    private class FakeUpdater : AppUpdates {
        override var enabled = true
        override val currentVersion = "0.9.11"
        val failures = MutableSharedFlow<String>()
        val cancels = MutableSharedFlow<Unit>()
        override val installFailures = failures
        override val installCancels = cancels
        var latest: suspend () -> AppRelease? = { null }
        var latestCalls = 0
        var canInstall = true
        var permissionScreens = 0
        var onDownload: () -> Unit = {}
        val installed = mutableListOf<File>()

        override suspend fun latest(): AppRelease? {
            latestCalls++
            return latest.invoke()
        }

        override suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): File {
            onDownload()
            onProgress(0.5f)
            onDownload()
            return File("/cache/a.apk")
        }

        override fun canInstall() = canInstall
        override fun openInstallPermission() {
            permissionScreens++
        }

        override suspend fun install(apk: File) {
            installed += apk
        }
    }
}
