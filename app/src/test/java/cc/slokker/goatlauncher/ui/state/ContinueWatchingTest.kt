package cc.slokker.goatlauncher.ui.state

import androidx.compose.ui.graphics.Color
import cc.slokker.goatlauncher.data.DeviceSettings
import cc.slokker.goatlauncher.data.LauncherApp
import cc.slokker.goatlauncher.data.WatchNextFeed
import cc.slokker.goatlauncher.data.WatchNextItem
import cc.slokker.goatlauncher.data.YouTubeAccount
import cc.slokker.goatlauncher.data.YouTubeLogin
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * O "Continuar a ver": juntar as leituras seguidas do Watch Next, o YouTube na app certa, as apps em que a pessoa o
 * desligou, o exemplo de debug e a ligação à conta YouTube. Com o Watch Next e o YouTube falsos.
 */
class ContinueWatchingTest {
    private val test = HolderScope()
    private val waits = FakeWaits()
    private val strings = TestStrings.create()
    private val toasts = mutableListOf<String>()
    private val prefetched = mutableListOf<Set<String>>()
    private var now = 1_000_000L

    private fun app(pkg: String) = LauncherApp(pkg, pkg, null, null, Color.Black, null)
    private fun item(pkg: String, id: Long) = WatchNextItem(id, pkg, "T$id", null, null, null, null, 16f / 9f, 0, 0, 0, 0, null)

    private val installed = MutableStateFlow<List<LauncherApp>?>(null)
    private val device = MutableStateFlow(DeviceSettings())
    private val feed = FakeFeed()
    private val youtube = FakeYouTube()
    private var demoAllowed = true

    private val watching by lazy {
        ContinueWatching(
            scope = test.scope,
            source = feed,
            youtube = youtube,
            installed = installed,
            device = device,
            homeApps = { listOf(app("tv.home.one"), app("tv.home.two"), app("tv.home.three")) },
            youtubeFeed = { "both" },
            prefetch = { prefetched += it.keys },
            strings = { strings },
            toast = { toasts += it },
            demoAllowed = demoAllowed,
            clock = { now },
            wait = waits::wait,
        )
    }

    @After
    fun tearDown() = test.cancel()

    @Test
    fun burstsOfChangesAreReadOnce300msAfterTheLast() {
        feed.rows = mapOf("tv.a" to listOf(item("tv.a", 1)))
        watching.start()
        assertEquals(listOf(300L), waits.asked)
        // Mais mudanças a meio da espera recomeçam-na (a app ainda está a escrever).
        feed.onChange()
        feed.onChange()
        assertEquals(0, feed.loads)
        assertEquals(1, waits.waiting)
        waits.release()
        assertEquals(1, feed.loads)
        assertEquals(mapOf("tv.a" to listOf(item("tv.a", 1))), watching.watchNext.value)
        assertEquals(listOf(setOf("tv.a")), prefetched)
    }

    @Test
    fun aFailedReadKeepsWhatWasThere() {
        feed.rows = mapOf("tv.a" to listOf(item("tv.a", 1)))
        watching.start()
        waits.release()
        feed.rows = null
        watching.reload()
        waits.release()
        assertEquals(setOf("tv.a"), watching.watchNext.value.keys)
    }

    @Test
    fun appsWithContinueWatchingOffAreLeftOut() {
        feed.rows = mapOf("tv.a" to listOf(item("tv.a", 1)), "tv.b" to listOf(item("tv.b", 2)))
        watching.start()
        waits.release()
        device.value = DeviceSettings(noContinue = setOf("tv.b"))
        assertEquals(setOf("tv.a"), watching.watchNext.value.keys)
    }

    @Test
    fun youTubeGoesAfterTheAppsOwnRowsInThePreferredYouTubeApp() {
        val yt = "com.google.android.youtube.tv"
        assertEquals(
            mapOf(yt to listOf(item(yt, 1), item(yt, 9))),
            ContinueWatching.merged(mapOf(yt to listOf(item(yt, 1))), listOf(item(yt, 9)), emptySet()),
        )
        youtube.linked = true
        youtube.items = { pkg -> listOf(item(pkg, 9)) }
        installed.value = listOf(app("com.liskovsoft.smarttubetv.beta"), app(yt), app("io.gh.reisxd.tizentube"))
        watching.start()
        // A TizenTube vem antes da app oficial e da SmartTube.
        assertEquals(listOf("io.gh.reisxd.tizentube" to "both"), youtube.asked)
        assertEquals(listOf(item("io.gh.reisxd.tizentube", 9)), watching.watchNext.value["io.gh.reisxd.tizentube"])
        assertTrue(setOf("io.gh.reisxd.tizentube") in prefetched)
    }

