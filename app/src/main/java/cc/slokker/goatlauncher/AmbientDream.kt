package cc.slokker.goatlauncher

import android.service.dreams.DreamService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import cc.slokker.goatlauncher.data.OnlinePhoto
import cc.slokker.goatlauncher.data.Wallpaper
import cc.slokker.goatlauncher.ui.LocalStrings
import cc.slokker.goatlauncher.ui.Strings
import cc.slokker.goatlauncher.ui.home.AmbientScene
import cc.slokker.goatlauncher.ui.home.WallpaperView
import cc.slokker.goatlauncher.ui.home.nowPlaying
import cc.slokker.goatlauncher.ui.state.NightClock
import cc.slokker.goatlauncher.ui.theme.GoatTheme
import kotlinx.coroutines.flow.map
import java.io.File

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
        moveTo(Lifecycle.State.RESUMED)
    }

    override fun onDreamingStopped() {
        moveTo(Lifecycle.State.CREATED)
        super.onDreamingStopped()
    }

    /** O sistema pode tirar a janela antes de parar o sonho: depois de DESTROYED já não se volta atrás (rebentava). */
    private fun moveTo(state: Lifecycle.State) {
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) lifecycleRegistry.currentState = state
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }
}

@Composable
private fun DreamContent(c: AppContainer) {
    val account by c.settings.account.collectAsState()
    val context = LocalContext.current
    val strings = remember { Strings(context) }
    // A foto online que o ecrã inicial mostrou por último (com o crédito, quando a fonte o dá).
    val photo by produceState<OnlinePhoto?>(null, account.wallpaper) { value = c.wallpapers.lastShown(account.wallpaper) }
    // A imagem do fundo que roda (fotos online ou pasta): a foto acima, ou uma da pasta.
    val rotating by produceState<Any?>(null, account.wallpaper, photo) {
        value = when (account.wallpaper.type) {
            Wallpaper.TYPE_FOLDER -> ImageImport.images(File(account.wallpaper.value)).randomOrNull()
            else -> photo?.url
        }
    }
    val watchNext by produceState(emptyMap(), Unit) { value = runCatching { c.watchNext.load() }.getOrNull().orEmpty() }
    // Noite: o fundo escurece, como no ecrã inicial (a mesma regra, ver NightClock). O sítio é o escolhido nas
    // definições ou o último que o IP deu (o ecrã inicial é que o vai buscar).
    val nightClock = remember(c) {
        NightClock(c.settings.account, c.settings.device.map { d -> d.location?.let { it.lat to it.lon } ?: c.weather.cachedLocation() })
    }
    val night by nightClock.night.collectAsState(false)
    CompositionLocalProvider(LocalStrings provides strings) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (account.ambientStyle == AccountSettings.AMBIENT_PHOTOS) {
                WallpaperView(account.wallpaper, rotating)
                if (night) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = NightClock.DIM)))
            }
            AmbientScene(
                style = account.ambientStyle,
                clock24h = account.clock24h,
                weather = c.weather.cached().takeIf { account.showWeather },
                nowPlaying = nowPlaying(watchNext),
                // O título e o crédito: a atribuição tem de aparecer onde a foto aparece.
                caption = photo?.title?.ifBlank { null },
                credit = photo?.credit,
            )
        }
    }
}
