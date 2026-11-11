package com.paisatrail.domain.calc

import kotlin.math.pow

enum class Compounding(val perYear: Int, val label: String) {
    MONTHLY(12, "Monthly"),
    QUARTERLY(4, "Quarterly"),
    HALF_YEARLY(2, "Half-yearly"),
    YEARLY(1, "Yearly")
}

data class DepositResult(val invested: Double, val maturity: Double) {
    val interest: Double get() = maturity - invested
}

/** Compound-interest maths for fixed and recurring deposits. Results are pre-tax estimates. */
object FdRdCalculator {

    /** A = P (1 + r/n)^(n t), with t in years. */
    fun fd(principal: Double, annualRatePct: Double, months: Int, compounding: Compounding): DepositResult {
        if (principal <= 0 || months <= 0) return DepositResult(maxOf(principal, 0.0), maxOf(principal, 0.0))
        val n = compounding.perYear.toDouble()
        val maturity = principal * (1 + annualRatePct / 100.0 / n).pow(n * months / 12.0)
        return DepositResult(principal, maturity)
    }

    /**
     * Each monthly instalment compounds for the months it stays invested:
     * M = sum over k = 1..months of P (1 + r/n)^(n k / 12).
     */
    fun rd(monthlyDeposit: Double, annualRatePct: Double, months: Int, compounding: Compounding): DepositResult {
        if (monthlyDeposit <= 0 || months <= 0) return DepositResult(0.0, 0.0)
        val n = compounding.perYear.toDouble()
        val growth = 1 + annualRatePct / 100.0 / n
        var maturity = 0.0
        for (monthsInvested in 1..months) {
            maturity += monthlyDeposit * growth.pow(n * monthsInvested / 12.0)
        }
        return DepositResult(monthlyDeposit * months, maturity)
    }
}
