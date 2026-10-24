package cc.slokker.goatlauncher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.slokker.goatlauncher.LauncherApp
import cc.slokker.goatlauncher.ui.activation.ActivationScreen
import cc.slokker.goatlauncher.ui.home.HomeScreen
import cc.slokker.goatlauncher.ui.home.ToastPill
import cc.slokker.goatlauncher.ui.common.semanticsOnlyForScreenReaders
import cc.slokker.goatlauncher.ui.home.WallpaperView
import cc.slokker.goatlauncher.ui.theme.GoatColors
import kotlinx.coroutines.delay

@Composable
fun LauncherRoot(vm: LauncherViewModel) {
    val ready by vm.ready.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val reddit by vm.redditImage.collectAsStateWithLifecycle()
    val folderImage by vm.folderImage.collectAsStateWithLifecycle()
    val api = (LocalContext.current.applicationContext as LauncherApp).container.backend

    // O contexto da Activity já vem na língua escolhida (AppCompat); ao mudar, a Activity é recriada.
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val strings = remember(configuration) { Strings(context) }
    CompositionLocalProvider(LocalStrings provides strings) {
        Box(Modifier.fillMaxSize().semanticsOnlyForScreenReaders().background(GoatColors.Background)) {
            // Até ler a cópia local das definições (milissegundos), só o fundo escuro: evita piscar o fundo por defeito.
            if (!ready) return@Box
            WallpaperView(account.wallpaper, reddit?.url ?: folderImage)
            // Modo noite: o fundo escurece (a interface por cima fica igual).
            val night by vm.night.collectAsStateWithLifecycle()
            val dim by animateFloatAsState(if (night) NIGHT_DIM else 0f, tween(600), label = "night")
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = dim }.background(Color.Black))
            when (screen) {
                // Início, Apps e Definições são separadores do mesmo ecrã (a barra fica, só o conteúdo muda).
                Screen.Home, Screen.Apps, Screen.Settings -> HomeScreen(vm)
                Screen.Activation -> ActivationScreen(api, onApproved = vm::onLinked, onBack = vm::closeActivation)
            }
            toast?.let { text ->
                ToastPill(text, Modifier.align(Alignment.TopCenter))
                LaunchedEffect(text) {
                    delay(2_500)
                    vm.clearToast()
                }
            }
        }
    }
}

private const val NIGHT_DIM = 0.35f
