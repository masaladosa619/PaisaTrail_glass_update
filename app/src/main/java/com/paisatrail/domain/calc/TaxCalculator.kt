package com.paisatrail.domain.calc

/** All amounts in rupees per year. Deductions only affect the old regime. */
data class TaxInput(
    val grossIncome: Long,
    val salaried: Boolean = true,
    val sec80C: Long = 0,
    val sec80D: Long = 0,
    val hraExemption: Long = 0,
    val homeLoanInterest: Long = 0,
    val nps80ccd1b: Long = 0,
    val otherDeductions: Long = 0
)

data class RegimeResult(
    val taxableIncome: Long,
    val slabTax: Double,
    val rebate: Double,
    val surcharge: Double,
    val cess: Double,
    val totalTax: Double
)

data class TaxComparison(val oldRegime: RegimeResult, val newRegime: RegimeResult) {
    val newIsBetter: Boolean get() = newRegime.totalTax <= oldRegime.totalTax
    val savings: Double get() = kotlin.math.abs(oldRegime.totalTax - newRegime.totalTax)
}

object TaxCalculator {

    fun compare(input: TaxInput): TaxComparison = TaxComparison(oldRegime(input), newRegime(input))

    fun newRegime(input: TaxInput): RegimeResult {
        val standard = if (input.salaried) TaxSlabs.NEW_STANDARD_DEDUCTION else 0L
        val taxable = maxOf(0L, input.grossIncome - standard)
        val slabTax = slabTax(taxable, TaxSlabs.NEW_REGIME)
        val afterRebate = when {
            taxable <= TaxSlabs.NEW_REBATE_INCOME_LIMIT -> maxOf(0.0, slabTax - TaxSlabs.NEW_REBATE_MAX)
            // Marginal relief: tax cannot exceed the income earned above the rebate limit.
            else -> minOf(slabTax, (taxable - TaxSlabs.NEW_REBATE_INCOME_LIMIT).toDouble())
        }
        return finish(taxable, slabTax, afterRebate, TaxSlabs.NEW_SURCHARGE)
    }

    fun oldRegime(input: TaxInput): RegimeResult {
        val standard = if (input.salaried) TaxSlabs.OLD_STANDARD_DEDUCTION else 0L
        val deductions = standard +
            minOf(maxOf(input.sec80C, 0), TaxSlabs.SEC_80C_CAP) +
            maxOf(input.sec80D, 0) +
            maxOf(input.hraExemption, 0) +
            minOf(maxOf(input.homeLoanInterest, 0), TaxSlabs.HOME_LOAN_INTEREST_CAP) +
            minOf(maxOf(input.nps80ccd1b, 0), TaxSlabs.SEC_80CCD_1B_CAP) +
            maxOf(input.otherDeductions, 0)
        val taxable = maxOf(0L, input.grossIncome - deductions)
        val slabTax = slabTax(taxable, TaxSlabs.OLD_REGIME)
        val afterRebate = if (taxable <= TaxSlabs.OLD_REBATE_INCOME_LIMIT) {
            maxOf(0.0, slabTax - TaxSlabs.OLD_REBATE_MAX)
        } else {
            slabTax
        }
        return finish(taxable, slabTax, afterRebate, TaxSlabs.OLD_SURCHARGE)
    }

    fun slabTax(taxableIncome: Long, slabs: List<Slab>): Double {
        var tax = 0.0
        var lower = 0L
        for (slab in slabs) {
            val upper = slab.upTo ?: Long.MAX_VALUE
            if (taxableIncome > lower) {
                tax += (minOf(taxableIncome, upper) - lower) * slab.ratePct / 100.0
            }
            lower = upper
            if (taxableIncome <= upper) break
        }
        return tax
    }

    private fun finish(
        taxable: Long,
        slabTax: Double,
        taxAfterRebate: Double,
        surchargeTiers: List<SurchargeTier>
    ): RegimeResult {
        val surchargePct = surchargeTiers.lastOrNull { taxable > it.incomeAbove }?.ratePct ?: 0.0
        val surcharge = taxAfterRebate * surchargePct / 100.0
        val cess = (taxAfterRebate + surcharge) * TaxSlabs.CESS_PCT / 100.0
        return RegimeResult(
            taxableIncome = taxable,
            slabTax = slabTax,
            rebate = slabTax - taxAfterRebate,
            surcharge = surcharge,
            cess = cess,
            totalTax = taxAfterRebate + surcharge + cess
        )
    }
}
