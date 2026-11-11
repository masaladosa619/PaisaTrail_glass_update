package com.paisatrail.ui.txn

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisatrail.domain.model.TxnSource
import com.paisatrail.ui.components.CategoryPicker
import com.paisatrail.ui.components.MoneyField
import com.paisatrail.ui.components.ScreenScaffold
import com.paisatrail.ui.components.SectionLabel
import com.paisatrail.ui.components.TypeToggle
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val TIME_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

@Composable
fun TxnEditScreen(onClose: () -> Unit, viewModel: TxnEditViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(form.finished) {
        if (form.finished) onClose()
    }

    ScreenScaffold(
        title = if (form.isNew) "Add transaction" else "Edit transaction",
        onBack = onClose,
        actions = {
            if (!form.isNew) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
        }
    ) { padding ->
        if (!form.loaded) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@ScreenScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            MoneyField(
                value = form.amount,
                onValueChange = viewModel::setAmount,
                label = "Amount",
                isError = form.error != null
            )
            form.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }

            TypeToggle(selected = form.type, onSelect = viewModel::setType)

            OutlinedTextField(
                value = form.merchant,
                onValueChange = viewModel::setMerchant,
                label = { Text("Paid to / received from") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SectionLabel("Category")
            CategoryPicker(selected = form.category, onSelect = viewModel::setCategory)

            SectionLabel("When")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val date = form.dateTime.toLocalDate()
                        DatePickerDialog(
                            context,
                            { _, year, month, day -> viewModel.setDate(LocalDate.of(year, month + 1, day)) },
                            date.year,
                            date.monthValue - 1,
                            date.dayOfMonth
                        ).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(DATE_LABEL.format(form.dateTime))
                }
                OutlinedButton(
                    onClick = {
                        val time = form.dateTime.toLocalTime()
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> viewModel.setTime(LocalTime.of(hour, minute)) },
                            time.hour,
                            time.minute,
                            false
                        ).show()
                    }
                ) {
                    Text(TIME_LABEL.format(form.dateTime))
                }
            }

            OutlinedTextField(
                value = form.note,
                onValueChange = viewModel::setNote,
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            if (!form.isNew) {
                val origin = when (form.source) {
                    TxnSource.MANUAL -> "Added by hand"
                    TxnSource.SCREENSHOT -> "Read from a shared screenshot"
                    TxnSource.STATEMENT -> "Imported from a statement"
                }
                val reference = form.txnId?.takeIf { form.source == TxnSource.SCREENSHOT }?.let { " · Ref $it" }.orEmpty()
                Text(origin + reference, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (form.isNew) "Add transaction" else "Save changes")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this transaction?") },
            text = { Text("It will be removed from your totals. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
