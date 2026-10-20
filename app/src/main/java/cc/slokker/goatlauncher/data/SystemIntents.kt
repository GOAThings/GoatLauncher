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
