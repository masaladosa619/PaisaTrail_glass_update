package com.paisatrail.ui.planner

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisatrail.data.local.CommitmentEntity
import com.paisatrail.domain.model.CommitmentType
import com.paisatrail.domain.util.DueDates
import com.paisatrail.domain.util.Money
import com.paisatrail.ui.components.MoneyField
import com.paisatrail.ui.components.ProgressBar
import com.paisatrail.ui.components.SectionCard
import com.paisatrail.ui.components.SectionLabel
import com.paisatrail.ui.components.bouncyClickable
import com.paisatrail.ui.components.riseIn
import com.paisatrail.ui.theme.AppColors
import com.paisatrail.work.ReminderNotifications
import java.time.LocalDate
import java.util.Locale

@Composable
fun PlannerScreen(
    onOpenFdRd: () -> Unit,
    onOpenTax: () -> Unit,
    viewModel: PlannerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editBudget by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CommitmentEntity?>(null) }
    var notificationsBlocked by remember { mutableStateOf(false) }

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setReminders(granted)
        notificationsBlocked = !granted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        Text("Planner", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.riseIn())

        // --- Monthly budget ---
        SectionCard(modifier = Modifier.bouncyClickable(shape = RoundedCornerShape(22.dp)) { editBudget = true }) {
            val budget = state.budget.monthlyBudgetPaise
            if (budget <= 0) {
                Text("Monthly budget", fontWeight = FontWeight.SemiBold)
                Text("Tap to set a spending limit and your income.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                val left = budget - state.spentThisMonthPaise
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Monthly budget", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(
                        if (left < 0) "${Money.format(-left, false)} over" else "${Money.format(left, false)} left",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (left < 0) AppColors.Expense else MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                ProgressBar(
                    fraction = state.spentThisMonthPaise.toFloat() / budget.toFloat(),
                    color = if (left < 0) AppColors.Expense else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${Money.format(state.spentThisMonthPaise, false)} of ${Money.format(budget, false)} spent this month",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            if (state.budget.monthlyIncomePaise > 0) {
                Text(
                    "Income ${Money.format(state.budget.monthlyIncomePaise, false)} a month",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        // --- Commitments ---
        SectionLabel("Commitments", modifier = Modifier.padding(top = 6.dp))
        SectionCard {
            CommitmentType.entries.forEachIndexed { index, type ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                CommitmentGroup(
                    type = type,
                    items = state.ofType(type),
                    onAdd = { editing = CommitmentEntity(type = type, name = "", amountPaise = 0, dueDay = 1) },
                    onEdit = { editing = it }
                )
            }
        }
        if (state.monthlyCommittedPaise > 0) {
            val income = state.budget.monthlyIncomePaise
            val share = if (income > 0) {
                String.format(Locale.US, " (%.0f%% of your income)", state.monthlyCommittedPaise * 100.0 / income)
            } else {
                ""
            }
            Text(
                "${Money.format(state.monthlyCommittedPaise, false)} a month is already committed$share.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        // --- Reminders ---
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Due-date reminders", fontWeight = FontWeight.SemiBold)
                    Text(
                        "A notification the day before and on the day something is due.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = state.remindersEnabled,
                    onCheckedChange = { enable ->
                        when {
                            !enable -> viewModel.setReminders(false)
                            ReminderNotifications.canNotify(context) -> viewModel.setReminders(true)
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> notificationsBlocked = true
                        }
                    }
                )
            }
            if (notificationsBlocked) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Notifications are turned off for PaisaTrail. Allow them in your phone's app settings, then switch this on.",
                    color = AppColors.Warning,
                    fontSize = 12.sp
                )
            }
        }

        // --- Calculators ---
        SectionLabel("Calculators", modifier = Modifier.padding(top = 6.dp))
        SectionCard {
            NavRow(Icons.Filled.Savings, "FD / RD calculator", "Maturity value with compounding", onOpenFdRd)
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
            NavRow(Icons.Filled.Calculate, "Income tax estimator", "Old vs new regime, side by side", onOpenTax)
        }
        Spacer(modifier = Modifier.height(110.dp))
    }

    if (editBudget) {
        BudgetDialog(
            budgetPaise = state.budget.monthlyBudgetPaise,
            incomePaise = state.budget.monthlyIncomePaise,
            onDismiss = { editBudget = false },
            onSave = { budget, income ->
                viewModel.saveBudget(budget, income)
                editBudget = false
            }
        )
    }

    editing?.let { commitment ->
        CommitmentDialog(
            initial = commitment,
            onDismiss = { editing = null },
            onSave = {
                viewModel.saveCommitment(it)
                editing = null
            },
            onDelete = {
                viewModel.deleteCommitment(commitment.id)
                editing = null
            }
        )
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().bouncyClickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CommitmentGroup(
    type: CommitmentType,
    items: List<CommitmentEntity>,
    onAdd: () -> Unit,
    onEdit: (CommitmentEntity) -> Unit
) {
    val today = LocalDate.now()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(type.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        val summary = when {
            items.isEmpty() -> "None yet"
            type == CommitmentType.CREDIT_CARD -> {
                val limit = items.sumOf { it.creditLimitPaise ?: 0L }
                val used = items.sumOf { it.amountPaise }
                if (limit > 0) String.format(Locale.US, "%.1f%% used", used * 100.0 / limit) else Money.format(used, false)
            }
            else -> "${Money.format(items.sumOf { it.amountPaise }, false)}/mo"
        }
        val overLimit = type == CommitmentType.CREDIT_CARD &&
            items.any { (it.creditLimitPaise ?: 0L) > 0 && it.amountPaise > (it.creditLimitPaise ?: 0L) }
        Text(
            summary,
            fontSize = 13.sp,
            color = if (overLimit) AppColors.Expense else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    items.forEach { item ->
        Row(
            modifier = Modifier.fillMaxWidth().bouncyClickable { onEdit(item) }.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontSize = 14.sp)
                val days = DueDates.daysUntil(item.dueDay, today)
                Text(
                    when (days) {
                        0 -> "Due today"
                        1 -> "Due tomorrow"
                        else -> "Due in $days days (day ${item.dueDay})"
                    },
                    fontSize = 12.sp,
                    color = if (days <= 1) AppColors.Warning else MaterialTheme.colorScheme.onSurfaceVariant
                )
                val limit = item.creditLimitPaise ?: 0L
                if (type == CommitmentType.CREDIT_CARD && limit > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    ProgressBar(
                        fraction = item.amountPaise.toFloat() / limit.toFloat(),
                        color = if (item.amountPaise > limit) AppColors.Expense else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(item.amountPaise), fontWeight = FontWeight.SemiBold)
                val limit = item.creditLimitPaise ?: 0L
                if (type == CommitmentType.CREDIT_CARD && limit > 0) {
                    Text(
                        String.format(Locale.US, "%.1f%% of %s", item.amountPaise * 100.0 / limit, Money.format(limit, false)),
                        fontSize = 11.sp,
                        color = if (item.amountPaise > limit) AppColors.Expense else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    TextButton(onClick = onAdd) { Text("+ Add") }
}

@Composable
private fun BudgetDialog(budgetPaise: Long, incomePaise: Long, onDismiss: () -> Unit, onSave: (Long, Long) -> Unit) {
    var budget by remember { mutableStateOf(if (budgetPaise > 0) Money.toInput(budgetPaise) else "") }
    var income by remember { mutableStateOf(if (incomePaise > 0) Money.toInput(incomePaise) else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget and income") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MoneyField(value = budget, onValueChange = { budget = it }, label = "Monthly budget")
                MoneyField(value = income, onValueChange = { income = it }, label = "Monthly income (take-home)")
                Text(
                    "Income powers the \"% income used\" chip and pacing. Leave either blank to switch it off.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(Money.parseToPaise(budget) ?: 0L, Money.parseToPaise(income) ?: 0L) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CommitmentDialog(
    initial: CommitmentEntity,
    onDismiss: () -> Unit,
    onSave: (CommitmentEntity) -> Unit,
    onDelete: () -> Unit
) {
    val isCard = initial.type == CommitmentType.CREDIT_CARD
    var name by remember { mutableStateOf(initial.name) }
    var amount by remember { mutableStateOf(if (initial.amountPaise > 0) Money.toInput(initial.amountPaise) else "") }
    var dueDay by remember { mutableStateOf(initial.dueDay.toString()) }
    var limit by remember { mutableStateOf(initial.creditLimitPaise?.let { Money.toInput(it) }.orEmpty()) }

    val day = dueDay.toIntOrNull()
    val dayValid = day != null && day in 1..31
    val amountPaise = Money.parseToPaise(amount) ?: 0L
    val valid = name.isNotBlank() && dayValid && (isCard || amountPaise > 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                (if (initial.id == 0L) "Add " else "Edit ") + when (initial.type) {
                    CommitmentType.SUBSCRIPTION -> "subscription"
                    CommitmentType.EMI -> "EMI"
                    CommitmentType.CREDIT_CARD -> "credit card"
                }
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                MoneyField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = if (isCard) "Current outstanding" else "Amount per month"
                )
                if (isCard) {
                    MoneyField(value = limit, onValueChange = { limit = it }, label = "Credit limit")
                }
                OutlinedTextField(
                    value = dueDay,
                    onValueChange = { raw -> dueDay = raw.filter { it.isDigit() }.take(2) },
                    label = { Text("Due day of month (1-31)") },
                    isError = dueDay.isNotEmpty() && !dayValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (initial.id != 0L) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            amountPaise = amountPaise,
                            dueDay = day ?: 1,
                            creditLimitPaise = if (isCard) Money.parseToPaise(limit)?.takeIf { it > 0 } else null
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
