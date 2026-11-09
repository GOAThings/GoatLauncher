package cc.slokker.goatlauncher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * As apps instaladas, uma só lista para o processo todo: o ecrã inicial, a porta 6470 do GoatRemote ([PhoneApps]) e o
 * índice para o site ([AppIndex]) leem daqui. A lista só se volta a ler do sistema quando muda (instalar, desinstalar,
 * atualizar: o [start] fica à escuta) ou quando o ecrã inicial volta à frente ([refresh]); antes cada pedido do telemóvel
 * fazia uma consulta inteira ao PackageManager.
 *
 * Também guarda o PNG da imagem de cada app ([png]), feito uma vez por versão da app e partilhado pelo GoatRemote e pelo
 * site (antes cada um codificava o seu).
 */
class AppCatalog(private val context: Context, private val scope: CoroutineScope) {
    private val installed = InstalledApps(context)

    private val _apps = MutableStateFlow<List<LauncherApp>?>(null)

    /** Todas as apps de TV instaladas, pelo nome; null até à primeira leitura (só se lê quando alguém pede). */
    val apps: StateFlow<List<LauncherApp>?> = _apps.asStateFlow()

    private val _loads = MutableSharedFlow<List<LauncherApp>>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Cada leitura acabada, mesmo sem mudanças (o índice do site tenta outra vez o que não conseguiu enviar). */
    val loads: SharedFlow<List<LauncherApp>> = _loads.asSharedFlow()

    private val requests = Channel<Unit>(Channel.CONFLATED)

    /** Alguém já pediu a lista: daí em diante, as mudanças no sistema voltam a lê-la. */
    @Volatile
    private var wanted = false

    /** O PNG do banner (ou, sem banner, do ícone) de cada app instalada, um por versão da app. */
    internal val pngs = PerAppCache<ImageBitmap, ByteArray>(::encodePng)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (wanted) requests.trySend(Unit)
        }
    }

    /** Fica à escuta das mudanças nas apps (no Application: vale com ou sem ecrã). */
    fun start() {
        scope.launch {
            // Pedidos seguidos juntam-se: no fim lê-se uma vez com tudo o que mudou.
            for (request in requests) reload()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        // Exportado: são difusões protegidas do sistema (só o sistema as pode enviar).
        ContextCompat.registerReceiver(context, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    /** Volta a ler a lista (sem esperar). */
    fun refresh() {
        wanted = true
        requests.trySend(Unit)
    }

    /** A lista de agora; na primeira vez, espera pela leitura. */
    suspend fun current(): List<LauncherApp> {
        _apps.value?.let { return it }
        refresh()
        return _apps.filterNotNull().first()
    }

    private suspend fun reload() {
        val list = installed.load()
        // Os PNG das apps que saíram vão com elas.
        pngs.retain(list.map { it.packageName })
        _apps.value = list
        _loads.tryEmit(list)
    }

    fun launchIntent(pkg: String): Intent? = installed.launchIntent(pkg)

    /** O PNG da imagem de [app] (o banner ou o ícone), ou null se não tiver nenhuma. */
    fun png(app: LauncherApp): ByteArray? = (app.banner ?: app.icon)?.let { pngs.get(app.packageName, it) }

    private companion object {
        const val TAG = "AppCatalog"

        /** O PNG tal como o GoatRemote e o site o recebem (o `icon_hash` do site é o SHA-1 destes bytes). */
        fun encodePng(image: ImageBitmap): ByteArray {
            Log.d(TAG, "PNG codificado: ${image.width}×${image.height}")
            return ByteArrayOutputStream().also { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }
    }
}
