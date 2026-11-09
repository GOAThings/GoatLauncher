package cc.slokker.goatlauncher.ui.state

import android.content.Context
import android.graphics.BitmapFactory
import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.ImageImport
import cc.slokker.goatlauncher.data.OnlinePhoto
import cc.slokker.goatlauncher.data.OnlineWallpapers
import cc.slokker.goatlauncher.data.Wallpaper
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** O que a rotação do fundo precisa de saber das imagens: o [CoilWallpaperImages] (outro nos testes). */
interface WallpaperImages {
    /** Carrega [url] para a cache no tamanho do ecrã: null se falhar, senão se é deitada (mais larga do que alta). */
    suspend fun preload(url: String): Boolean?

    /** A largura da foto original (já na cache), sem a descodificar; null se não se souber. */
    suspend fun originalWidth(url: String): Int?

    /** A luminosidade média (0..1) de [data] (um URL ou um ficheiro); null se não carregar. */
    suspend fun luminance(data: Any): Double?

    /** As imagens de uma pasta (ver ImageImport.images). */
    suspend fun folderImages(dir: File): List<File>
}

/**
 * Os fundos que rodam: as fotos online (Wikimedia, NASA) e as imagens de uma pasta, trocadas a cada
 * [AccountSettings.wallpaperMinutes] e de preferência do tom certo (claras de dia, escuras à noite: [night]).
 * Uma foto só aparece depois de carregada (para entrar com um fade sem saltos), deitada e com a resolução mínima.
 *
 * [sources] diz a fonte das fotos de cada fundo (ver OnlineWallpapers); null se não for de fotos online.
 */
class WallpaperRotation(
    private val scope: CoroutineScope,
    private val account: Flow<AccountSettings>,
    private val night: Flow<Boolean>,
    private val sources: (Wallpaper) -> OnlineWallpapers.Source?,
    private val images: WallpaperImages,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val _photo = MutableStateFlow<OnlinePhoto?>(null)

    /** Com um fundo de fotos online: a foto atual (já carregada na cache). */
    val onlinePhoto: StateFlow<OnlinePhoto?> = _photo.asStateFlow()

    private val _folderImage = MutableStateFlow<File?>(null)

    /** Com o fundo "pasta": a imagem dela que está a aparecer agora. */
    val folderImage: StateFlow<File?> = _folderImage.asStateFlow()

    /** Imagens que não servem de fundo (ao alto, ou que não carregaram). */
    private val badPhotos = mutableSetOf<String>()

    /** Largura original de cada foto online já carregada (para a "resolução mínima"). */
    private val photoWidths = mutableMapOf<String, Int>()

    /** Se cada foto (URL ou caminho) é clara (true) ou escura (false), para o modo dia/noite. */
    private val photoLight = mutableMapOf<String, Boolean>()

    /** Chamado uma vez, ao arrancar o ecrã (depois de tudo o resto estar pronto: a escolha corre já até esperar). */
    fun start() {
        scope.launch {
            // Fotos online: mostra uma e troca-a a cada AccountSettings.wallpaperMinutes.
            var lastNight: Boolean? = null
            combine(account, night) { a, n -> PhotoRotation(a.wallpaper, a.photoMinWidth, a.wallpaperMinutes * 60_000L, n) }
                .distinctUntilChanged().collectLatest { (wp, minWidth, rotateMs, night) ->
                    val source = sources(wp)
                    if (source == null) {
                        _photo.value = null
                        return@collectLatest
                    }
                    // Ao abrir: a mesma foto da última vez, já (está na cache); a escolha e as verificações vêm depois.
                    if (_photo.value == null) _photo.value = source.pool.lastShown(source.id)
                    // Trocar dia/noite pede logo uma foto do tom certo.
                    var next = lastNight != null && lastNight != night
                    lastNight = night
                    while (true) {
                        // Um intervalo mais curto do que o tempo que a foto já leva troca-a logo (ver PhotoPool.current).
                        showPhoto(source, next, minWidth, night, rotateMs)
                        wait(source.pool.msUntilRotation(source.id, rotateMs))
                        next = true
                    }
                }
        }
        scope.launch {
            // Uma pasta: uma imagem ao acaso, trocada com o mesmo intervalo das fotos online (e ao passar de dia para
            // noite, ou ao mudar o intervalo), de preferência do tom certo.
            combine(account, night) { a, n -> Triple(a.wallpaper, a.wallpaperMinutes * 60_000L, n) }
                .distinctUntilChanged().collectLatest { (wp, rotateMs, night) ->
                    if (wp.type != Wallpaper.TYPE_FOLDER) {
                        _folderImage.value = null
                        return@collectLatest
                    }
                    val dir = File(wp.value)
                    while (true) {
                        _folderImage.value = pickFolderImage(dir, night)
                        wait(rotateMs)
                    }
                }
        }
    }

    /**
     * Escolhe a foto de [source] e só a mostra depois de carregada (deitada e com pelo menos [minWidth] px);
     * senão tenta outra. A atual também é verificada: a resolução mínima pode ter subido.
     */
    private suspend fun showPhoto(source: OnlineWallpapers.Source, next: Boolean, minWidth: Int, night: Boolean, rotateMs: Long) {
        // Uma que sirva mas seja do tom errado (dia/noite) fica de reserva, se não aparecer nenhuma do certo.
        var fallback: OnlinePhoto? = null
        repeat(PHOTO_TRIES) { attempt ->
            val small = photoWidths.filterValues { it < minWidth }.keys
            val wrongTone = photoLight.filterValues { light -> light == night }.keys
            val image = source.pool.current(source.id, rotateMs, next = next || attempt > 0, skip = badPhotos + small + wrongTone)
                ?: return@repeat
            if (!preload(image.url)) {
                badPhotos += image.url
                return@repeat
            }
            if ((photoWidths[image.url] ?: Int.MAX_VALUE) < minWidth) return@repeat
            val light = isLight(image.url, image.url)
            if (light != null && light == night) {
                if (fallback == null) fallback = image
                return@repeat
            }
            _photo.value = image
            return
        }
        fallback?.let { if (it.url != _photo.value?.url) _photo.value = it }
    }

    /** Carrega a foto para a cache; false se falhar ou for ao alto. Guarda a largura original (resolução mínima). */
    private suspend fun preload(url: String): Boolean {
        val landscape = images.preload(url) ?: return false
        if (url !in photoWidths) images.originalWidth(url)?.let { photoWidths[url] = it }
        return landscape
    }

    /** Uma imagem da pasta, diferente da atual e do tom certo se houver; null se a pasta não tiver imagens. */
    private suspend fun pickFolderImage(dir: File, night: Boolean): File? {
        val all = images.folderImages(dir)
        val others = (all - setOfNotNull(_folderImage.value)).shuffled().ifEmpty { all }
        // Mede no máximo umas quantas (cada uma é uma leitura pequena da imagem).
        return others.take(FOLDER_TONE_TRIES).firstOrNull { f -> isLight(f, f.path).let { it == null || it != night } }
            ?: others.firstOrNull()
    }

    /** Acima de 35% de luminosidade média conta como clara; null se não carregar. Mede-se uma vez por imagem. */
    private suspend fun isLight(data: Any, key: String): Boolean? =
        photoLight[key] ?: images.luminance(data)?.let { it > LIGHT_LUMA }?.also { photoLight[key] = it }

    private companion object {
        const val PHOTO_TRIES = 10
        const val FOLDER_TONE_TRIES = 8
        const val LIGHT_LUMA = 0.35
    }
}

