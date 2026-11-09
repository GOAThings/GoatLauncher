package cc.slokker.goatlauncher.ui.home

import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.ui.Strings

/**
 * Como é o ecrã inicial: a disposição (Android TV ou Apple TV) e, na Android, o estilo do "Continuar a ver"
 * (Definições → Aparência). Guarda-se como as duas strings de sempre ([layout], [continueStyle]); aqui fica o que
 * cada um quer dizer, num só sítio, para o ecrã inicial, a pré-visualização e as Definições.
 * Um estilo novo é uma entrada nova: os `when (style)` sem `else` não compilam até ele ter o seu ramo.
 */
enum class HomeStyle(val layout: String, val continueStyle: String?) {
    /** Uma fila de cartões por cima dos favoritos. */
    CardRow(AccountSettings.LAYOUT_ANDROID, AccountSettings.STYLE_ROW),

    /** O conteúdo mais recente em destaque, a encher o ecrã. */
    Shelf(AccountSettings.LAYOUT_ANDROID, AccountSettings.STYLE_SHELF),

    /** Como [Shelf], com o painel "A seguir" ao lado. */
    Spotlight(AccountSettings.LAYOUT_ANDROID, AccountSettings.STYLE_SPOTLIGHT),

    /** Os favoritos numa coluna à esquerda e o conteúdo da app com foco à direita. */
    SideColumn(AccountSettings.LAYOUT_ANDROID, AccountSettings.STYLE_COLUMN),

    /** Um carrossel de cartões grandes por cima dos favoritos. */
    Carousel(AccountSettings.LAYOUT_ANDROID, AccountSettings.STYLE_CAROUSEL),

    /** Disposição Apple TV: cartões grandes, a doca dos favoritos e as outras apps por baixo; sem separador Apps. */
    AppleTv(AccountSettings.LAYOUT_APPLE_TV, null),
    ;

    /** Há separador Apps (na Apple TV as outras apps estão no Início, por baixo da doca). */
    val hasAppsTab: Boolean get() = this != AppleTv

    /** O destaque em grande por cima dos favoritos (ShelfHero), com ‹ › a passar pelos conteúdos. */
    val shelfHero: Boolean get() = this == Shelf || this == Spotlight

    /** O conteúdo com foco enche o fundo do ecrã (se a imagem tiver a forma do ecrã). */
    val heroBackdrop: Boolean get() = shelfHero || this == SideColumn

    /** O nome do estilo do "Continuar a ver" (só os da disposição Android). */
    fun styleLabel(s: Strings): String = when (this) {
        CardRow -> s.styleRow
        Shelf -> s.styleShelf
        Spotlight -> s.styleSpotlightNext
        SideColumn -> s.styleColumn
        Carousel -> s.styleCarousel
        AppleTv -> APPLE_TV_NAME
    }

    /** "Apple TV" ou "Android TV · Destaque" (o resumo na página principal das Definições). */
    fun label(s: Strings): String = if (this == AppleTv) APPLE_TV_NAME else "$ANDROID_TV_NAME · ${styleLabel(s)}"

    companion object {
        /** Os nomes das disposições (nomes próprios: não se traduzem). */
        const val ANDROID_TV_NAME = "Android TV"
        const val APPLE_TV_NAME = "Apple TV"

        /** Os estilos da disposição Android, pela ordem das Definições ([AccountSettings.STYLES]). */
        val ANDROID: List<HomeStyle> = entries.filter { it.continueStyle != null }

        /** O estilo destas definições. Um estilo desconhecido é a fila de cartões (como lê o fromJson). */
        fun of(account: AccountSettings): HomeStyle = of(account.layout, account.continueStyle)

        fun of(layout: String, continueStyle: String): HomeStyle =
            if (layout == AccountSettings.LAYOUT_APPLE_TV) AppleTv else ANDROID.firstOrNull { it.continueStyle == continueStyle } ?: CardRow
    }
}
