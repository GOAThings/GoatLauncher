package cc.slokker.goatlauncher.data

import cc.slokker.goatlauncher.data.FakeSettingsBackend.Companion.httpError
import cc.slokker.goatlauncher.data.FakeSettingsBackend.Companion.notModified
import cc.slokker.goatlauncher.data.FakeSettingsBackend.Companion.ok
import cc.slokker.goatlauncher.data.FakeSettingsBackend.Companion.status
import cc.slokker.goatlauncher.data.api.AuthRefreshResponse
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * O SettingsRepository com o disco e o backend falsos: o corpo do PATCH com e sem revisões, o 409 e a nova tentativa, o
 * 304, ligar a uma conta vazia e com o controlo parental desta TV, o 401 que desliga, desligar com um sync a meio, e a
 * espera pela leitura do disco. É o código mais arriscado do launcher (tudo o que a conta guarda passa por aqui).
 */
class SettingsRepositoryTest {
    private val job = SupervisorJob()

    /** Como o scope da app, mas sem thread principal: o que for lançado corre já, até esperar por alguma coisa. */
    private val scope = CoroutineScope(job + Dispatchers.Unconfined)
    private val api = FakeSettingsBackend()
    private val disk = MemoryDisk()
    private val sessionDisk = MemoryDataStore()
    private val sessions = SessionStore(sessionDisk)
    private var now = 1_000_000L
    private val repo = SettingsRepository(disk, api, sessions, scope, clock = { now })

    /** Espera que acabe o que o repositório lançou (gravar e enviar). */
    private suspend fun settle() {
        while (true) {
            val running = job.children.filter { it.isActive }.toList()
            if (running.isEmpty()) return
            running.forEach { it.join() }
        }
    }

    private fun json(s: String): JsonObject = JsonParser.parseString(s).asJsonObject

    private val defaultAccount = AccountSettings().toJson()
    private val defaultDevice = DeviceSettings().toJson()

    /** Uma TV ligada, com a última leitura da conta (revisões [accountRev]/[deviceRev], ETag "e1") igual ao que tem. */
    private suspend fun linkedWithRevs(accountRev: Long? = 5, deviceRev: Long? = 7) {
        disk.snapshot = SettingsSnapshot(
            account = defaultAccount.toString(),
            device = defaultDevice.toString(),
            accountBase = defaultAccount.toString().takeIf { accountRev != null },
            deviceBase = defaultDevice.toString().takeIf { deviceRev != null },
            accountRev = accountRev,
            deviceRev = deviceRev,
            etag = "e1",
        )
        sessions.save(SessionStore.Session("t1", "pessoa@example.com", "Sala"))
        repo.init()
    }

    /** O estado da conta como o servidor o devolve. */
    private fun state(account: JsonObject, accountRev: Long, device: JsonObject, deviceRev: Long) = JsonObject().apply {
        add("account", account)
        addProperty("account_rev", accountRev)
        add("device", device)
        addProperty("device_rev", deviceRev)
    }

    @Test
    fun patchWithRevisionsSendsOnlyTheChange() = runBlocking {
        linkedWithRevs()
        val changed = defaultAccount.deepCopy().apply { addProperty("clock_24h", false) }
        api.patches += { ok(state(changed, 6, defaultDevice, 7).toString()) }
        repo.updateAccount { it.copy(clock24h = false) }
        settle()
        assertEquals(1, api.sentPatches.size)
        assertEquals(json("""{"account_merge":{"clock_24h":false},"account_rev":5}"""), api.sentPatches[0])
        // A resposta passa a ser a base, com a revisão nova; nada fica pendente.
        assertFalse(disk.snapshot.accountDirty)
        assertEquals(6L, disk.snapshot.accountRev)
        assertEquals(changed, json(disk.snapshot.accountBase!!))
        assertFalse(repo.account.value.clock24h)
    }

    @Test
    fun patchWithoutRevisionsSendsTheWholeObject() = runBlocking {
        // Um servidor antigo (sem revisões): vai o objeto inteiro, como antes.
        linkedWithRevs(accountRev = null, deviceRev = null)
        api.patches += { ok("{}") }
        repo.updateDevice { it.copy(hidden = setOf("com.example.hidden")) }
        settle()
        val body = api.sentPatches.single()
        assertEquals(setOf("device"), body.keySet())
        assertEquals(repo.device.value.toJson(), body.getAsJsonObject("device"))
        assertFalse(disk.snapshot.deviceDirty)
    }

