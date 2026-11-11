package com.paisatrail.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FdRdCalculatorTest {

    @Test
    fun `fd with quarterly compounding`() {
        val result = FdRdCalculator.fd(100_000.0, 7.0, 12, Compounding.QUARTERLY)

        assertEquals(100_000.0, result.invested, 0.001)
        assertEquals(107_185.90, result.maturity, 0.01)
        assertEquals(7_185.90, result.interest, 0.01)
    }

    @Test
    fun `fd with monthly compounding over two years`() {
        val result = FdRdCalculator.fd(50_000.0, 8.0, 24, Compounding.MONTHLY)

        assertEquals(58_644.40, result.maturity, 0.01)
    }

    @Test
    fun `more frequent compounding never earns less`() {
        val yearly = FdRdCalculator.fd(100_000.0, 7.0, 36, Compounding.YEARLY).maturity
        val halfYearly = FdRdCalculator.fd(100_000.0, 7.0, 36, Compounding.HALF_YEARLY).maturity
        val quarterly = FdRdCalculator.fd(100_000.0, 7.0, 36, Compounding.QUARTERLY).maturity
        val monthly = FdRdCalculator.fd(100_000.0, 7.0, 36, Compounding.MONTHLY).maturity

        assertTrue(yearly < halfYearly && halfYearly < quarterly && quarterly < monthly)
    }

    @Test
    fun `rd with quarterly compounding`() {
        val result = FdRdCalculator.rd(5_000.0, 7.0, 12, Compounding.QUARTERLY)

        assertEquals(60_000.0, result.invested, 0.001)
        assertEquals(62_310.66, result.maturity, 0.01)
        assertEquals(2_310.66, result.interest, 0.01)
    }

    @Test
    fun `zero rate returns exactly what was put in`() {
        assertEquals(100_000.0, FdRdCalculator.fd(100_000.0, 0.0, 12, Compounding.QUARTERLY).maturity, 0.001)
        assertEquals(60_000.0, FdRdCalculator.rd(5_000.0, 0.0, 12, Compounding.MONTHLY).maturity, 0.001)
    }

    @Test
    fun `invalid input is handled without blowing up`() {
        assertEquals(0.0, FdRdCalculator.rd(0.0, 7.0, 12, Compounding.MONTHLY).maturity, 0.001)
        assertEquals(0.0, FdRdCalculator.fd(-5.0, 7.0, 12, Compounding.MONTHLY).maturity, 0.001)
        assertEquals(1_000.0, FdRdCalculator.fd(1_000.0, 7.0, 0, Compounding.MONTHLY).maturity, 0.001)
    }
}
