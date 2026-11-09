package cc.slokker.goatlauncher.ui.state

import cc.slokker.goatlauncher.data.ClientInfo
import cc.slokker.goatlauncher.data.FakeSettingsBackend
import cc.slokker.goatlauncher.data.FakeSettingsBackend.Companion.ok
import cc.slokker.goatlauncher.data.FakeSettingsBackend.Companion.status
import cc.slokker.goatlauncher.data.SessionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** O relatório para o site (`POST api/reports`): o corpo, os segredos que nunca saem da TV e as respostas. */
class ReportSenderTest {
    private val test = HolderScope()
    private val api = FakeSettingsBackend()
    private val strings = TestStrings.create()
    private val session = MutableStateFlow<SessionStore.Session?>(null)
    private val headers = mutableListOf<String>()
    private val secretsSeen = mutableListOf<List<String>>()

    private val sender = ReportSender(
        scope = test.scope,
        backend = api,
        client = ClientInfo("0.9.12", "fire-tv"),
        device = "Android 11 · NVIDIA SHIELD",
        session = session,
        layout = { "android" },
        youtubeSecrets = { listOf("yt-refresh") },
        strings = { strings },
        collectLog = { header, secrets ->
            headers += header
            secretsSeen += secrets
            "o registo"
        },
    )

    @Test
    fun theBodyHasTheAppVersionPlatformDeviceMessageAndLog() = runBlocking {
        api.reports += { ok("""{"id":"r1"}""") }
        sender.send("  O fundo não muda  ")
        test.settle()
        val body = api.sentReports.single()
        assertEquals(
            setOf("app", "version", "platform", "device", "message", "log"),
            body.keySet(),
        )
        assertEquals("goatlauncher", body.get("app").asString)
        assertEquals("0.9.12", body.get("version").asString)
        assertEquals("fire-tv", body.get("platform").asString)
        assertEquals("Android 11 · NVIDIA SHIELD", body.get("device").asString)
        assertEquals("O fundo não muda", body.get("message").asString)
        assertEquals("o registo", body.get("log").asString)
        assertEquals("GoatLauncher 0.9.12 · Android 11 · NVIDIA SHIELD\nAccount: no · layout: android", headers.single())
        assertEquals(ReportState.Sent("r1"), sender.state.value)
    }

    @Test
    fun anEmptyMessageIsLeftOutAndALongOneIsCut() = runBlocking {
        api.reports += { ok("""{"id":"r1"}""") }
        api.reports += { ok("""{"id":"r2"}""") }
        sender.send("   ")
        test.settle()
        sender.send("x".repeat(2500))
        test.settle()
        assertFalse(api.sentReports[0].has("message"))
        assertEquals(2000, api.sentReports[1].get("message").asString.length)
    }

    @Test
    fun theAccountTokenEmailAndYouTubeTokensAreRedacted() = runBlocking {
        session.value = SessionStore.Session("t1", "pessoa@example.com", "Sala")
        api.reports += { ok("""{"id":"r1"}""") }
        sender.send("")
        test.settle()
        assertEquals(listOf("t1", "pessoa@example.com", "yt-refresh"), secretsSeen.single())
        assertTrue(headers.single().endsWith("Account: yes · layout: android"))
    }

    @Test
    fun tooManyReportsAndOtherFailuresSayWhy() = runBlocking {
        api.reports += { status(429) }
        sender.send("a")
        test.settle()
        assertEquals(ReportState.Failed(strings.reportTooMany), sender.state.value)

        api.reports += { ok("{}") }
        sender.send("a")
        test.settle()
        assertEquals(ReportState.Failed(strings.reportFailed("HTTP 200")), sender.state.value)

        api.reports += { throw java.io.IOException("sem rede") }
        sender.send("a")
        test.settle()
        assertEquals(ReportState.Failed(strings.reportFailed("sem rede")), sender.state.value)
    }

    @Test
    fun aSecondPressWhileSendingSendsNothing() = runBlocking {
        val answer = CompletableDeferred<Unit>()
        api.reports += { answer.await(); ok("""{"id":"r1"}""") }
        sender.send("a")
        assertEquals(ReportState.Sending, sender.state.value)
        sender.send("b")
        answer.complete(Unit)
        test.settle()
        assertEquals(1, api.sentReports.size)
        assertEquals(ReportState.Sent("r1"), sender.state.value)
    }
}