    @Test
    fun conflictRebasesOnTheAccountAndTriesAgain() = runBlocking {
        linkedWithRevs()
        // Outra TV mudou o tempo entretanto (revisão 6): o 409 traz o estado de agora.
        val other = defaultAccount.deepCopy().apply { addProperty("show_weather", false) }
        val both = other.deepCopy().apply { addProperty("clock_24h", false) }
        api.patches += { status(409, state(other, 6, defaultDevice, 7).toString()) }
        api.patches += { ok(state(both, 7, defaultDevice, 7).toString()) }
        repo.updateAccount { it.copy(clock24h = false) }
        settle()
        assertEquals(2, api.sentPatches.size)
        // A segunda tentativa vai com a revisão nova, só com a mudança desta TV.
        assertEquals(json("""{"account_merge":{"clock_24h":false},"account_rev":6}"""), api.sentPatches[1])
        // As duas mudanças ficam.
        assertFalse(repo.account.value.clock24h)
        assertFalse(repo.account.value.showWeather)
        assertEquals(7L, disk.snapshot.accountRev)
        assertFalse(disk.snapshot.accountDirty)
    }

    @Test
    fun notModifiedKeepsEverything() = runBlocking {
        linkedWithRevs()
        val saves = disk.saves
        api.gets += { notModified() }
        repo.sync()
        // Pede com o ETag da última leitura; o 304 não muda nada nem grava.
        assertEquals(listOf<String?>("e1"), api.sentEtags)
        assertTrue(api.sentPatches.isEmpty())
        assertEquals(saves, disk.saves)
        assertEquals("e1", disk.snapshot.etag)
        assertEquals(defaultAccount, repo.account.value.toJson())
    }

    @Test
    fun syncIfStaleOnlyAfterFiveMinutes() = runBlocking {
        linkedWithRevs()
        api.gets += { notModified() }
        repo.sync()
        now += 4 * 60_000L
        repo.syncIfStale()
        assertEquals(1, api.sentEtags.size)
        now += 2 * 60_000L
        api.gets += { notModified() }
        repo.syncIfStale()
        assertEquals(2, api.sentEtags.size)
    }

    @Test
    fun oneGetPerProcessStartEvenWithAnEarlyResumeOrASecondScreen() = runBlocking {
        linkedWithRevs()
        // O GET do arranque fica à espera da rede: o ecrã volta à frente (onResume) e abre-se um ecrã novo entretanto.
        val reply = CompletableDeferred<Response<JsonObject>>()
        api.refreshes += { AuthRefreshResponse("t2", null) }
        api.gets += { reply.await() }
        val startup = repo.startSync()
        val resume = scope.launch { repo.syncIfStale() }
        assertTrue(startup === repo.startSync())
        reply.complete(notModified())
        startup.join()
        resume.join()
        // Um só GET (e uma só renovação do token); o onResume encontrou-o fresco.
        assertEquals(1, api.sentEtags.size)
        assertEquals(1, api.refreshCalls)
        // Mais tarde, passado o intervalo, volta a sincronizar.
        now += 6 * 60_000L
        api.gets += { notModified() }
        repo.syncIfStale()
        assertEquals(2, api.sentEtags.size)
    }

    @Test
    fun aFailedStartupSyncIsRetriedOnResume() = runBlocking {
        linkedWithRevs()
        api.refreshes += { AuthRefreshResponse("t2", null) }
        api.gets += { throw java.io.IOException("sem rede") }
        repo.startSync().join()
        api.gets += { notModified() }
        repo.syncIfStale()
        assertEquals(2, api.sentEtags.size)
    }

    @Test
    fun startupRenewsTheTokenOnce() = runBlocking {
        linkedWithRevs()
        api.refreshes += { AuthRefreshResponse("t2", null) }
        api.gets += { notModified() }
        repo.sync(refreshToken = true)
        // O SessionStore é o único dono do token: o interceptor e o disco veem o novo.
        assertEquals("t2", sessions.token)
        assertEquals("t2", repo.session.value?.token)
        assertEquals("t2", sessionDisk.value[SessionStore.Keys.TOKEN])
    }

    @Test
    fun linkToAnEmptyAccountSendsThisTv() = runBlocking {
        disk.snapshot = SettingsSnapshot(
            account = defaultAccount.deepCopy().apply { addProperty("wallpaper_minutes", 60) }.toString(),
            device = DeviceSettings(pin = "sal:hash", locked = setOf("com.netflix.ninja")).toJson().toString(),
        )
        repo.init()
        // A conta ainda não tem definições: recebe as desta TV, inteiras (ainda não há revisões).
        api.gets += { ok("""{"account":{},"device":{}}""", etag = "e2") }
        api.patches += { body -> ok(state(body.getAsJsonObject("account"), 1, body.getAsJsonObject("device"), 1).toString()) }
        repo.link("t1", "pessoa@example.com", "Sala")
        settle()
        assertEquals("t1", sessionDisk.value[SessionStore.Keys.TOKEN])
        assertEquals(listOf<String?>(null), api.sentEtags)
        val body = api.sentPatches.single()
        assertEquals(setOf("account", "device"), body.keySet())
        assertEquals(60, body.getAsJsonObject("account").get("wallpaper_minutes").asInt)
        assertEquals("sal:hash", body.getAsJsonObject("device").get("pin").asString)
        assertFalse(disk.snapshot.keepParental)
        assertFalse(disk.snapshot.accountDirty || disk.snapshot.deviceDirty)
        assertEquals(1L, disk.snapshot.deviceRev)
    }

