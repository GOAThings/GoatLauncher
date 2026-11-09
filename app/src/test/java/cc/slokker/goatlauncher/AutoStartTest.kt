package cc.slokker.goatlauncher

import cc.slokker.goatlauncher.data.DeviceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O pedido do código (AutoStart.unlockIntent) só conta uma vez: um intent guardado pelo sistema não o repete. E a regra
 * do arranque automático, a mesma ao ligar e ao acordar.
 */
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

    @Test
    fun onBootTheChosenAppOpensAfterItsDelay() {
        val device = DeviceSettings(autostart = "cc.slokker.itv", autostartDelay = 10)
        assertEquals(AutoStart.Pending("cc.slokker.itv", 10_000L), AutoStart.pending(device, wake = false))
        assertNull(AutoStart.pending(DeviceSettings(), wake = false))
    }

    @Test
    fun onWakeOnlyWithTheWakeOption() {
        val device = DeviceSettings(autostart = "cc.slokker.itv", autostartDelay = 3)
        assertNull(AutoStart.pending(device, wake = true))
        assertEquals(AutoStart.Pending("cc.slokker.itv", 3_000L), AutoStart.pending(device.copy(autostartWake = true), wake = true))
        assertNull(AutoStart.pending(DeviceSettings(autostartWake = true), wake = true))
    }

    @Test
    fun oncePerBootAndOnlyRightAfterIt() {
        assertTrue(AutoStart.isFirstOpenAfterBoot(boot = 7, done = 6, sinceBoot = 60_000L))
        // Já abriu neste arranque (o ecrã inicial foi recriado).
        assertFalse(AutoStart.isFirstOpenAfterBoot(boot = 7, done = 7, sinceBoot = 60_000L))
        // A app foi atualizada (ou o processo reiniciado) muito depois de ligar.
        assertFalse(AutoStart.isFirstOpenAfterBoot(boot = 7, done = 6, sinceBoot = 11 * 60_000L))
        // Sem BOOT_COUNT no Android.
        assertFalse(AutoStart.isFirstOpenAfterBoot(boot = -1, done = -1, sinceBoot = 1_000L))
    }
}
