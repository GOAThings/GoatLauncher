package cc.slokker.goatlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun fixture(name: String): String =
    requireNotNull(PhotoFeedsTest::class.java.classLoader!!.getResource(name)) { name }.readText()

/** As fontes de fotos do fundo, com respostas verdadeiras guardadas em `src/test/resources`. */
class PhotoFeedsTest {
    // apod-basic.xml: 9 dias do feed. Witch Head (3000 px), Fireball (sem tamanho), De Gerlache (1500 px, pequena),
    // Swift (5252 px), Ganymede e Io (vídeo), Rosetta (quadrada), Eagle (YouTube), ISS (vídeo), Manhattan (ao alto).
    private val apod = ApodFeed.parse(fixture("apod-basic.xml"))

    @Test
    fun apodKeepsOnlyLandscapePhotos() {
        assertEquals(
            listOf("Rigel and the Witch Head Nebula", "Major Fireball Meteor", "A Farewell to Swift"),
            apod.map { it.title },
        )
    }

    @Test
    fun apodSkipsVideoDays() {
        val titles = apod.map { it.title }
        assertFalse(titles.any { "Ganymede" in it || "Sonified" in it || "Space Station" in it })
    }

    @Test
    fun apodAsksForATvSizedImage() {
        val swift = apod.first { "Swift" in it.title }
        assertEquals(
            "https://assets.science.nasa.gov/dynamicimage/assets/science/cds/apod/apod/2026/november/" +
                "Swift_Millionth_Image_Mosaic.jpg?w=3840&fit=clip",
            swift.url,
        )
        // Mais pequena do que 4K (ou sem tamanho): a original, sem ampliar.
        assertTrue(apod.first { "Witch" in it.title }.url.endsWith("/WitchHead_Czerski_3000.jpg"))
        assertTrue(apod.first { "Fireball" in it.title }.url.endsWith("/meteor2017_Taylor_1200.jpg"))
    }

    @Test
    fun apodAlwaysHasTheCredit() {
        assertEquals("Piotr Czerski", apod.first { "Witch" in it.title }.credit)
        assertEquals("NASA/Swift and AndreaMosaic", apod.first { "Swift" in it.title }.credit)
        assertTrue(apod.all { !it.credit.isNullOrBlank() })
    }

    // wikimedia-potd.json: 13 dias da imagem do dia, com um ficheiro que já não existe (sem imageinfo), um SVG,
    // um dia sem imagem e fotos ao alto.
    private val potd = WikimediaFeed.parse(fixture("wikimedia-potd.json"))

    @Test
    fun wikimediaSkipsMissingFilesDrawingsAndPortraits() {
        assertTrue(potd.isNotEmpty())
        assertTrue(potd.none { "Santa_Mar%C3%ADa_Reina" in it.url || "Santa María Reina" in it.title })
        assertTrue(potd.none { it.url.endsWith(".svg") || it.url.endsWith(".svg.png") })
        // A Església Santa Margalida (7000×7824) e a capa de 1964 (6989×9143) são ao alto.
        assertTrue(potd.none { "Margalida" in it.url || "Swing_into_books" in it.url })
        // A panorâmica dos Himalaias (7570×2200) perdia quase tudo.
        assertTrue(potd.none { "Himalayas" in it.url })
    }

    @Test
    fun wikimediaUsesTheTvSizedThumbnail() {
        val norderney = potd.first { "Norderney" in it.url }
        assertTrue(norderney.url, norderney.url.startsWith("https://"))
        assertTrue(norderney.url, "/3840px-" in norderney.url)
        assertFalse(norderney.url, '?' in norderney.url)
    }

    @Test
    fun wikimediaCaptionHasAuthorAndLicence() {
        val wombat = potd.first { "wombat" in it.url }
        assertEquals("Charles J. Sharp · CC BY-SA 4.0", wombat.credit)
        assertTrue(wombat.title, wombat.title.startsWith("Common wombat (Vombatus ursinus tasmaniensis)"))
        assertTrue(potd.all { !it.credit.isNullOrBlank() })
    }

    @Test
    fun htmlBecomesPlainText() {
        assertEquals(
            "Great Blue Heron (Ardea herodias), hunting",
            plainText("""Great Blue Heron ( <i><a href="x">Ardea herodias</a></i> ), hunting"""),
        )
        assertEquals("Rosetta's Selfie & more", plainText("Rosetta&#039;s Selfie &amp; more"))
        assertEquals("V. Höfer, D. Večerník", plainText("<a href=\"x\">V. Höfer</a>, D. Večerník"))
    }

    @Test
    fun longTextIsShortenedAtAWord() {
        assertEquals("one two…", shorten("one two three", 9))
        assertEquals("short", shorten("short", 9))
    }

    @Test
    fun aDamagedCacheReadsAsEmpty() {
        assertEquals(emptyList<OnlinePhoto>(), PhotoPool.readList("not json"))
        assertEquals(emptyList<OnlinePhoto>(), PhotoPool.readList(null))
    }

    @Test
    fun theCacheKeepsTitleAndCredit() {
        val list = listOf(OnlinePhoto("https://a/1.jpg", "One", "Ana · CC BY 4.0"), OnlinePhoto("https://a/2.jpg", "Two"))
        assertEquals(list, PhotoPool.readList(PhotoPool.writeList(list)))
        // Uma entrada sem crédito também se lê.
        val old = PhotoPool.readList("""[{"url":"https://a/x.jpg","title":"Lake"}]""")
        assertEquals("https://a/x.jpg", old.single().url)
        assertNull(old.single().credit)
    }
}
