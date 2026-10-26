package cc.slokker.goatlauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import cc.slokker.goatlauncher.data.api.BackendApi
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * As apps desta TV para o site da conta (organizar a fila, escolher a app ao ligar, bloquear apps…): o nome, a cor
 * e o banner ou ícone de cada uma (`PUT /api/launcher/apps`). Só se envia quando a lista muda; as imagens vão à
 * parte, só as que o servidor ainda não tem (a resposta diz quais faltam).
 */
class AppIndex(context: Context, private val api: BackendApi, private val settings: SettingsRepository) {
    private val prefs = context.getSharedPreferences("app_index", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    /** Envia o índice de [apps] se mudou desde o último envio desta conta. */
    suspend fun upload(apps: List<LauncherApp>) = mutex.withLock {
        val session = settings.session.value ?: return@withLock
        try {
            withContext(Dispatchers.Default) { send(apps, session.token) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Índice das apps: ${e.message}")
        }
    }

    private suspend fun send(apps: List<LauncherApp>, account: String) {
        val images = apps.associate { it.packageName to it.png() }
        val list = JsonArray()
        for (app in apps) {
            val png = images[app.packageName] ?: continue
            list.add(JsonObject().apply {
                addProperty("package", app.packageName)
                addProperty("label", app.label)
                addProperty("banner", app.banner != null)
                addProperty("color", String.format("#%06X", app.color.toArgb() and 0xFFFFFF))
                addProperty("icon_hash", sha1(png))
            })
        }
        val hash = sha1(list.toString().toByteArray())
        // O hash vale para esta conta: noutra (ou ao voltar a ligar a TV), envia-se outra vez.
        val key = sha1(account.toByteArray())
        if (prefs.getString("hash", null) == hash && prefs.getString("account", null) == key) return
        val res = withContext(Dispatchers.IO) {
            api.putApps(JsonObject().apply {
                addProperty("hash", hash)
                add("apps", list)
            })
        }
        val missing = res.body()?.getAsJsonArray("missing")?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
        if (!res.isSuccessful || missing == null) {
            Log.w(TAG, "Índice das apps: HTTP ${res.code()}")
            return
        }
        var allSent = true
        for (pkg in missing) {
            val png = images[pkg] ?: continue
            val icon = withContext(Dispatchers.IO) { api.putAppIcon(pkg, png.toRequestBody(PNG)) }
            if (!icon.isSuccessful) {
                allSent = false
                Log.w(TAG, "Ícone de $pkg: HTTP ${icon.code()}")
            }
        }
        // Com um ícone em falta, tenta-se de novo na próxima vez (o servidor volta a dizer quais faltam).
        if (allSent) prefs.edit().putString("hash", hash).putString("account", key).apply()
        Log.i(TAG, "Índice das apps enviado: ${list.size()} apps, ${missing.size} ícones")
    }

    /** O banner (480×270) ou o ícone (192×192) em PNG, como no PhoneApps, a caber nos 200 KB do servidor. */
    private fun LauncherApp.png(): ByteArray? {
        var bitmap = (banner ?: icon)?.asAndroidBitmap() ?: return null
        repeat(3) {
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            if (bytes.size <= MAX_ICON_BYTES) return bytes
            bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width * 3 / 4, bitmap.height * 3 / 4, true)
        }
        return null
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "AppIndex"
        const val MAX_ICON_BYTES = 200 * 1024
        val PNG = "image/png".toMediaType()
    }
}
