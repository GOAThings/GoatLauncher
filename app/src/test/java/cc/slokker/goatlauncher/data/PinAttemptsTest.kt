package cc.slokker.goatlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PinAttemptsTest {
    private fun failures(n: Int, at: Long = 1_000L): PinAttempts {
        var attempts = PinAttempts()
        repeat(n) { attempts = attempts.failed(at) }
        return attempts
    }

    @Test
    fun theFirstFailuresDoNotWaitAndThenTheDelayGrows() {
        assertEquals(0L, failures(3).delayMs)
        val delays = (4..12).map { failures(it).delayMs }
        assertEquals(delays.sorted(), delays)
        assertEquals(true, delays.first() > 0)
        assertEquals(true, delays[1] > delays[0] && delays[5] > delays[4])
        // Fica no máximo, sem crescer para sempre.
        assertEquals(failures(100).delayMs, failures(10).delayMs)
    }

    @Test
    fun waitCountsDownFromTheLastFailure() {
        val attempts = failures(4, at = 10_000L)
        assertEquals(attempts.delayMs, attempts.remainingMs(10_000L))
        assertEquals(attempts.delayMs - 5_000, attempts.remainingMs(15_000L))
        assertEquals(0L, attempts.remainingMs(10_000L + attempts.delayMs))
        assertEquals(0L, attempts.remainingMs(Long.MAX_VALUE / 2))
    }

    @Test
    fun aRebootNeverShortensTheWait() {
        val attempts = failures(5, at = 3_600_000L)
        // O relógio recomeçou do zero: conta a espera inteira.
        assertEquals(attempts.delayMs, attempts.remainingMs(1_000L))
    }

    @Test
    fun aFailureAfterTheWaitAsksForMore() {
        val first = failures(4, at = 1_000L)
        val next = first.failed(1_000L + first.delayMs)
        assertEquals(true, next.delayMs > first.delayMs)
        assertEquals(next.delayMs, next.remainingMs(1_000L + first.delayMs))
    }
}
