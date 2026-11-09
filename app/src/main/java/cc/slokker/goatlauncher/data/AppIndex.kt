package cc.slokker.goatlauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import cc.slokker.goatlauncher.data.api.BackendApi
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
    private val prefs = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    /**
     * O hash do PNG de cada app (null se não couber no servidor). Só se volta a codificar quando a imagem da app muda:
     * antes, cada regresso ao ecrã inicial codificava os PNG de todas as apps só para ver que nada tinha mudado.
     */
    private val hashes = PerAppCache<ImageBitmap, String?> { image -> png(image)?.let(::sha1) }

    /**
     * Envia o índice de [apps] se mudou desde o último envio desta conta. [afterLink]: a TV acabou de ser ligada (a
     * uma conta nova, ou outra vez à mesma, que a recebe como outra TV): envia-se sempre.
     */
    suspend fun upload(apps: List<LauncherApp>, afterLink: Boolean = false) = mutex.withLock {
        val session = settings.session.value ?: return@withLock
        if (afterLink) prefs.edit().remove(Prefs.HASH).remove(Prefs.ACCOUNT).apply()
        try {
            withContext(Dispatchers.Default) { send(apps, indexKey(session)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Índice das apps: ${e.message}")
        }
    }

    private suspend fun send(apps: List<LauncherApp>, key: String) {
        hashes.retain(apps.map { it.packageName })
        val list = JsonArray()
        for (app in apps) {
            val image = app.banner ?: app.icon ?: continue
            val iconHash = hashes.get(app.packageName, image) ?: continue
            // O mesmo resumo que o GoatRemote recebe em /apps, mais o hash da imagem.
            list.add(app.summary().apply { addProperty("icon_hash", iconHash) })
        }
        val hash = sha1(list.toString().toByteArray())
        if (prefs.getString(Prefs.HASH, null) == hash && prefs.getString(Prefs.ACCOUNT, null) == key) return
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
        val byPkg = apps.associateBy { it.packageName }
        for (pkg in missing) {
            // Só as que o servidor ainda não tem (poucas): codificam-se outra vez aqui.
            val png = byPkg[pkg]?.let { it.banner ?: it.icon }?.let(::png) ?: continue
            val icon = withContext(Dispatchers.IO) { api.putAppIcon(pkg, png.toRequestBody(PNG)) }
            when {
                icon.isSuccessful -> Unit
                // Grande demais para o servidor: essa app fica sem imagem no site, e não se tenta outra vez.
                isIconTooBig(icon.code(), runCatching { icon.errorBody()?.string() }.getOrNull()) ->
                    Log.w(TAG, "Ícone de $pkg recusado (icon_too_big, ${png.size} bytes): fica de fora")
                else -> {
                    allSent = false
                    Log.w(TAG, "Ícone de $pkg: HTTP ${icon.code()}")
                }
            }
        }
        // Com um ícone em falta (sem ser recusado), tenta-se de novo na próxima vez (o servidor volta a dizer quais faltam).
        if (allSent) prefs.edit().putString(Prefs.HASH, hash).putString(Prefs.ACCOUNT, key).apply()
        Log.i(TAG, "Índice das apps enviado: ${list.size()} apps, ${missing.size} ícones")
    }

    /**
     * O banner (480×270) ou o ícone (192×192) em PNG, como no PhoneApps, a caber nos limites do servidor
     * (1024×1024 e 200 KB): maior, reduz-se; se nem assim couber, não vai.
     */
    private fun png(image: ImageBitmap): ByteArray? {
        val source = image.asAndroidBitmap()
        val (width, height) = fitIconSize(source.width, source.height)
        var bitmap = if (width == source.width && height == source.height) source else Bitmap.createScaledBitmap(source, width, height, true)
        repeat(3) {
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            if (bytes.size <= MAX_ICON_BYTES) return bytes
            bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width * 3 / 4, bitmap.height * 3 / 4, true)
        }
        return null
    }

    /** O que já foi enviado (nunca muda de nome: ver PersistedNamesTest). */
    internal object Prefs {
        const val FILE = "app_index"
        const val HASH = "hash"
        const val ACCOUNT = "account"
    }

    private companion object {
        const val TAG = "AppIndex"
        const val MAX_ICON_BYTES = 200 * 1024
        val PNG = "image/png".toMediaType()
    }
}

/**
 * A conta de um envio do índice: o email (o token muda a cada arranque, e com ele o índice ia todo outra vez). Sem
 * email, o token, como antes. Ligar a TV outra vez à mesma conta envia na mesma (ver [AppIndex.upload]).
 */
internal fun indexKey(session: SessionStore.Session): String = sha1((session.email ?: session.token).toByteArray())

private fun sha1(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

/** O maior lado que o servidor aceita num banner ou ícone. */
internal const val MAX_ICON_SIDE = 1024

/** O tamanho de uma imagem [width]×[height] reduzida (sem mudar a proporção) para caber em [max]×[max]. */
internal fun fitIconSize(width: Int, height: Int, max: Int = MAX_ICON_SIDE): Pair<Int, Int> {
    if (width <= max && height <= max) return width to height
    val scale = max.toDouble() / maxOf(width, height)
    return (width * scale).toInt().coerceIn(1, max) to (height * scale).toInt().coerceIn(1, max)
}

/** O servidor recusou a imagem por ser grande demais: 400 `icon_too_big` (o `code` pode vir dentro de `data`). */
internal fun isIconTooBig(status: Int, body: String?): Boolean {
    if (status != 400 || body.isNullOrBlank()) return false
    val json = runCatching { JsonParser.parseString(body) }.getOrNull() as? JsonObject ?: return false
    fun JsonObject.code() = get("code")?.takeIf { it.isJsonPrimitive }?.asString
    return (json.code() ?: (json.get("data") as? JsonObject)?.code()) == "icon_too_big"
}
