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

class LanRequestTest {
    private fun request(vararg lines: String) =
        LanRequest.read((lines.joinToString("\r\n") + "\r\n\r\n").byteInputStream())

    @Test
    fun acceptsWhatGoatRemoteSends() {
        val android = request("POST /open/a.b HTTP/1.1", "User-Agent: Dalvik/2.1.0", "Host: 192.168.1.20:6470", "Connection: Keep-Alive", "Content-Length: 0")
        assertTrue(android!!.allowed)
        assertEquals("POST", android.method)
        assertEquals("/open/a.b", android.path)
        assertTrue(request("GET /apps HTTP/1.1", "Host: 192.168.1.20", "Accept: */*")!!.allowed)
        assertTrue(request("GET /apps HTTP/1.1", "host: [fe80::1]:6470")!!.allowed)
    }

    @Test
    fun refusesOriginAndForeignHost() {
        assertFalse(request("POST /volume/up HTTP/1.1", "Host: 192.168.1.20:6470", "Origin: https://site.example")!!.allowed)
        assertFalse(request("POST /volume/up HTTP/1.1", "Host: 192.168.1.20:6470", "ORIGIN: null")!!.allowed)
        assertFalse(request("GET /apps HTTP/1.1", "Host: site.example:6470")!!.allowed)
        assertFalse(request("GET /apps HTTP/1.1")!!.allowed)
        assertFalse(request("GET /apps HTTP/1.1", "Host: 192.168.1.20", "Host: site.example")!!.allowed)
    }

    @Test
    fun hostMustBeAnIpLiteral() {
        for (host in listOf(
            "10.0.0.1", "10.0.0.1:6470", "255.255.255.255:65535",
            "[::1]", "[::]", "[fe80::1]:6470", "[fd12:3456::abcd]", "[fe80::1%25wlan0]:6470", "[fe80::1%wlan0]",
            "[1:2:3:4:5:6:7:8]", "[::ffff:192.168.1.20]:6470", "[1:2:3:4:5:6:1.2.3.4]",
        )) assertTrue(host, LanRequest.isIpHost(host))
        for (host in listOf(
            "", "tv", "tv.local", "tv.local:6470", "1.2.3", "1.2.3.4.5", "256.1.1.1", "1.2.3.4.site.example", "+1.2.3.4",
            "1.2.3.4:", "1.2.3.4:x", "1.2.3.4:65536", "::1", "[", "[]", "[::1", "[::1]x", "[::1]:", "[site.example]",
            "[1:2:3]", "[1:2:3:4:5:6:7:8:9]", "[:::]", "[1::2::3]", "[12345::]", "[g::]", "[fe80::1%]", "[1.2.3.4]",
        )) assertFalse(host, LanRequest.isIpHost(host))
    }

    @Test
    fun readsAtMostTheLimit() {
        var read = 0
        val endless = object : java.io.InputStream() {
            override fun read(): Int {
                read++
                return 'a'.code
            }
        }
        assertEquals(null, LanRequest.read(endless))
        assertEquals(LanRequest.MAX_HEAD_BYTES, read)
    }

    @Test
    fun incompleteOrMalformedRequestsAreNull() {
        assertEquals(null, LanRequest.read("GET /apps HTTP/1.1\r\nHost: 10.0.0.1\r\n".byteInputStream()))
        assertEquals(null, LanRequest.read("".byteInputStream()))
        assertEquals(null, LanRequest.read("\r\n".byteInputStream()))
        assertEquals(null, LanRequest.read("GET\r\n\r\n".byteInputStream()))
    }
}
