package com.paisatrail.ui.calc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paisatrail.domain.calc.Compounding
import com.paisatrail.domain.calc.FdRdCalculator
import com.paisatrail.domain.util.Money
import com.paisatrail.ui.components.FilterPill
import com.paisatrail.ui.components.KeyValueRow
import com.paisatrail.ui.components.MoneyField
import com.paisatrail.ui.components.ScreenScaffold
import com.paisatrail.ui.components.SectionCard
import com.paisatrail.ui.components.SectionLabel

@Composable
fun FdRdScreen(onClose: () -> Unit) {
    var isFd by rememberSaveable { mutableStateOf(true) }
    var amount by rememberSaveable { mutableStateOf("100000") }
    var rate by rememberSaveable { mutableStateOf("7") }
    var months by rememberSaveable { mutableStateOf("12") }
    var compounding by rememberSaveable { mutableStateOf(Compounding.QUARTERLY) }

    val principal = amount.toDoubleOrNull() ?: 0.0
    val ratePct = rate.toDoubleOrNull() ?: 0.0
    val tenure = months.toIntOrNull() ?: 0
    val valid = principal > 0 && tenure in 1..600 && ratePct in 0.0..50.0
    val result = if (!valid) {
        null
    } else if (isFd) {
        FdRdCalculator.fd(principal, ratePct, tenure, compounding)
    } else {
        FdRdCalculator.rd(principal, ratePct, tenure, compounding)
    }

    ScreenScaffold(title = "FD / RD calculator", onBack = onClose) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("Fixed deposit", isFd, { isFd = true })
                FilterPill("Recurring deposit", !isFd, { isFd = false })
            }

            MoneyField(
                value = amount,
                onValueChange = { amount = it },
                label = if (isFd) "Deposit amount" else "Monthly deposit"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = rate,
                    onValueChange = { raw ->
                        val filtered = raw.filter { it.isDigit() || it == '.' }.take(5)
                        if (filtered.count { it == '.' } <= 1) rate = filtered
                    },
                    label = { Text("Interest % p.a.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = months,
                    onValueChange = { raw -> months = raw.filter { it.isDigit() }.take(3) },
                    label = { Text("Tenure (months)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            SectionLabel("Interest compounds")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Compounding.entries.forEach { option ->
                    FilterPill(option.label, compounding == option, { compounding = option })
                }
            }

            SectionCard {
                if (result == null) {
                    Text(
                        "Enter an amount, a rate up to 50% and a tenure of 1 to 600 months.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SectionLabel("Maturity value")
                    Text(
                        Money.formatRupees(result.maturity),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    KeyValueRow("You put in", Money.formatRupees(result.invested))
                    KeyValueRow("Interest earned", Money.formatRupees(result.interest))
                }
            }
            Text(
                "Estimate only. Banks round and credit interest on their own schedule, and interest is " +
                    "taxable; TDS is not deducted here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