    @Test
    fun youTubeWaitsForTheAppListAndIsRefreshedWhenStale() {
        youtube.linked = true
        youtube.items = { pkg -> listOf(item(pkg, 9)) }
        watching.start()
        assertEquals(0, youtube.asked.size)
        installed.value = listOf(app("com.google.android.youtube.tv"))
        assertEquals(1, youtube.asked.size)
        now += 5 * 60_000L
        watching.onResume()
        assertEquals(1, youtube.asked.size)
        now += 6 * 60_000L
        watching.onResume()
        assertEquals(2, youtube.asked.size)
    }

    @Test
    fun withoutAYouTubeAccountThereAreNoRecommendations() {
        installed.value = listOf(app("com.google.android.youtube.tv"))
        watching.start()
        assertEquals(0, youtube.asked.size)
        assertFalse(watching.youtubeLinked.value)
    }

    @Test
    fun theDemoFillsTheFirstTwoHomeAppsOnlyInDebug() {
        feed.rows = emptyMap()
        watching.start()
        waits.release()
        watching.enableDemo()
        waits.release()
        assertEquals(setOf("tv.home.one", "tv.home.two"), watching.watchNext.value.keys)
        assertEquals(3, watching.watchNext.value.getValue("tv.home.one").size)
    }

    @Test
    fun theDemoIsOffInRelease() {
        demoAllowed = false
        feed.rows = emptyMap()
        watching.start()
        waits.release()
        watching.enableDemo()
        assertEquals(0, waits.waiting)
        assertEquals(emptyMap<String, List<WatchNextItem>>(), watching.watchNext.value)
    }

    @Test
    fun linkingYouTubeRetriesAfterAnErrorAndPollsUntilConfirmed() {
        installed.value = listOf(app("com.google.android.youtube.tv"))
        youtube.items = { pkg -> listOf(item(pkg, 9)) }
        val login = YouTubeLogin("ABCD-EFGH", "https://www.google.com/device", "dev", 5, System.currentTimeMillis() + 60_000)
        youtube.logins += { throw IOException("sem rede") }
        youtube.logins += { login }
        youtube.polls += null
        youtube.polls += true
        watching.startYouTubeLogin()
        assertEquals(listOf(strings.youtubeError), toasts)
        assertEquals(listOf(10_000L), waits.asked)
        waits.release()
        assertEquals(login, watching.youtubeLogin.value)
        assertEquals(5_000L, waits.asked.last())
        waits.release() // ainda à espera da pessoa
        waits.release() // confirmado
        assertTrue(watching.youtubeLinked.value)
        assertNull(watching.youtubeLogin.value)
        assertEquals(listOf(strings.youtubeError, strings.youtubeLinked), toasts)
        assertEquals(setOf("com.google.android.youtube.tv"), watching.watchNext.value.keys)
    }

    @Test
    fun unlinkingYouTubeClearsItsRows() {
        youtube.linked = true
        youtube.items = { pkg -> listOf(item(pkg, 9)) }
        installed.value = listOf(app("com.google.android.youtube.tv"))
        watching.start()
        assertTrue(watching.watchNext.value.isNotEmpty())
        watching.unlinkYouTube()
        assertFalse(watching.youtubeLinked.value)
        assertFalse(youtube.linked)
        assertTrue(watching.watchNext.value.isEmpty())
    }

    @Test
    fun stopUnregistersTheObserver() {
        watching.start()
        assertTrue(feed.observing)
        watching.stop()
        assertFalse(feed.observing)
    }

    private class FakeFeed : WatchNextFeed {
        var rows: Map<String, List<WatchNextItem>>? = emptyMap()
        var loads = 0
        var onChange: () -> Unit = {}
        var observing = false

        override suspend fun load(): Map<String, List<WatchNextItem>>? {
            loads++
            return rows
        }

        override fun observe(onChange: () -> Unit): () -> Unit {
            this.onChange = onChange
            observing = true
            return { observing = false }
        }
    }

    private class FakeYouTube : YouTubeAccount {
        override var linked = false
        var items: (String) -> List<WatchNextItem> = { emptyList() }
        val asked = mutableListOf<Pair<String, String>>()
        val logins = ArrayDeque<() -> YouTubeLogin>()
        val polls = ArrayDeque<Boolean?>()

        override fun unlink() {
            linked = false
        }

        override suspend fun startLogin(): YouTubeLogin = logins.removeFirst()()

        override suspend fun poll(login: YouTubeLogin): Boolean? = polls.removeFirst().also { if (it == true) linked = true }

        override suspend fun recommendations(packageName: String, feed: String, max: Int): List<WatchNextItem> {
            asked += packageName to feed
            return items(packageName)
        }
    }
}
