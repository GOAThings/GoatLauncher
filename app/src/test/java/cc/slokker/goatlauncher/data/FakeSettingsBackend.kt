package cc.slokker.goatlauncher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import cc.slokker.goatlauncher.data.api.AuthRefreshResponse
import cc.slokker.goatlauncher.data.api.BackendApi
import cc.slokker.goatlauncher.data.api.DevicePollRequest
import cc.slokker.goatlauncher.data.api.DevicePollResponse
import cc.slokker.goatlauncher.data.api.DeviceStartRequest
import cc.slokker.goatlauncher.data.api.DeviceStartResponse
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/** Um DataStore em memória (o SessionStore e o DataStoreSettingsDisk sem ficheiros). */
internal class MemoryDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: StateFlow<Preferences> = state
    val value: Preferences get() = state.value

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

/** O SettingsDisk em memória, com o que foi gravado por último. */
internal class MemoryDisk(var snapshot: SettingsSnapshot = SettingsSnapshot()) : SettingsDisk {
    var saves = 0
    override suspend fun load() = snapshot
    override suspend fun save(snapshot: SettingsSnapshot) {
        saves++
        this.snapshot = snapshot
    }
}

/**
 * O backend das definições com respostas preparadas por cada teste: [gets] para `GET api/launcher/settings`, [patches]
 * para `PATCH` (cada uma recebe o corpo enviado), [refreshes] para o auth-refresh. Guarda o que lhe pediram.
 */
internal class FakeSettingsBackend : BackendApi {
    val gets = ArrayDeque<suspend (etag: String?) -> Response<JsonObject>>()
    val patches = ArrayDeque<suspend (body: JsonObject) -> Response<JsonObject>>()
    val refreshes = ArrayDeque<suspend () -> AuthRefreshResponse>()
    /** Respostas a `POST api/reports` (ver ReportSenderTest). */
    val reports = ArrayDeque<suspend (body: JsonObject) -> Response<JsonObject>>()

    val sentPatches = mutableListOf<JsonObject>()
    val sentEtags = mutableListOf<String?>()
    val sentReports = mutableListOf<JsonObject>()
    var refreshCalls = 0

    override suspend fun settings(etag: String?): Response<JsonObject> {
        sentEtags += etag
        return gets.removeFirst()(etag)
    }

    override suspend fun updateSettings(body: JsonObject): Response<JsonObject> {
        sentPatches += body.deepCopy()
        return patches.removeFirst()(body)
    }

    override suspend fun authRefresh(): AuthRefreshResponse {
        refreshCalls++
        return refreshes.removeFirst()()
    }

    override suspend fun deviceStart(body: DeviceStartRequest): DeviceStartResponse = error("não usado")
    override suspend fun devicePoll(body: DevicePollRequest): Response<DevicePollResponse> = error("não usado")
    override suspend fun sendReport(body: JsonObject): Response<JsonObject> {
        sentReports += body.deepCopy()
        return reports.removeFirst()(body)
    }

    override suspend fun deviceLogout(token: String): Response<Unit> = error("não usado")
    override suspend fun putApps(body: JsonObject): Response<JsonObject> = error("não usado")
    override suspend fun putAppIcon(pkg: String, png: RequestBody): Response<JsonObject> = error("não usado")

    companion object {
        fun ok(json: String, etag: String? = null): Response<JsonObject> {
            val body = JsonParser.parseString(json).asJsonObject
            return if (etag != null) Response.success(body, headersOf("ETag", etag)) else Response.success(body)
        }

        fun status(code: Int, json: String = "{}"): Response<JsonObject> =
            Response.error(code, json.toResponseBody("application/json".toMediaType()))

        /** O 304 (o Retrofit dá-o como um erro sem corpo: não é 2xx). */
        fun notModified(): Response<JsonObject> = Response.error(
            "".toResponseBody(),
            okhttp3.Response.Builder()
                .code(304).message("Not Modified").protocol(Protocol.HTTP_1_1)
                .request(Request.Builder().url("https://thegoat.systems/api/launcher/settings").build())
                .build(),
        )

        /** Como o Retrofit responde a um método sem Response<> quando o servidor diz [code]. */
        fun httpError(code: Int): Nothing = throw HttpException(status(code))
    }
}
