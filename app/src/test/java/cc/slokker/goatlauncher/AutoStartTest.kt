package cc.slokker.goatlauncher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** O pedido do código (AutoStart.unlockIntent) só conta uma vez: um intent guardado pelo sistema não o repete. */
class AutoStartTest {
    @Test
    fun aNewRequestCounts() {
        assertTrue(AutoStart.isFresh(createdAt = 1_000L, now = 1_000L))
        assertTrue(AutoStart.isFresh(createdAt = 1_000L, now = 9_000L))
        // Sem hora: um `am start --es cc.slokker.goatlauncher.UNLOCK <pacote>` à mão.
        assertTrue(AutoStart.isFresh(createdAt = -1L, now = 5_000_000L))
    }

    @Test
    fun theIntentTheSystemReplaysLaterDoesNot() {
        // O processo foi fechado e o ecrã reaberto (Home) muito depois, com o intent que o abriu da primeira vez.
        assertFalse(AutoStart.isFresh(createdAt = 1_000L, now = 1_000L + 10 * 60_000L))
        // A TV reiniciou (o relógio recomeçou): nunca é de agora.
        assertFalse(AutoStart.isFresh(createdAt = 5_000_000L, now = 1_000L))
    }
}
