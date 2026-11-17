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
        // Os URLs saem inteiros (o servidor e o caminho também podem ser credenciais).
        assertTrue(out, out.contains("Opening http://***"))
        assertEquals("/live/***/***/1.ts", BugReport.redact("/live/joao/s3cr3t/1.ts", emptyList()))
        assertEquals("Failed URL=http://*** (403)", BugReport.redact("Failed URL=http://10457072.host.example:80/MAGABC/DEF/1474191 (403)", emptyList()))
        assertEquals("/stalker/***/***/play", BugReport.redact("/stalker/abc/gwsecret9/play", emptyList()))
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
    fun masksBareHostsAndKeepsCodeNames() {
        val out = BugReport.redact(
            """
            java.net.ConnectException: failed to connect to subscriber-id.host.example/192.0.2.1 (port 80)
            pretending to be “subscriber-id.host.example”
            Unable to resolve host "iptv.provider.com": No address
            at cc.slokker.goatlauncher.data.WatchNext.load(WatchNext.kt:24)
            at okhttp3.internal.connection.RealCall.execute(RealCall.kt:154)
            Open com.netflix.ninja, org.xbmc.kodi and com.google.android.youtube.tv
            GoatLauncher 0.9.3 is fine
            """.trimIndent(),
            emptyList(),
        )
        for (host in listOf("subscriber-id", "host.example", "192.0.2.1", "iptv.provider.com")) assertFalse("$host leaked:\n$out", out.contains(host))
        for (kept in listOf(
            "java.net.ConnectException", "cc.slokker.goatlauncher.data.WatchNext.load(WatchNext.kt:24)",
            "okhttp3.internal.connection.RealCall.execute(RealCall.kt:154)", "com.netflix.ninja", "org.xbmc.kodi",
            "com.google.android.youtube.tv", "GoatLauncher 0.9.3",
        )) assertTrue("$kept lost:\n$out", out.contains(kept))
    }

    @Test
    fun masksMacsEmailsAndIps() {
        for ((line, want) in listOf(
            "mac 00:1A:79:00:00:01 here" to "mac *** here",
            "mac=00%3A1A%3A79%3A00%3A00%3A01" to "mac=***",
            "account joao.silva+tv@gmail.com signed in" to "account *** signed in",
            "from <a_b@mail.example.pt>" to "from <***>",
            "peer 192.168.1.20:6470 ok" to "peer ***:6470 ok",
            "link-local fe80::1%wlan0 up" to "link-local *** up",
            "via [fe80::a00:27ff:fe4e:66a1%wlan0]:6470" to "via [***]:6470",
            "global 2001:db8:85a3:0:0:8a2e:370:7334 ok" to "global *** ok",
            "loopback ::1, mapped ::ffff:192.0.2.1 done" to "loopback ***, mapped *** done",
            "short 2001:db8::7334." to "short ***.",
        )) assertEquals(line, want, BugReport.redact(line, emptyList()))
    }

    @Test
    fun timestampsAndCodeColonsStay() {
        for (line in listOf(
            "11-17 01:12:03.456  1234  5678 I GoatLauncher: start",
            "at 01:12:03 and 23:59:59, took 00:00:01",
            "2026-11-17T01:12:03Z",
            "String::class and a :: b",
            "ratio 16:9, score 3:2:1",
        )) assertEquals(line, BugReport.redact(line, emptyList()))
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
