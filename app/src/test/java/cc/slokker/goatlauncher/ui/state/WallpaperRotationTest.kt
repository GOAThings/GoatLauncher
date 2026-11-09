package cc.slokker.goatlauncher.ui.state

import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.OnlinePhoto
import cc.slokker.goatlauncher.data.OnlineWallpapers
import cc.slokker.goatlauncher.data.PhotoPicker
import cc.slokker.goatlauncher.data.Wallpaper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Os fundos que rodam: o tom certo de dia e de noite, a reserva quando não há nenhuma do tom certo, as fotos que não
 * servem (ao alto, pequenas), a troca a cada intervalo e a pasta. Com as fotos e as imagens falsas.
 */
class WallpaperRotationTest {
    private val test = HolderScope()
    private val waits = FakeWaits()
    private val account = MutableStateFlow(AccountSettings(wallpaper = Wallpaper(Wallpaper.TYPE_WIKIMEDIA, "potd")))
    private val night = MutableStateFlow(false)

    private val day1 = OnlinePhoto("https://img/day1.jpg", "Dia 1")
    private val dark1 = OnlinePhoto("https://img/dark1.jpg", "Noite 1")
    private val day2 = OnlinePhoto("https://img/day2.jpg", "Dia 2")
    private val pool = FakePool(listOf(day1, dark1, day2))
    private val images = FakeImages().apply {
        light["https://img/day1.jpg"] = 0.6
        light["https://img/day2.jpg"] = 0.7
        light["https://img/dark1.jpg"] = 0.1
    }

    private val rotation = WallpaperRotation(
        scope = test.scope,
        account = account,
        night = night,
        sources = { wp -> OnlineWallpapers.Source(pool, "potd").takeIf { wp.isOnlinePhotos } },
        images = images,
        wait = waits::wait,
    )

    @After
    fun tearDown() = test.cancel()

    @Test
    fun byDayALightPhotoAtNightADarkOne() {
        rotation.start()
        assertEquals(day1, rotation.onlinePhoto.value)
        // O botão dia/noite pede logo uma foto do tom certo (sem esperar pelo intervalo).
        night.value = true
        assertEquals(dark1, rotation.onlinePhoto.value)
        assertTrue(pool.calls.last().next)
        night.value = false
        assertEquals(day1, rotation.onlinePhoto.value)
    }

    @Test
    fun withoutAPhotoOfTheRightToneTheFirstUsableOneStays() {
        images.light["https://img/dark1.jpg"] = 0.5
        night.value = true
        rotation.start()
        // Todas claras: fica a primeira que carregou (melhor do que nenhuma).
        assertEquals(day1, rotation.onlinePhoto.value)
    }

    @Test
    fun portraitsSmallAndBrokenPhotosAreSkipped() {
        images.landscape["https://img/day1.jpg"] = false
        images.landscape["https://img/day2.jpg"] = null
        images.widths["https://img/dark1.jpg"] = 1280
        val big = OnlinePhoto("https://img/big.jpg", "Grande")
        images.light[big.url] = 0.8
        images.widths[big.url] = 3840
        pool.photos = listOf(day1, dark1, day2, big)
        rotation.start()
        assertEquals(big, rotation.onlinePhoto.value)
        // As que não servem não voltam a ser pedidas.
        assertTrue(pool.calls.last().skip.containsAll(listOf(day1.url, day2.url, dark1.url)))
    }

    @Test
    fun theLastPhotoShowsAtOnceThenRotatesAtTheInterval() {
        pool.last = day2
        pool.until = 12_345L
        pool.onScreen = { rotation.onlinePhoto.value }
        rotation.start()
        // Antes de escolher (e de carregar a escolhida), já está a da última vez.
        assertEquals(day2, pool.calls.first().onScreen)
        assertEquals(listOf(12_345L), waits.asked)
        assertEquals(false, pool.calls.first().next)
        waits.release()
        assertTrue(pool.calls.last().next)
        assertEquals(2, waits.asked.size)
    }

    @Test
    fun anotherWallpaperClearsThePhoto() {
        rotation.start()
        assertEquals(day1, rotation.onlinePhoto.value)
        account.value = AccountSettings(wallpaper = Wallpaper(Wallpaper.TYPE_BUILTIN, "lake"))
        assertNull(rotation.onlinePhoto.value)
    }

    @Test
    fun aFolderRotatesWithTheIntervalAndPrefersTheRightTone() {
        val light = File("/pasta/clara.jpg")
        val dark = File("/pasta/escura.jpg")
        images.folder = listOf(light, dark)
        images.light[light.path] = 0.8
        images.light[dark.path] = 0.1
        night.value = true
        account.value = AccountSettings(wallpaper = Wallpaper(Wallpaper.TYPE_FOLDER, "/pasta"), wallpaperMinutes = 5)
        rotation.start()
        assertEquals(dark, rotation.folderImage.value)
        assertEquals(listOf(5 * 60_000L), waits.asked)
        // A seguinte é outra (a única clara, por falta de outra escura).
        waits.release()
        assertEquals(light, rotation.folderImage.value)
        assertNull(rotation.onlinePhoto.value)
    }

    @Test
    fun eachImageToneIsMeasuredOnce() {
        rotation.start()
        night.value = true
        night.value = false
        assertEquals(1, images.measured.count { it == day1.url })
    }

    /** Uma fonte de fotos sem disco: escolhe a primeira que não está em skip (o PhotoPool escolhe ao acaso). */
    private class FakePool(var photos: List<OnlinePhoto>) : PhotoPicker {
        class Call(val next: Boolean, val skip: Set<String>, val onScreen: OnlinePhoto?)

        val calls = mutableListOf<Call>()
        var shown: OnlinePhoto? = null
        var last: OnlinePhoto? = null
        var until = 60_000L
        var onScreen: () -> OnlinePhoto? = { null }

        override suspend fun current(id: String, rotateMs: Long, next: Boolean, skip: Set<String>): OnlinePhoto? {
            calls += Call(next, skip, onScreen())
            val list = photos.filterNot { it.url in skip }
            val current = list.firstOrNull { it == shown }
            if (!next && current != null) return current
            shown = (list - setOfNotNull(current)).firstOrNull() ?: current
            return shown
        }

        override suspend fun lastShown(id: String) = last
        override fun msUntilRotation(id: String, rotateMs: Long) = until
    }

    private class FakeImages : WallpaperImages {
        val landscape = mutableMapOf<String, Boolean?>()
        val widths = mutableMapOf<String, Int>()
        val light = mutableMapOf<String, Double>()
        val measured = mutableListOf<String>()
        var folder = emptyList<File>()

        override suspend fun preload(url: String): Boolean? = if (url in landscape) landscape[url] else true
        override suspend fun originalWidth(url: String): Int? = widths[url] ?: 3840
        override suspend fun luminance(data: Any): Double? {
            val key = (data as? File)?.path ?: data.toString()
            measured += key
            return light[key]
        }

        override suspend fun folderImages(dir: File) = folder
    }
}
