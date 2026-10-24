package cc.slokker.goatlauncher

import android.service.dreams.DreamService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.data.ImageImport
import cc.slokker.goatlauncher.data.SunTimes
import cc.slokker.goatlauncher.data.Wallpaper
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.Strings
import cc.slokker.goatlauncher.ui.home.AmbientScene
import cc.slokker.goatlauncher.ui.home.WallpaperView
import cc.slokker.goatlauncher.ui.home.nowPlaying
import cc.slokker.goatlauncher.ui.theme.GoatTheme
import kotlinx.coroutines.delay
import java.io.File
import java.util.Calendar

/**
 * O protetor de ecrã do Android (Definições do Android → Protetor de ecrã → GoatLauncher): o mesmo que o modo
 * ambiente do ecrã inicial (o fundo com as horas, ou o relógio a ressaltar), mas em qualquer app.
 */
class AmbientDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        // O Compose procura o dono do ciclo de vida na janela: o protetor de ecrã não é uma Activity.
        window.decorView.setViewTreeLifecycleOwner(this)
        window.decorView.setViewTreeSavedStateRegistryOwner(this)
        val container = (application as LauncherApp).container
        setContentView(ComposeView(this).apply { setContent { GoatTheme { DreamContent(container) } } })
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }
}

@Composable
private fun DreamContent(c: AppContainer) {
    LaunchedEffect(Unit) { c.settings.init() }
    val account by c.settings.account.collectAsState()
    val context = LocalContext.current
    val strings = remember { Strings(context) }
    // A imagem do fundo que roda (Reddit ou pasta): a que o ecrã inicial mostrou por último, ou uma da pasta.
    val rotating by produceState<Any?>(null, account.wallpaper) {
        value = when (account.wallpaper.type) {
            Wallpaper.TYPE_REDDIT -> c.reddit.lastShown(account.wallpaper.value)?.url
            Wallpaper.TYPE_FOLDER -> ImageImport.images(File(account.wallpaper.value)).randomOrNull()
            else -> null
        }
    }
    val watchNext by produceState(emptyMap(), Unit) { value = runCatching { c.watchNext.load() }.getOrDefault(emptyMap()) }
    // Noite: o fundo escurece, como no ecrã inicial (verifica-se de minuto a minuto).
    val night by produceState(false, account) {
        while (true) {
            val now = Calendar.getInstance()
            val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val place = c.settings.device.value.location?.let { it.lat to it.lon } ?: c.weather.cachedLocation()
            val sun = place?.let { (lat, lon) -> SunTimes.of(lat, lon, now.timeInMillis) }
            value = SunTimes.isNight(account, minute, sun)
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    CompositionLocalProvider(LocalStrings provides strings) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (account.ambientStyle == AccountSettings.AMBIENT_PHOTOS) {
                WallpaperView(account.wallpaper, rotating)
                if (night) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = DREAM_NIGHT_DIM)))
            }
            AmbientScene(
                style = account.ambientStyle,
                clock24h = account.clock24h,
                weather = c.weather.cached().takeIf { account.showWeather },
                nowPlaying = nowPlaying(watchNext),
                caption = null,
            )
        }
    }
}

/** Igual ao escurecer do modo noite no ecrã inicial. */
private const val DREAM_NIGHT_DIM = 0.35f
