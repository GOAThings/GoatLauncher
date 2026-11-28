package cc.slokker.goatlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackdropsTest {
    private val base = "https://media.example/jellyfin/abc/Items/a1110100-0000-1323-3fff-ffffff010000/Images/"

    @Test
    fun thumbBecomesBackdropWithoutTheTag() {
        assertEquals(
            "${base}Backdrop?maxWidth=1920&quality=90",
            Backdrops.backdropOf("${base}Thumb?tag=c079dd176d2592cbcc7c&quality=90&maxWidth=640"),
        )
    }

    @Test
    fun primaryWithIndexAndNoQuery() {
        assertEquals("${base}Backdrop?maxWidth=1920&quality=90", Backdrops.backdropOf("${base}Primary/0"))
    }

    @Test
    fun keepsTheApiKey() {
        assertEquals(
            "${base}Backdrop?api_key=k1&maxWidth=1920&quality=90",
            Backdrops.backdropOf("${base}Thumb?tag=x&api_key=k1"),
        )
    }

    @Test
    fun aioMetadataEpisodeGetsTheShowBackdrop() {
        val items = "https://media.example/jellyfin/abc/Items/"
        assertEquals(
            "${items}a1210200-0000-0707-6900-ffffff010000/Images/Backdrop?maxWidth=1920&quality=90",
            Backdrops.showBackdropOf("${items}a1410200-0000-0707-6900-020001010000/Images/Primary?tag=x&maxWidth=640"),
        )
    }

    @Test
    fun otherEpisodesHaveNoShowBackdrop() {
        val items = "https://media.example/jellyfin/abc/Items/"
        assertNull(Backdrops.showBackdropOf("${items}6f1d2c3b4a5948e7b6c5d4e3f2a1b0c9/Images/Primary"))
        assertNull(Backdrops.showBackdropOf("${items}a1110100-0000-1323-3fff-ffffff010000/Images/Thumb"))
    }

    @Test
    fun otherImagesStay() {
        assertNull(Backdrops.backdropOf("${base}Backdrop?maxWidth=1280"))
        assertNull(Backdrops.backdropOf("${base}Logo"))
        assertNull(Backdrops.backdropOf("https://image.tmdb.org/t/p/w1280/abc.jpg"))
        assertNull(Backdrops.backdropOf("http://127.0.0.1:8765/logos/daily.png"))
    }
}