/** O que decide a rotação das fotos online: mudar qualquer um recomeça-a. */
private data class PhotoRotation(val wallpaper: Wallpaper, val minWidth: Int, val rotateMs: Long, val night: Boolean)

/** As imagens pelo Coil, na mesma cache que o ecrã usa para as desenhar. */
class CoilWallpaperImages(private val context: Context) : WallpaperImages {
    override suspend fun preload(url: String): Boolean? {
        val metrics = context.resources.displayMetrics
        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context)
                .data(url)
                .size(metrics.widthPixels, metrics.heightPixels)
                .scale(Scale.FILL)
                .build(),
        )
        val image = (result as? SuccessResult)?.image ?: return null
        return image.width > image.height
    }

    override suspend fun originalWidth(url: String): Int? = withContext(Dispatchers.IO) {
        runCatching {
            SingletonImageLoader.get(context).diskCache?.openSnapshot(url)?.use { snapshot ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(snapshot.data.toFile().path, options)
                options.outWidth.takeIf { it > 0 }
            }
        }.getOrNull()
    }

    /** Numa cópia de 32×18 (barata). */
    override suspend fun luminance(data: Any): Double? = withContext(Dispatchers.IO) {
        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context).data(data).size(32, 18).allowHardware(false).build(),
        )
        val bmp = (result as? SuccessResult)?.image?.toBitmap() ?: return@withContext null
        val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
        px.sumOf { c ->
            0.2126 * ((c shr 16) and 0xFF) + 0.7152 * ((c shr 8) and 0xFF) + 0.0722 * (c and 0xFF)
        } / (px.size * 255.0)
    }

    override suspend fun folderImages(dir: File): List<File> = ImageImport.images(dir)
}
