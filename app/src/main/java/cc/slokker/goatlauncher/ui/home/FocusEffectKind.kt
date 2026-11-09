package cc.slokker.goatlauncher.ui.home

import cc.slokker.goatlauncher.data.AccountSettings
import cc.slokker.goatlauncher.ui.Strings

/**
 * Os efeitos de foco dos cartões das apps (Definições → Aparência), um por id guardado ([id]), pela ordem em que o OK
 * os percorre. Aqui fica o que as Definições e o desenho (FocusEffect.kt) precisam de saber de cada um; o desenho em
 * si faz-se em focusEffect(), com `when (kind)` sem `else`: um efeito novo não compila sem o seu ramo.
 */
enum class FocusEffectKind(val id: String) {
    BORDER(AccountSettings.FOCUS_BORDER),
    GLOW(AccountSettings.FOCUS_GLOW),
    GLASS(AccountSettings.FOCUS_GLASS),
    REFLECTION(AccountSettings.FOCUS_REFLECTION),
    AURORA(AccountSettings.FOCUS_AURORA),
    VIEWFINDER(AccountSettings.FOCUS_VIEWFINDER),
    UNDERLINE(AccountSettings.FOCUS_UNDERLINE),
    RING(AccountSettings.FOCUS_RING),
    DIM(AccountSettings.FOCUS_DIM),
    SHADOW(AccountSettings.FOCUS_SHADOW),
    ;

    /** As animações que este efeito aceita, pela ordem do OK (vazia: sem a linha da animação). */
    val animations: List<String> get() = AccountSettings.focusAnimations(id)

    /** Se se escolhe a cor: "só sombra" e "escurecer os outros" não desenham nada colorido. */
    val takesColor: Boolean get() = this != SHADOW && this != DIM

    /** O vidro tem sempre o brilho a passar, mesmo sem animação escolhida. */
    val alwaysAnimated: Boolean get() = this == GLASS

    fun label(s: Strings): String = when (this) {
        BORDER -> s.effectBorder
        GLOW -> s.effectGlow
        GLASS -> s.effectGlass
        REFLECTION -> s.effectReflection
        AURORA -> s.effectAurora
        VIEWFINDER -> s.effectViewfinder
        UNDERLINE -> s.effectUnderline
        RING -> s.effectRing
        DIM -> s.effectDim
        SHADOW -> s.effectShadow
    }

    /** O nome da [animation] neste efeito: na aurora, o "brilho a rodar" é o gradiente a girar. */
    fun animationLabel(animation: String, s: Strings): String = when (animation) {
        AccountSettings.ANIM_PULSE -> s.animPulse
        AccountSettings.ANIM_BREATH -> s.animBreath
        AccountSettings.ANIM_SHINE -> if (this == AURORA) s.animSpin else s.animShine
        else -> s.animNone
    }

    /** A animação que este efeito mostra com [chosen] guardada: uma que ele não aceite fica "nenhuma". */
    fun animationOf(chosen: String): String = chosen.takeIf { it in animations } ?: AccountSettings.ANIM_NONE

    /** Quanto dura uma volta da animação (ms). */
    fun period(animation: String): Int = when {
        this == GLASS -> GLASS_PERIOD
        animation == AccountSettings.ANIM_PULSE -> 1200
        animation == AccountSettings.ANIM_BREATH -> 3200
        this == AURORA -> 4000
        else -> 2600
    }

    companion object {
        /** O vidro: o brilho passa na primeira parte do ciclo e descansa no resto. */
        const val GLASS_PERIOD = 3600

        /** O efeito de um id guardado; um desconhecido é o contorno (como lê o fromJson). */
        fun of(id: String): FocusEffectKind = entries.firstOrNull { it.id == id } ?: BORDER
    }
}
