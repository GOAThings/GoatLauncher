package cc.slokker.goatlauncher.data.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST

const val BACKEND_URL = "https://sys.slokker.cc/"

/** Nome desta app na conta Slokker (cada TV fica registada como um dispositivo do Launcher). */
const val BACKEND_APP = "launcher"

/**
 * API da conta Slokker para o GoatLauncher. O login do dispositivo só dá acesso às definições do Launcher
 * (as listas da GoatTV respondem 403). O token vai em `Authorization: <token>` (ver AppContainer).
 */
interface BackendApi {
    @POST("api/devices/start")
    suspend fun deviceStart(@Body body: DeviceStartRequest): DeviceStartResponse

    @POST("api/devices/poll")
    suspend fun devicePoll(@Body body: DevicePollRequest): Response<DevicePollResponse>

    /** Renova o token do dispositivo. 401/404: a TV foi desligada na conta. */
    @POST("api/collections/devices/auth-refresh")
    suspend fun authRefresh(): AuthRefreshResponse

    @GET("api/launcher/settings")
    suspend fun settings(): SettingsResponse

    /** Substitui `account` e/ou `device` inteiros (só os campos enviados). */
    @PATCH("api/launcher/settings")
    suspend fun updateSettings(@Body body: JsonObject): JsonObject
}

// Todos os campos são anuláveis: o Gson não respeita a nulidade do Kotlin.

data class DeviceStartRequest(val app: String, @SerializedName("device_name") val deviceName: String)

data class DeviceStartResponse(
    val code: String?,
    @SerializedName("device_secret") val deviceSecret: String?,
    @SerializedName("verification_url") val verificationUrl: String?,
    @SerializedName("verification_url_complete") val verificationUrlComplete: String?,
    @SerializedName("expires_in") val expiresIn: Int?,
    val interval: Int?,
)

data class DevicePollRequest(@SerializedName("device_secret") val deviceSecret: String)

data class DevicePollResponse(val status: String?, val token: String?, val device: BackendDevice?, val user: BackendUser?)

/** Esta TV, tal como aparece em sys.slokker.cc/conta → Dispositivos. */
data class BackendDevice(val id: String?, val name: String?, val app: String?)

data class BackendUser(val id: String?, val email: String?)

data class AuthRefreshResponse(val token: String?, val record: JsonObject?)

/** O JSON das definições é decidido pela app (ver LauncherSettings): aqui fica em bruto. */
data class SettingsResponse(val account: JsonElement?, val device: JsonElement?)