    @Test
    fun linkWithACodeKeepsThisTvsParentalControl() = runBlocking {
        val local = DeviceSettings(
            apps = listOf("local.first"), pin = "sal:hash", locked = setOf("com.netflix.ninja"), lockSettings = true,
            lockFrom = 20, lockTo = 8,
        )
        disk.snapshot = SettingsSnapshot(account = defaultAccount.toString(), device = local.toJson().toString())
        repo.init()
        // A conta já tem esta TV, com outra ordem e sem código: a conta ganha, menos no controlo parental.
        val remoteDevice = DeviceSettings(apps = listOf("remote.first", "remote.second"), autostart = "remote.first").toJson()
        api.gets += { ok(state(defaultAccount, 3, remoteDevice, 4).toString(), etag = "e3") }
        // O servidor aplica o patch e devolve o estado novo.
        api.patches += { ok(state(defaultAccount, 3, repo.device.value.toJson(), 5).toString()) }
        repo.link("t1", null, "Sala")
        settle()
        val device = repo.device.value
        assertEquals(listOf("remote.first", "remote.second"), device.apps)
        assertEquals("remote.first", device.autostart)
        assertEquals("sal:hash", device.pin)
        assertEquals(setOf("com.netflix.ninja"), device.locked)
        // O controlo parental desta TV segue para a conta, só ele, e uma só vez.
        val patch = api.sentPatches.single()
        assertEquals(4L, patch.get("device_rev").asLong)
        assertEquals(DeviceSettings.PARENTAL_KEYS, patch.getAsJsonObject("device_merge").keySet())
        assertFalse(disk.snapshot.keepParental)
    }

    @Test
    fun parentalKeysAreExactlyWhatLinkKeeps() {
        // withParentalOf e PARENTAL_KEYS dizem o mesmo: só estas chaves vêm da TV, o resto (e o desconhecido) da conta.
        val local = DeviceSettings(pin = "sal:hash", locked = setOf("a"), lockSettings = true, lockFrom = 20, lockTo = 8)
        val remote = DeviceSettings.fromJson(json("""{"apps":["x"],"autostart":"x","site_only":1,"pin":"outro:hash","locked":["b"]}"""))
        val kept = remote.withParentalOf(local).toJson()
        val remoteJson = remote.toJson()
        val localJson = local.toJson()
        for (key in kept.keySet() + remoteJson.keySet()) {
            val expected = if (key in DeviceSettings.PARENTAL_KEYS) localJson.get(key) else remoteJson.get(key)
            assertEquals(key, expected, kept.get(key))
        }
    }

    @Test
    fun unauthorizedUnlinks() = runBlocking {
        linkedWithRevs()
        // O PATCH diz 401: a TV foi apagada na conta.
        api.patches += { status(401) }
        repo.updateAccount { it.copy(clock24h = false) }
        settle()
        assertNull(repo.session.value)
        assertNull(sessions.token)
        assertNull(sessionDisk.value[SessionStore.Keys.TOKEN])
        // As definições ficam (só deixam de sincronizar); a base, as revisões e o ETag da conta vão.
        assertFalse(repo.account.value.clock24h)
        assertEquals(SettingsSnapshot(account = disk.snapshot.account, device = disk.snapshot.device), disk.snapshot)
        assertFalse(json(disk.snapshot.account!!).get("clock_24h").asBoolean)
    }

    @Test
    fun notFoundOnRefreshUnlinks() = runBlocking {
        linkedWithRevs()
        api.refreshes += { httpError(404) }
        repo.sync(refreshToken = true)
        assertNull(repo.session.value)
        assertTrue(api.sentEtags.isEmpty())
    }

    @Test
    fun unlinkDuringASyncDropsWhatItBrings() = runBlocking {
        linkedWithRevs()
        // O auth-refresh do arranque fica à espera da rede; entretanto a pessoa desliga a conta.
        val refresh = CompletableDeferred<AuthRefreshResponse>()
        api.refreshes += { refresh.await() }
        val sync = scope.launch { repo.sync(refreshToken = true) }
        val unlink = scope.async { repo.unlink() }
        assertNull(repo.session.value)
        assertFalse(unlink.isCompleted) // espera que o sync acabe
        refresh.complete(AuthRefreshResponse("renovado", null))
        sync.join()
        unlink.await()
        // O token renovado nunca chega ao disco, e o GET da conta antiga nem sai.
        assertNull(sessionDisk.value[SessionStore.Keys.TOKEN])
        assertNull(sessions.token)
        assertTrue(api.sentEtags.isEmpty())
        assertEquals(SettingsSnapshot(account = disk.snapshot.account, device = disk.snapshot.device), disk.snapshot)
    }

