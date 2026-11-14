package cc.slokker.goatlauncher.ui.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * O ecrã tal como está agora (fundo, banners, tudo), em miniatura e desfocado: o que fica por trás do vidro do menu
 * da app, como o vídeo por trás do vidro da GoatTV. null antes do Android 8 (sem PixelCopy da janela) ou se falhar.
 */
suspend fun captureScreenBlur(view: View): ImageBitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    val window = view.context.activity()?.window ?: return null
    val small = Bitmap.createBitmap(BLUR_W, BLUR_H, Bitmap.Config.ARGB_8888)
    val ok = suspendCancellableCoroutine { cont ->
        runCatching {
            // O PixelCopy reduz a janela inteira para o tamanho da miniatura.
            PixelCopy.request(window, small, { cont.resume(it == PixelCopy.SUCCESS) }, Handler(Looper.getMainLooper()))
        }.onFailure { cont.resume(false) }
    }
    if (!ok) return null
    return withContext(Dispatchers.Default) {
        val work = IntArray(BLUR_W * BLUR_H)
        val tmp = IntArray(BLUR_W * BLUR_H)
        small.getPixels(work, 0, BLUR_W, 0, 0, BLUR_W, BLUR_H)
        // 3 passagens de box blur ≈ gaussiano (a desfocagem da GoatTV).
        repeat(3) {
            boxPass(work, tmp, horizontal = true)
            boxPass(tmp, work, horizontal = false)
        }
        small.setPixels(work, 0, BLUR_W, 0, 0, BLUR_W, BLUR_H)
        small.asImageBitmap()
    }
}

private fun boxPass(input: IntArray, output: IntArray, horizontal: Boolean) {
    val w = BLUR_W
    val lines = if (horizontal) BLUR_H else w
    val len = if (horizontal) w else BLUR_H
    val window = 2 * RADIUS + 1
    for (line in 0 until lines) {
        fun idx(i: Int): Int {
            val c = i.coerceIn(0, len - 1)
            return if (horizontal) line * w + c else c * w + line
        }
        var sr = 0
        var sg = 0
        var sb = 0
        for (i in -RADIUS..RADIUS) {
            val p = input[idx(i)]
            sr += (p shr 16) and 0xFF
            sg += (p shr 8) and 0xFF
            sb += p and 0xFF
        }
        for (i in 0 until len) {
            output[idx(i)] = (0xFF shl 24) or ((sr / window) shl 16) or ((sg / window) shl 8) or (sb / window)
            val add = input[idx(i + RADIUS + 1)]
            val sub = input[idx(i - RADIUS)]
            sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
            sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
            sb += (add and 0xFF) - (sub and 0xFF)
        }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** O tamanho da miniatura e o raio da desfocagem: os do vidro da GoatTV sobre o vídeo. */
private const val BLUR_W = 128
private const val BLUR_H = 72
private const val RADIUS = 3
