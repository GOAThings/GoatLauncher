package cc.slokker.goatlauncher.ui

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
    val api = (LocalContext.current.applicationContext as LauncherApp).container.backend

    CompositionLocalProvider(LocalStrings provides vm.strings) {
        Box(Modifier.fillMaxSize().background(GoatColors.Background)) {
            // Até ler a cópia local das definições (milissegundos), só o fundo escuro: evita piscar o fundo por defeito.
            if (!ready) return@Box
            WallpaperView(account.wallpaper, reddit?.url)
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
