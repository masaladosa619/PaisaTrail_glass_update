package com.paisatrail.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisatrail.domain.model.Category
import com.paisatrail.domain.model.TxnType
import com.paisatrail.domain.util.Money
import com.paisatrail.ui.components.AnimatedAmount
import com.paisatrail.ui.components.CategoryPicker
import com.paisatrail.ui.components.EmptyState
import com.paisatrail.ui.components.MoneyField
import com.paisatrail.ui.components.Pill
import com.paisatrail.ui.components.ScreenScaffold
import com.paisatrail.ui.components.TypeToggle
import com.paisatrail.ui.components.bouncyClickable
import com.paisatrail.ui.components.color
import com.paisatrail.ui.components.glass
import com.paisatrail.ui.components.riseIn
import com.paisatrail.ui.components.shimmer
import com.paisatrail.ui.theme.AppColors
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val ONE_MONTH_NOTICE = "Only 1-month statements are supported"
private val ROW_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

@Composable
fun ImportScreen(onClose: () -> Unit, viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.load(uri)
    }

    ScreenScaffold(title = "Import statement", onBack = onClose) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                ImportState.Idle -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EmptyState(
                        icon = Icons.Filled.PictureAsPdf,
                        title = "Drop in a month of spending",
                        body = "Pick the PDF statement you downloaded from your bank. It is read on this phone " +
                            "and you review every row before anything is saved."
                    )
                    Pill(
                        ONE_MONTH_NOTICE,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = { pickPdf.launch(arrayOf("application/pdf")) }) { Text("Choose PDF") }
                }

                is ImportState.Working -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        current.message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.shimmer()
                    )
                }

                is ImportState.NeedsPassword -> PasswordPrompt(
                    wrongPassword = current.wrongPassword,
                    onSubmit = viewModel::retryWithPassword,
                    onCancel = viewModel::reset
                )

                is ImportState.Review -> ReviewList(
                    review = current,
                    onToggle = viewModel::toggle,
                    onEdit = viewModel::edit,
                    onSave = viewModel::save
                )

                is ImportState.Done -> Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EmptyState(
                        icon = Icons.Filled.CheckCircle,
                        title = "${current.saved} transactions saved",
                        body = if (current.duplicates > 0) {
                            "${current.duplicates} were already in your history and were skipped."
                        } else {
                            "They're on your dashboard now."
                        }
                    )
                    Button(onClick = onClose) { Text("Back to dashboard") }
                }

                is ImportState.Error -> Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EmptyState(icon = Icons.Filled.ErrorOutline, title = "That didn't work", body = current.message)
                    Button(onClick = { pickPdf.launch(arrayOf("application/pdf")) }) { Text("Choose another PDF") }
                    TextButton(onClick = onClose) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun PasswordPrompt(wrongPassword: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var password by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("This PDF is locked", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Banks usually protect statements with a password (often part of your name plus date of birth, " +
                "or your customer id). It is used once to open the file and is never stored.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("PDF password") },
            singleLine = true,
            isError = wrongPassword,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        if (wrongPassword) {
            Text("That password didn't open the file.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = { onSubmit(password) }, enabled = password.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text("Unlock")
            }
        }
    }
}

@Composable
private fun ReviewList(
    review: ImportState.Review,
    onToggle: (Int) -> Unit,
    onEdit: (Int, String, Long, TxnType, Category) -> Unit,
    onSave: () -> Unit
) {
    var editing by remember { mutableStateOf<Int?>(null) }
    val included = review.rows.filter { it.included }
    val totalExpense = included.filter { it.type == TxnType.EXPENSE }.sumOf { it.amountPaise }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("All read.", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Tap a row to fix it, untick to leave it out." +
                        if (review.skippedLines > 0) " ${review.skippedLines} unreadable line(s) were skipped." else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Text(
                included.size.toString(),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
            itemsIndexed(review.rows, key = { _, row -> row.key }) { index, row ->
                Row(
                    modifier = Modifier
                        .animateItem()
                        .riseIn(index)
                        .fillMaxWidth()
                        .bouncyClickable { editing = row.key }
                        .padding(start = 6.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = row.included, onCheckedChange = { onToggle(row.key) })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.merchant, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            ROW_DATE.format(row.date),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Pill(
                        row.category.label,
                        containerColor = row.category.color().copy(alpha = 0.18f),
                        contentColor = row.category.color()
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        (if (row.type == TxnType.EXPENSE) "-" else "+") + Money.format(row.amountPaise),
                        color = if (row.type == TxnType.EXPENSE) AppColors.Expense else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.glass(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), strong = true),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Total expense", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    AnimatedAmount(paise = totalExpense, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Pill(
                    ONE_MONTH_NOTICE,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onSave,
                    enabled = included.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Save ${included.size} transactions")
                }
            }
        }
    }

    val row = review.rows.firstOrNull { it.key == editing }
    if (row != null) {
        EditRowDialog(
            row = row,
            onDismiss = { editing = null },
            onConfirm = { merchant, amount, type, category ->
                onEdit(row.key, merchant, amount, type, category)
                editing = null
            }
        )
    }
}

@Composable
private fun EditRowDialog(
    row: ImportRow,
    onDismiss: () -> Unit,
    onConfirm: (String, Long, TxnType, Category) -> Unit
) {
    var merchant by remember(row.key) { mutableStateOf(row.merchant) }
    var amount by remember(row.key) { mutableStateOf(Money.toInput(row.amountPaise)) }
    var type by remember(row.key) { mutableStateOf(row.type) }
    var category by remember(row.key) { mutableStateOf(row.category) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fix this row") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(row.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                MoneyField(value = amount, onValueChange = { amount = it }, label = "Amount")
                TypeToggle(selected = type, onSelect = { type = it })
                CategoryPicker(selected = category, onSelect = { category = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(merchant, Money.parseToPaise(amount) ?: row.amountPaise, type, category) }) {
                Text("Apply")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
