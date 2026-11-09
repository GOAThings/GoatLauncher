package cc.slokker.goatlauncher.data

import android.os.Build
import cc.slokker.goatlauncher.BuildConfig

/**
 * Como o launcher se apresenta lá fora: o id da app, a versão e o tipo de TV. Um só sítio para o `X-Goat-Client` do
 * backend, a identificação nos serviços das fotos do fundo e os relatórios (antes cada um fazia a sua conta).
 *
 * O `app` do pedido de ligação à conta é outro ([cc.slokker.goatlauncher.data.api.BACKEND_APP], "launcher"): é o nome
 * do tipo de aparelho no site, não a identificação do cliente.
 */
class ClientInfo(val version: String, val platform: String) {
    /** `goatlauncher/<versão> (<plataforma>)`, por exemplo `goatlauncher/0.8.9 (android-tv)`. */
    val goatClient: String get() = "$APP/$version ($platform)"

    /**
     * Para serviços públicos que pedem uma identificação com contacto (a política do Wikimedia): a de [goatClient] com a
     * página do projeto, por exemplo `goatlauncher/0.9.11 (android-tv; +https://github.com/GOAThings/GoatLauncher)`.
     */
    val publicUserAgent: String get() = "$APP/$version ($platform; +https://github.com/${Updater.RELEASES_REPO})"

    companion object {
        /** O id da app nos relatórios (`app`) e no início das identificações; o site conta com ele, não muda. */
        const val APP = "goatlauncher"

        /** Esta app, nesta TV. */
        val current: ClientInfo by lazy { ClientInfo(BuildConfig.VERSION_NAME, platformOf(Build.MANUFACTURER)) }

        /** "fire-tv" nas TVs da Amazon, "android-tv" nas outras. */
        internal fun platformOf(manufacturer: String?): String =
            if (manufacturer.equals("Amazon", ignoreCase = true)) "fire-tv" else "android-tv"
    }
}
