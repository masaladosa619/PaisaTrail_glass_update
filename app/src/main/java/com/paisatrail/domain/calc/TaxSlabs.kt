package com.paisatrail.domain.calc

/**
 * Income-tax slabs and limits for resident individuals below 60 years of age.
 *
 * APPLIES TO: Financial Year 2026-27 (Assessment Year 2027-28).
 * Union Budget 2026 left the personal slabs unchanged from FY 2025-26, so these are the
 * FY 2025-26 figures carried forward. When a new Budget changes anything, this is the only
 * file you need to edit. All amounts are in rupees.
 *
 * Not modelled: senior-citizen slabs, special-rate income (capital gains, lottery),
 * surcharge marginal relief, and the alternate minimum tax. Results are estimates only.
 */
data class Slab(
    /** Upper limit of this slab in rupees; null means "no upper limit". */
    val upTo: Long?,
    val ratePct: Double
)

data class SurchargeTier(val incomeAbove: Long, val ratePct: Double)

object TaxSlabs {
    const val FINANCIAL_YEAR = "FY 2026-27 (AY 2027-28)"

    val NEW_REGIME = listOf(
        Slab(400_000, 0.0),
        Slab(800_000, 5.0),
        Slab(1_200_000, 10.0),
        Slab(1_600_000, 15.0),
        Slab(2_000_000, 20.0),
        Slab(2_400_000, 25.0),
        Slab(null, 30.0)
    )

    val OLD_REGIME = listOf(
        Slab(250_000, 0.0),
        Slab(500_000, 5.0),
        Slab(1_000_000, 20.0),
        Slab(null, 30.0)
    )

    /** Standard deduction for salaried individuals and pensioners. */
    const val NEW_STANDARD_DEDUCTION = 75_000L
    const val OLD_STANDARD_DEDUCTION = 50_000L

    /** Section 87A rebate: full tax waived (up to the cap) when taxable income is within the limit. */
    const val NEW_REBATE_INCOME_LIMIT = 1_200_000L
    const val NEW_REBATE_MAX = 60_000L
    const val OLD_REBATE_INCOME_LIMIT = 500_000L
    const val OLD_REBATE_MAX = 12_500L

    /** Health and education cess on (tax + surcharge). */
    const val CESS_PCT = 4.0

    /** Highest matching tier applies. */
    val OLD_SURCHARGE = listOf(
        SurchargeTier(5_000_000, 10.0),
        SurchargeTier(10_000_000, 15.0),
        SurchargeTier(20_000_000, 25.0),
        SurchargeTier(50_000_000, 37.0)
    )
    val NEW_SURCHARGE = listOf(
        SurchargeTier(5_000_000, 10.0),
        SurchargeTier(10_000_000, 15.0),
        SurchargeTier(20_000_000, 25.0)
    )

    /** Old-regime deduction caps. */
    const val SEC_80C_CAP = 150_000L
    const val SEC_80CCD_1B_CAP = 50_000L
    const val HOME_LOAN_INTEREST_CAP = 200_000L
}
