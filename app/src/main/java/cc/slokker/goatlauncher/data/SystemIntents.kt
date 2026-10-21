package cc.slokker.goatlauncher.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

/** Intents do sistema usados pelo ecrã inicial. */
object SystemIntents {
    private const val GLOBAL_SEARCH = "android.search.action.GLOBAL_SEARCH"

    /** Pesquisa do sistema (Google / Assistente), se a TV tiver. Ver os <queries> do manifesto. */
    fun search(context: Context): Intent? =
        listOf(Intent(GLOBAL_SEARCH), Intent(Intent.ACTION_ASSIST))
            .firstOrNull { it.resolveActivity(context.packageManager) != null }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Abre as definições de rede (Wi-Fi e cabo, no Android TV ficam no mesmo ecrã); senão as do Android. */
    fun openNetworkSettings(context: Context): Boolean =
        listOf(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS)
            .any { start(context, Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    fun accessibilitySettings(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun androidSettings(): Intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun appInfo(pkg: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Abre [intent]; devolve false se não houver quem o abra ou a app recusar. */
    fun start(context: Context, intent: Intent?): Boolean {
        if (intent == null) return false
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w("SystemIntents", "Sem atividade para $intent")
            false
        } catch (e: SecurityException) {
            Log.w("SystemIntents", "Recusado: $intent", e)
            false
        }
    }
}