    @Test
    fun unlinkDuringAGetKeepsTheOldAccountOut() = runBlocking {
        linkedWithRevs()
        val get = CompletableDeferred<Response<JsonObject>>()
        api.gets += { get.await() }
        val sync = scope.launch { repo.sync() }
        val unlink = scope.async { repo.unlink() }
        val other = defaultAccount.deepCopy().apply { addProperty("clock_24h", false) }
        get.complete(ok(state(other, 9, defaultDevice, 9).toString(), etag = "e9"))
        sync.join()
        unlink.await()
        // A resposta da conta que já não é desta TV não muda nada.
        assertTrue(repo.account.value.clock24h)
        assertNull(disk.snapshot.etag)
        assertNull(disk.snapshot.accountRev)
        assertNull(disk.snapshot.accountBase)
    }

    @Test
    fun aRefusalOfTheOldTokenDoesNotUnlinkTheNewAccount() = runBlocking {
        linkedWithRevs()
        val get = CompletableDeferred<Response<JsonObject>>()
        api.gets += { get.await() }
        val sync = scope.launch { repo.sync() }
        val unlink = scope.async { repo.unlink() }
        // Ligada logo a outra conta, enquanto o GET antigo esperava; esse acaba com 401.
        api.gets += { ok("""{"account":{},"device":{}}""") }
        api.patches += { ok("{}") }
        val link = scope.async { repo.link("novo", "outra@example.com", "Sala") }
        get.complete(status(401))
        sync.join()
        unlink.await()
        link.await()
        settle()
        assertEquals("novo", repo.session.value?.token)
        assertEquals("novo", sessionDisk.value[SessionStore.Keys.TOKEN])
    }

    @Test
    fun awaitReadyWaitsForTheDiskCopy() = runBlocking {
        disk.snapshot = SettingsSnapshot(device = DeviceSettings(pin = "sal:hash", locked = setOf("com.netflix.ninja")).toJson().toString())
        val gate = ParentalGate(repo)
        // Antes de ler o disco não se sabe se há código: a pergunta espera (nunca responde "não").
        val locked = scope.async { gate.appLocked("com.netflix.ninja") }
        val other = scope.async { gate.appLocked("org.xbmc.kodi") }
        assertFalse(locked.isCompleted)
        repo.init()
        assertTrue(locked.await())
        assertFalse(other.await())
        assertTrue(gate.parentalLocked())
        assertFalse(gate.settingsLocked())
    }

    @Test
    fun aChangeBeforeTheDiskIsReadIsAppliedOnTopOfIt() = runBlocking {
        disk.snapshot = SettingsSnapshot(
            account = defaultAccount.deepCopy().apply { addProperty("wallpaper_minutes", 60) }.toString(),
            device = defaultDevice.toString(),
        )
        repo.updateAccount { it.copy(clock24h = false) }
        // Nada se grava com os valores por defeito por cima da cópia do disco.
        assertEquals(0, disk.saves)
        assertTrue(repo.account.value.clock24h)
        repo.init()
        settle()
        assertFalse(repo.account.value.clock24h)
        assertEquals(60, repo.account.value.wallpaperMinutes)
        val saved = json(disk.snapshot.account!!)
        assertEquals(60, saved.get("wallpaper_minutes").asInt)
        assertFalse(saved.get("clock_24h").asBoolean)
    }

    @Test
    fun aDamagedBlobIsNotOverwrittenWithDefaults() = runBlocking {
        disk.snapshot = SettingsSnapshot(account = "{\"clock_24h\": fal", device = defaultDevice.toString())
        repo.init()
        assertEquals(AccountSettings(), repo.account.value)
        // Mudar outra coisa grava, mas o blob estragado fica como estava (pode ainda dar para o recuperar à mão).
        repo.updateDevice { it.copy(hidden = setOf("a")) }
        settle()
        assertEquals("{\"clock_24h\": fal", disk.snapshot.account)
        assertEquals(setOf("a"), DeviceSettings.fromJson(json(disk.snapshot.device!!)).hidden)
        // Uma alteração a sério na conta substitui-o.
        repo.updateAccount { it.copy(clock24h = false) }
        settle()
        assertFalse(json(disk.snapshot.account!!).get("clock_24h").asBoolean)
    }
}
