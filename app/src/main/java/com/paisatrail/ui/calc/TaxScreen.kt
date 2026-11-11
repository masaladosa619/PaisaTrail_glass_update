package com.paisatrail.ui.calc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paisatrail.domain.calc.RegimeResult
import com.paisatrail.domain.calc.TaxCalculator
import com.paisatrail.domain.calc.TaxInput
import com.paisatrail.domain.calc.TaxSlabs
import com.paisatrail.domain.util.Money
import com.paisatrail.ui.components.FilterPill
import com.paisatrail.ui.components.MoneyField
import com.paisatrail.ui.components.Pill
import com.paisatrail.ui.components.ScreenScaffold
import com.paisatrail.ui.components.SectionLabel
import com.paisatrail.ui.components.glass
import com.paisatrail.ui.components.riseIn
import com.paisatrail.ui.components.tintedGlass
import com.paisatrail.ui.theme.AppColors

@Composable
fun TaxScreen(onClose: () -> Unit) {
    var income by rememberSaveable { mutableStateOf("") }
    var salaried by rememberSaveable { mutableStateOf(true) }
    var sec80c by rememberSaveable { mutableStateOf("") }
    var sec80d by rememberSaveable { mutableStateOf("") }
    var hra by rememberSaveable { mutableStateOf("") }
    var homeLoan by rememberSaveable { mutableStateOf("") }
    var nps by rememberSaveable { mutableStateOf("") }
    var other by rememberSaveable { mutableStateOf("") }

    fun rupees(text: String): Long = text.substringBefore('.').toLongOrNull() ?: 0L

    val gross = rupees(income)
    val comparison = if (gross > 0) {
        TaxCalculator.compare(
            TaxInput(
                grossIncome = gross,
                salaried = salaried,
                sec80C = rupees(sec80c),
                sec80D = rupees(sec80d),
                hraExemption = rupees(hra),
                homeLoanInterest = rupees(homeLoan),
                nps80ccd1b = rupees(nps),
                otherDeductions = rupees(other)
            )
        )
    } else {
        null
    }

    ScreenScaffold(title = "Income tax estimator", onBack = onClose) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Pill(
                "ESTIMATE ONLY · ${TaxSlabs.FINANCIAL_YEAR}",
                containerColor = AppColors.Warning.copy(alpha = 0.18f),
                contentColor = AppColors.Warning
            )
            MoneyField(value = income, onValueChange = { income = it }, label = "Gross income for the year")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("Salaried", salaried, { salaried = true })
                FilterPill("Not salaried", !salaried, { salaried = false })
            }

            if (comparison != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RegimeCard(
                        title = "Old regime",
                        result = comparison.oldRegime,
                        better = !comparison.newIsBetter,
                        modifier = Modifier.weight(1f)
                    )
                    RegimeCard(
                        title = "New regime",
                        result = comparison.newRegime,
                        better = comparison.newIsBetter,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    when {
                        comparison.savings < 1.0 -> "Both regimes come out the same for these numbers."
                        comparison.newIsBetter -> "The new regime saves about ${Money.formatRupees(comparison.savings)}."
                        else -> "The old regime saves about ${Money.formatRupees(comparison.savings)}."
                    },
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text("Enter your yearly income to compare both regimes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionLabel("Old-regime deductions (optional)", modifier = Modifier.padding(top = 6.dp))
            MoneyField(value = sec80c, onValueChange = { sec80c = it }, label = "80C (PF, ELSS, LIC…) · max ${Money.format(TaxSlabs.SEC_80C_CAP * 100, false)}")
            MoneyField(value = sec80d, onValueChange = { sec80d = it }, label = "80D (health insurance)")
            MoneyField(value = hra, onValueChange = { hra = it }, label = "HRA exemption")
            MoneyField(value = homeLoan, onValueChange = { homeLoan = it }, label = "Home-loan interest · max ${Money.format(TaxSlabs.HOME_LOAN_INTEREST_CAP * 100, false)}")
            MoneyField(value = nps, onValueChange = { nps = it }, label = "NPS 80CCD(1B) · max ${Money.format(TaxSlabs.SEC_80CCD_1B_CAP * 100, false)}")
            MoneyField(value = other, onValueChange = { other = it }, label = "Other deductions")

            Text(
                "This is an estimate for a resident individual under 60, using the slabs in TaxSlabs.kt for " +
                    "${TaxSlabs.FINANCIAL_YEAR}. It leaves out capital gains, special-rate income and surcharge " +
                    "marginal relief. It is not tax advice; check with a tax professional before you file.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RegimeCard(title: String, result: RegimeResult, better: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    // The cheaper regime gets a green-tinted pane; the other one stays plain glass.
    Surface(
        modifier = if (better) {
            modifier.riseIn().tintedGlass(shape, MaterialTheme.colorScheme.primary)
        } else {
            modifier.riseIn(1).glass(shape)
        },
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(Money.formatRupees(result.totalTax), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (better) {
                Pill(
                    "LOWER TAX",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Line("Taxable", Money.format(result.taxableIncome * 100, false))
            Line("Slab tax", Money.formatRupees(result.slabTax))
            if (result.rebate > 0) Line("Rebate", "-" + Money.formatRupees(result.rebate))
            if (result.surcharge > 0) Line("Surcharge", Money.formatRupees(result.surcharge))
            Line("Cess", Money.formatRupees(result.cess))
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
