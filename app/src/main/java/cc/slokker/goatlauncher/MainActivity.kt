package cc.slokker.goatlauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import cc.slokker.goatlauncher.ui.LauncherRoot
import cc.slokker.goatlauncher.ui.LauncherViewModel
import cc.slokker.goatlauncher.ui.theme.GoatTheme

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

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
        setContent {
            GoatTheme { LauncherRoot(vm) }
        }
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
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
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
