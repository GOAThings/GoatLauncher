package cc.slokker.goatlauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** Imagens da box: as pastas e discos que se podem percorrer, e a cópia de uma imagem escolhida para a app. */
object ImageImport {
    private val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    /** Onde começar: o armazenamento interno e os discos montados em /storage (USB, rede). */
    fun roots(): List<File> {
        val internal = Environment.getExternalStorageDirectory()
        val others = File("/storage").listFiles()
            ?.filter { it.isDirectory && it.name != "self" && it.name != "emulated" && it.canRead() }
            .orEmpty()
            .sortedBy { it.name.lowercase() }
        return listOf(internal) + others
    }

    /** Subpastas e imagens de [dir] (sem ficheiros escondidos), pastas primeiro. */
    suspend fun list(dir: File): List<File> = withContext(Dispatchers.IO) {
        val files = dir.listFiles().orEmpty().filterNot { it.name.startsWith(".") }
        files.filter { it.isDirectory }.sortedBy { it.name.lowercase() } +
            files.filter { it.isFile && it.extension.lowercase() in EXTENSIONS }.sortedBy { it.name.lowercase() }
    }

    fun isRoot(dir: File) = roots().any { it.path == dir.path }

    /** As imagens de [dir] (sem subpastas nem ficheiros escondidos). */
    suspend fun images(dir: File): List<File> = withContext(Dispatchers.IO) {
        dir.listFiles().orEmpty().filter { it.isFile && !it.name.startsWith(".") && it.extension.lowercase() in EXTENSIONS }
    }

    /** Lê uma imagem (ficheiro ou content://) já reduzida para caber em [maxW]×[maxH]. */
    suspend fun decode(context: Context, source: Any, maxW: Int, maxH: Int): Bitmap? = withContext(Dispatchers.IO) {
        fun open(): InputStream? = when (source) {
            is File -> source.inputStream()
            is Uri -> context.contentResolver.openInputStream(source)
            else -> null
        }
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxW && bounds.outHeight / (sample * 2) >= maxH) sample *= 2
            open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?.let { fit(it, maxW, maxH) }
        }.onFailure { Log.w("ImageImport", "Ler $source", it) }.getOrNull()
    }

    /** Reduz [bitmap] (sem aumentar) para caber em [maxW]×[maxH]. */
    fun fit(bitmap: Bitmap, maxW: Int, maxH: Int): Bitmap {
        val scale = minOf(maxW.toFloat() / bitmap.width, maxH.toFloat() / bitmap.height, 1f)
        return if (scale >= 1f) bitmap else bitmap.scale((bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1))
    }

    /** Copia uma imagem para fundo (JPEG até 1920×1080). Devolve o nome do ficheiro em `files/wallpapers`. */
    suspend fun saveWallpaper(context: Context, source: Any): String? {
        val bitmap = decode(context, source, 1920, 1080) ?: return null
        return withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
            val name = "wall-${System.currentTimeMillis()}.jpg"
            File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            // Só fica o fundo atual.
            dir.listFiles()?.filter { it.name != name }?.forEach { it.delete() }
            name
        }
    }

    fun wallpaperFile(context: Context, name: String) = File(File(context.filesDir, "wallpapers"), name)
}
