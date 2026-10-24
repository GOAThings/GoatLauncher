package cc.slokker.goatlauncher.ui.home

/**
 * Se a batida desta frame é num canto: bateu numa borda ([hitX] nas laterais, [hitY] em cima/em baixo) e, nesse
 * momento, a outra coordenada está a menos de [tolerance] da sua borda (ou bateu nas duas).
 */
internal fun isCornerHit(hitX: Boolean, hitY: Boolean, x: Float, y: Float, maxX: Float, maxY: Float, tolerance: Float): Boolean {
    fun near(v: Float, max: Float) = v <= tolerance || v >= max - tolerance
    return (hitX && near(y, maxY)) || (hitY && near(x, maxX))
}
