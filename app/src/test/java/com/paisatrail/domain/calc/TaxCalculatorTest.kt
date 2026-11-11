package com.paisatrail.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expected values are worked by hand from TaxSlabs.kt (FY 2026-27). */
class TaxCalculatorTest {

    @Test
    fun `new regime - salaried income of 12,75,000 pays nothing thanks to the 87A rebate`() {
        val result = TaxCalculator.newRegime(TaxInput(grossIncome = 1_275_000))

        assertEquals(1_200_000L, result.taxableIncome)
        assertEquals(60_000.0, result.slabTax, 0.001)
        assertEquals(60_000.0, result.rebate, 0.001)
        assertEquals(0.0, result.totalTax, 0.001)
    }

    @Test
    fun `new regime - 15,75,000 salaried`() {
        val result = TaxCalculator.newRegime(TaxInput(grossIncome = 1_575_000))

        assertEquals(1_500_000L, result.taxableIncome)
        assertEquals(105_000.0, result.slabTax, 0.001)
        assertEquals(4_200.0, result.cess, 0.001)
        assertEquals(109_200.0, result.totalTax, 0.001)
    }

    @Test
    fun `new regime - marginal relief just above the rebate limit`() {
        val result = TaxCalculator.newRegime(TaxInput(grossIncome = 1_210_000, salaried = false))

        assertEquals(61_500.0, result.slabTax, 0.001)
        assertEquals(10_400.0, result.totalTax, 0.001)
    }

    @Test
    fun `old regime - deductions are capped and applied`() {
        val result = TaxCalculator.oldRegime(
            TaxInput(grossIncome = 1_050_000, sec80C = 200_000)
        )

        assertEquals(850_000L, result.taxableIncome)
        assertEquals(82_500.0, result.slabTax, 0.001)
        assertEquals(85_800.0, result.totalTax, 0.001)
    }

    @Test
    fun `old regime - rebate wipes out tax up to five lakh taxable`() {
        val result = TaxCalculator.oldRegime(TaxInput(grossIncome = 550_000))

        assertEquals(500_000L, result.taxableIncome)
        assertEquals(12_500.0, result.slabTax, 0.001)
        assertEquals(0.0, result.totalTax, 0.001)
    }

    @Test
    fun `surcharge applies above fifty lakh`() {
        val result = TaxCalculator.newRegime(TaxInput(grossIncome = 6_000_000, salaried = false))

        // Slab tax: 20k + 40k + 60k + 80k + 100k + 30% of 36L = 13,80,000
        assertEquals(1_380_000.0, result.slabTax, 0.001)
        assertEquals(138_000.0, result.surcharge, 0.001)
        assertEquals(60_720.0, result.cess, 0.001)
        assertEquals(1_578_720.0, result.totalTax, 0.001)
    }

    @Test
    fun `comparison picks the cheaper regime`() {
        val plain = TaxCalculator.compare(TaxInput(grossIncome = 1_500_000))
        assertTrue(plain.newIsBetter)

        val heavyDeductions = TaxCalculator.compare(
            TaxInput(
                grossIncome = 1_500_000,
                sec80C = 150_000,
                sec80D = 50_000,
                hraExemption = 300_000,
                homeLoanInterest = 200_000,
                nps80ccd1b = 50_000
            )
        )
        // Old: taxable 7,00,000 -> 52,500 + cess = 54,600. New: taxable 14,25,000 -> 93,750 + cess = 97,500.
        assertEquals(54_600.0, heavyDeductions.oldRegime.totalTax, 0.001)
        assertEquals(97_500.0, heavyDeductions.newRegime.totalTax, 0.001)
        assertFalse(heavyDeductions.newIsBetter)
        assertEquals(42_900.0, heavyDeductions.savings, 0.001)
    }

    @Test
    fun `zero and negative income never produce tax`() {
        assertEquals(0.0, TaxCalculator.newRegime(TaxInput(grossIncome = 0)).totalTax, 0.001)
        assertEquals(0.0, TaxCalculator.oldRegime(TaxInput(grossIncome = -100)).totalTax, 0.001)
    }
}
