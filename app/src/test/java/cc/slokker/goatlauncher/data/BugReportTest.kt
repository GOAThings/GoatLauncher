package cc.slokker.goatlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BugReportTest {

    @Test
    fun redactsKnownSecretsAndCredentialShapes() {
        val log = """
            Opening http://iptv.example:8080/live/joao/s3cr3t/123.ts
            GET http://iptv.example/player_api.php?username=joao&password=s3cr3t&action=get_live_streams
            M3U https://lists.example/get.php?token=abcdef123&type=m3u
            Proxy http://bob:hunter2@proxy.example/x
            Authorization: tok_live_999 sent
            Error with tok_live_999 and myplaylistpass
        """.trimIndent()
        val out = BugReport.redact(log, listOf("tok_live_999", "myplaylistpass", "ab"))
        for (secret in listOf("joao", "s3cr3t", "abcdef123", "hunter2", "tok_live_999", "myplaylistpass", "bob:")) {
            assertFalse("$secret leaked:\n$out", out.contains(secret))
        }
        assertTrue(out.contains("http://iptv.example:8080/live/***/***/123.ts"))
        assertTrue(out.contains("action=get_live_streams"))
        // Segredos de menos de 3 caracteres não se trocam (senão apagava letras soltas).
        assertTrue(out.contains("Opening"))
    }

    @Test
    fun redactsSignedAndPrefixedCredentialShapes() {
        for (line in listOf(
            "https://cdn.example/p.jpg?Expires=1&Signature=DUMMY&Key-Pair-Id=DUMMY",
            "https://DUMMY@media.example/p.jpg",
            "https://cdn.example/p.jpg?auth_token=DUMMY",
            "https://cdn.example/p.jpg?X-Amz-Credential=DUMMY&refresh_token=DUMMY",
            "Authorization: Bearer DUMMY",
        )) assertFalse(line, BugReport.redact(line, emptyList()).contains("DUMMY"))
    }

    @Test
    fun otherAppsUrisAreReducedToSchemeAndHost() {
        assertEquals("https://cdn.example", BugReport.originOf("https://cdn.example/p.jpg?Signature=DUMMY"))
        assertEquals("http://media.example", BugReport.originOf("http://media.example:8080/U/DUMMY/1.jpg"))
        assertEquals("https://media.example", BugReport.originOf("https://DUMMY:DUMMY@media.example/p.jpg"))
        assertEquals("http://[fd00::1]", BugReport.originOf("http://[fd00::1]:80/DUMMY"))
        assertEquals("content://app.provider", BugReport.originOf("content://app.provider/DUMMY/1"))
        assertEquals("data:", BugReport.originOf("data:image/png;base64,DUMMY"))
        assertEquals("?", BugReport.originOf("DUMMY"))
        assertEquals("?", BugReport.originOf(null))
    }

    @Test
    fun keepsTheNewestLinesThatFit() {
        val log = (1..100).joinToString("\n") { "line $it" }
        val out = BugReport.trim("HEAD\n", log, max = 60)
        assertTrue(out.startsWith("HEAD\n"))
        assertTrue(out.endsWith("line 100"))
        assertFalse(out.contains("line 1\n"))
        assertEquals(true, out.toByteArray().size <= 60)
    }
}
