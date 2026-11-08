package cc.slokker.goatlauncher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Cantos da interface: arredondados, mas discretos. Mudar aqui muda a app toda. */
object GoatShapes {
    /** Painéis laterais, guia, definições, cartões grandes (erro, ativação, HUD). */
    val Panel = RoundedCornerShape(12.dp)

    /** Cartões pequenos (troca de canal, avisos, toast). */
    val Card = RoundedCornerShape(10.dp)

    /** Linhas de lista, campos de texto, botões, chips e cápsulas de estado. */
    val Control = RoundedCornerShape(ControlRadius)

    /** Etiquetas, células do guia, teclas. */
    val Small = RoundedCornerShape(4.dp)

    /**
     * A barra dos separadores, com [BarPadding] à volta dos botões: o raio dos controlos mais esse espaço, para os
     * cantos de fora acompanharem os de dentro.
     */
    val Bar = RoundedCornerShape(ControlRadius + BarPadding)
}

/** Espaço entre a barra dos separadores e os botões lá dentro. */
val BarPadding = 5.dp

/** Raio dos controlos (também o do brilho de foco, que tem de coincidir). */
val ControlRadius = 8.dp
