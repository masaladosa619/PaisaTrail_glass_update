package com.paisatrail.domain.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitCalculatorTest {

    private val asha = 1L
    private val dev = 2L

    @Test
    fun `equal split hands leftover paise to the first participants`() {
        val shares = SplitCalculator.equalSplit(10_000L, listOf(SELF_ID, asha, dev))

        assertEquals(listOf(3_334L, 3_333L, 3_333L), shares.map { it.sharePaise })
        assertEquals(10_000L, shares.sumOf { it.sharePaise })
        assertTrue(SplitCalculator.equalSplit(10_000L, emptyList()).isEmpty())
        assertTrue(SplitCalculator.equalSplit(0L, listOf(SELF_ID, asha)).isEmpty())
    }

    @Test
    fun `custom split must add up exactly`() {
        val good = listOf(ShareInput(SELF_ID, 7_000L), ShareInput(asha, 3_000L))
        val short = listOf(ShareInput(SELF_ID, 7_000L), ShareInput(asha, 2_999L))
        val negative = listOf(ShareInput(SELF_ID, 11_000L), ShareInput(asha, -1_000L))

        assertTrue(SplitCalculator.isValidCustomSplit(10_000L, good))
        assertFalse(SplitCalculator.isValidCustomSplit(10_000L, short))
        assertFalse(SplitCalculator.isValidCustomSplit(10_000L, negative))
        assertFalse(SplitCalculator.isValidCustomSplit(10_000L, emptyList()))
    }

    @Test
    fun `friends owe you their share of what you paid`() {
        val dinner = ExpenseInput(
            paidById = SELF_ID,
            amountPaise = 90_000L,
            shares = SplitCalculator.equalSplit(90_000L, listOf(SELF_ID, asha, dev))
        )

        val balances = SplitCalculator.balancesWithSelf(listOf(dinner), emptyList())

        assertEquals(30_000L, balances[asha])
        assertEquals(30_000L, balances[dev])
        assertEquals(60_000L, SplitCalculator.totalOwedToYou(balances))
        assertEquals(0L, SplitCalculator.totalYouOwe(balances))
    }

    @Test
    fun `you owe your share of what a friend paid and friend-to-friend debts are ignored`() {
        val cab = ExpenseInput(
            paidById = asha,
            amountPaise = 60_000L,
            shares = listOf(ShareInput(SELF_ID, 20_000L), ShareInput(asha, 20_000L), ShareInput(dev, 20_000L))
        )

        val balances = SplitCalculator.balancesWithSelf(listOf(cab), emptyList())

        assertEquals(-20_000L, balances[asha])
        assertEquals(null, balances[dev])
        assertEquals(20_000L, SplitCalculator.totalYouOwe(balances))
    }

    @Test
    fun `expenses net out and settlements move balances toward zero`() {
        val dinner = ExpenseInput(SELF_ID, 100_000L, listOf(ShareInput(SELF_ID, 50_000L), ShareInput(asha, 50_000L)))
        val movie = ExpenseInput(asha, 40_000L, listOf(ShareInput(SELF_ID, 20_000L), ShareInput(asha, 20_000L)))
        val expenses = listOf(dinner, movie)

        assertEquals(30_000L, SplitCalculator.balancesWithSelf(expenses, emptyList())[asha])

        val partPayment = SettlementInput(fromId = asha, toId = SELF_ID, amountPaise = 10_000L)
        assertEquals(20_000L, SplitCalculator.balancesWithSelf(expenses, listOf(partPayment))[asha])

        val fullPayment = SettlementInput(fromId = asha, toId = SELF_ID, amountPaise = 30_000L)
        assertEquals(0L, SplitCalculator.balancesWithSelf(expenses, listOf(fullPayment))[asha])
    }

    @Test
    fun `paying a friend back clears what you owe`() {
        val cab = ExpenseInput(dev, 50_000L, listOf(ShareInput(SELF_ID, 25_000L), ShareInput(dev, 25_000L)))
        val payback = SettlementInput(fromId = SELF_ID, toId = dev, amountPaise = 25_000L)

        val balances = SplitCalculator.balancesWithSelf(listOf(cab), listOf(payback))

        assertEquals(0L, balances[dev])
        assertEquals(0L, SplitCalculator.totalYouOwe(balances))
    }
}
