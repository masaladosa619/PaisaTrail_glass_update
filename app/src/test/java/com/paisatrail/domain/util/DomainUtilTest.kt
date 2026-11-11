package com.paisatrail.domain.util

import com.paisatrail.domain.insights.InsightsCalculator
import com.paisatrail.domain.insights.Pacing
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainUtilTest {

    @Test
    fun `indian digit grouping`() {
        assertEquals("₹0", Money.format(0))
        assertEquals("₹999", Money.format(99_900))
        assertEquals("₹1,000", Money.format(100_000))
        assertEquals("₹1,00,000", Money.format(10_000_000))
        assertEquals("₹12,34,56,789", Money.format(12_345_678_900))
        assertEquals("₹5,749.41", Money.format(574_941))
        assertEquals("-₹250.50", Money.format(-25_050))
        assertEquals("₹749.00", Money.format(74_900, showPaise = true))
        assertEquals("₹1,07,186", Money.formatRupees(107_185.90))
    }

    @Test
    fun `parsing money input`() {
        assertEquals(12_345_678L, Money.parseToPaise("₹1,23,456.78"))
        assertEquals(74_900L, Money.parseToPaise("Rs. 749"))
        assertEquals(74_950L, Money.parseToPaise("749.5"))
        assertEquals(5L, Money.parseToPaise("0.049"))
        assertNull(Money.parseToPaise(""))
        assertNull(Money.parseToPaise("abc"))
        assertNull(Money.parseToPaise("1.2.3"))
        assertEquals("749", Money.toInput(74_900))
        assertEquals("749.05", Money.toInput(74_905))
    }

    @Test
    fun `upi link`() {
        assertEquals(
            "upi://pay?pa=asha@okbank&pn=Asha%20Rao&am=1500.00&cu=INR&tn=Goa%20trip",
            UpiLink.build("asha@okbank", "Asha Rao", 150_000, "Goa trip")
        )
        assertEquals("upi://pay?pa=dev@upi&cu=INR", UpiLink.build("dev@upi", "", 0))
        assertTrue(UpiLink.isValidVpa("asha.rao-1@okbank"))
        assertFalse(UpiLink.isValidVpa("asha rao"))
        assertFalse(UpiLink.isValidVpa("asha@"))
    }

    @Test
    fun `due dates clamp to the month length and roll over`() {
        assertEquals(LocalDate.of(2026, 11, 15), DueDates.nextDue(15, LocalDate.of(2026, 11, 10)))
        assertEquals(0, DueDates.daysUntil(10, LocalDate.of(2026, 11, 10)))
        assertEquals(LocalDate.of(2026, 12, 5), DueDates.nextDue(5, LocalDate.of(2026, 11, 10)))
        assertEquals(LocalDate.of(2027, 2, 28), DueDates.nextDue(31, LocalDate.of(2027, 2, 1)))
        assertEquals(LocalDate.of(2027, 1, 3), DueDates.nextDue(3, LocalDate.of(2026, 12, 20)))
    }

    @Test
    fun `insights - under the limit`() {
        val insights = InsightsCalculator.compute(
            spentPaise = 1_000_000,
            month = YearMonth.of(2026, 11),
            today = LocalDate.of(2026, 11, 10),
            incomePaise = 8_000_000,
            budgetPaise = 0
        )

        assertEquals(100_000L, insights.dailyAveragePaise)
        assertEquals(3_000_000L, insights.projectedPaise)
        assertEquals(Pacing.UNDER, insights.pacing)
        assertTrue(insights.pacingMessage.contains("income"))
        assertTrue(insights.pacingMessage.contains("₹1,000"))
    }

    @Test
    fun `insights - over the budget suggests a daily cap`() {
        val insights = InsightsCalculator.compute(
            spentPaise = 2_000_000,
            month = YearMonth.of(2026, 11),
            today = LocalDate.of(2026, 11, 10),
            incomePaise = 8_000_000,
            budgetPaise = 3_000_000
        )

        // Projected 60,000 vs budget 30,000; 10,000 left over 20 days = 500 a day.
        assertEquals(6_000_000L, insights.projectedPaise)
        assertEquals(Pacing.OVER, insights.pacing)
        assertTrue(insights.pacingMessage.contains("budget"))
        assertTrue(insights.pacingMessage.contains("₹500 a day"))
    }

    @Test
    fun `insights - no limit and past months`() {
        val noLimit = InsightsCalculator.compute(500_000, YearMonth.of(2026, 11), LocalDate.of(2026, 11, 5), 0, 0)
        assertEquals(Pacing.UNKNOWN, noLimit.pacing)

        val past = InsightsCalculator.compute(3_100_000, YearMonth.of(2026, 10), LocalDate.of(2026, 11, 5), 0, 4_000_000)
        assertEquals(31, past.daysElapsed)
        assertEquals(100_000L, past.dailyAveragePaise)
        assertEquals(Pacing.UNDER, past.pacing)

        val future = InsightsCalculator.compute(0, YearMonth.of(2026, 12), LocalDate.of(2026, 11, 5), 0, 4_000_000)
        assertEquals(0L, future.projectedPaise)
        assertEquals(Pacing.UNKNOWN, future.pacing)
    }
}
