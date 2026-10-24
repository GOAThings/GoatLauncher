package cc.slokker.goatlauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cc.slokker.goatlauncher.data.NavSounds
import cc.slokker.goatlauncher.ui.LauncherRoot
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import cc.slokker.goatlauncher.ui.LauncherViewModel
import cc.slokker.goatlauncher.ui.theme.GoatTheme

class MainActivity : AppCompatActivity() {
    private val vm: LauncherViewModel by viewModels()
    private val sounds by lazy { NavSounds(this) }

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.reloadWatchNext()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // É o ecrã inicial: o Voltar que chega até aqui não faz nada (os ecrãs tratam o seu antes).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        requestWatchNextPermissions()
        hideSystemBars()
        if (BuildConfig.DEBUG && intent.getBooleanExtra("demo_watch_next", false)) vm.enableDemoWatchNext()
        intent.getStringExtra(AutoStart.EXTRA_UNLOCK)?.let { vm.requestUnlock(this, it) }
        setContent {
            GoatTheme { LauncherRoot(vm) }
        }
        // Sons de navegação (Aparência): ligam-se e desligam-se com a definição.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.account.map { it.navSounds }.distinctUntilChanged().collect { sounds.setEnabled(it) }
            }
        }
        // O fundo (desenho ou foto) enche sempre o ecrã: o fundo da janela por baixo era só mais uma camada a pintar.
        window.setBackgroundDrawable(null)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        sounds.onKey(event)
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        sounds.setEnabled(false)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Ecrã inteiro, como no Android TV (que não tem barras). Em tablets/Waydroid, as barras voltam com um gesto. */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: o botão Home com o launcher já aberto chega aqui.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.onHomePressed()
        // Uma app bloqueada abriu por outro caminho (ver HomeGuardService): pede o código.
        intent.getStringExtra(AutoStart.EXTRA_UNLOCK)?.let { vm.requestUnlock(this, it) }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onPause() {
        vm.onPause()
        super.onPause()
    }

    /**
     * READ_TV_LISTINGS (e READ_EPG_DATA) são permissões "perigosas" em quem as define: pede-se ao arrancar.
     * Só se pede o que existe nesta TV e ainda não foi dado.
     */
    private fun requestWatchNextPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val missing = WATCH_NEXT_PERMISSIONS.filter { name ->
            val info = runCatching { packageManager.getPermissionInfo(name, 0) }.getOrNull() ?: return@filter false
            val dangerous = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.protection == PermissionInfo.PROTECTION_DANGEROUS
            } else {
                @Suppress("DEPRECATION")
                info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE == PermissionInfo.PROTECTION_DANGEROUS
            }
            dangerous && ContextCompat.checkSelfPermission(this, name) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionRequest.launch(missing.toTypedArray())
    }

    private companion object {
        val WATCH_NEXT_PERMISSIONS = listOf(
            "android.permission.READ_TV_LISTINGS",
            "com.android.providers.tv.permission.READ_EPG_DATA",
        )
    }
}
