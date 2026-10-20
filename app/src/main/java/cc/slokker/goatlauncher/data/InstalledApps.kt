package cc.slokker.goatlauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Uma app de TV instalada. Sem [banner], o cartão mostra o [icon] sobre a [color] da app. */
@Immutable
class LauncherApp(
    val packageName: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val color: Color,
)

/** As apps com entrada no launcher da TV (categoria LEANBACK_LAUNCHER), menos este launcher. */
class InstalledApps(private val context: Context) {
    private val pm = context.packageManager

    /** Os banners só se voltam a desenhar quando a app é atualizada (o caminho do APK muda a cada instalação). */
    private val cache = HashMap<String, Pair<String, LauncherApp>>()

    /** Ordenadas pelo nome. */
    suspend fun load(): List<LauncherApp> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val infos = runCatching { pm.queryIntentActivities(intent, 0) }.getOrElse { emptyList() }
        synchronized(cache) {
            val apps = infos
                .distinctBy { it.activityInfo.packageName }
                .filter { it.activityInfo.packageName != context.packageName }
                .mapNotNull { info ->
                    val pkg = info.activityInfo.packageName
                    val stamp = info.activityInfo.applicationInfo.sourceDir.orEmpty()
                    cache[pkg]?.takeIf { it.first == stamp }?.second
                        ?: runCatching { build(info) }
                            .onFailure { Log.w(TAG, "Banner de $pkg", it) }
                            .getOrNull()
                            ?.also { cache[pkg] = stamp to it }
                }
            cache.keys.retainAll(apps.map { it.packageName }.toSet())
            apps.sortedBy { it.label.lowercase() }
        }
    }

    private fun build(info: ResolveInfo): LauncherApp {
        val activity = info.activityInfo
        val banner = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)
        val icon = if (banner == null) activity.loadIcon(pm) else null
        return LauncherApp(
            packageName = activity.packageName,
            label = info.loadLabel(pm).toString(),
            banner = banner?.toImage(BANNER_W, BANNER_H),
            icon = icon?.toImage(ICON_SIZE, ICON_SIZE),
            color = icon?.let(::dominantColor) ?: Color(0xFF1C2333),
        )
    }

    fun launchIntent(pkg: String): Intent? =
        (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun Drawable.toImage(w: Int, h: Int): ImageBitmap = toBitmap(w, h).asImageBitmap()

    /** Cor média dos píxeis opacos do ícone, escurecida, para o fundo do cartão. */
    private fun dominantColor(icon: Drawable): Color {
        val bmp = icon.toBitmap(24, 24)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            val p = bmp.getPixel(x, y)
            if ((p ushr 24) < 200) continue
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
            n++
        }
        if (n == 0) return Color(0xFF1C2333)
        val k = 0.55f / 255f
        return Color(r / n * k, g / n * k, b / n * k)
    }

    companion object {
        private const val TAG = "InstalledApps"

        // Banners de TV: 320×180 em xhdpi. Desenha-se a 1,5× para o cartão com foco (maior) continuar nítido.
        private const val BANNER_W = 480
        private const val BANNER_H = 270
        private const val ICON_SIZE = 192
    }
}
