package cc.slokker.goatlauncher.data.api

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

const val BACKEND_URL = "https://thegoat.systems/"

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

    /**
     * `{account, account_rev, device, device_rev}`; com [etag] (o `ETag` da última resposta), 304 sem corpo quando
     * nada mudou.
     */
    @GET("api/launcher/settings")
    suspend fun settings(@Header("If-None-Match") etag: String?): Response<JsonObject>

    /**
     * Grava `account_merge` / `device_merge` (JSON Merge Patch) com `account_rev` / `device_rev`, ou `account` /
     * `device` inteiros (servidores antigos). 200 e 409 trazem o estado atual, com as revisões.
     */
    @PATCH("api/launcher/settings")
    suspend fun updateSettings(@Body body: JsonObject): Response<JsonObject>

    /** O índice das apps desta TV, para o site. Responde `{hash, apps, missing: [pacotes sem ícone]}`. */
    @PUT("api/launcher/apps")
    suspend fun putApps(@Body body: JsonObject): Response<JsonObject>

    /** O banner ou ícone (PNG, até 200 KB) de uma app do último índice; o SHA-1 tem de ser o `icon_hash` dela. */
    @PUT("api/launcher/apps/{package}/icon")
    suspend fun putAppIcon(@Path("package") pkg: String, @Body png: RequestBody): Response<JsonObject>
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

/** Esta TV, tal como aparece em thegoat.systems/conta → Dispositivos. */
data class BackendDevice(val id: String?, val name: String?, val app: String?)

data class BackendUser(val id: String?, val email: String?)

data class AuthRefreshResponse(val token: String?, val record: JsonObject?)

