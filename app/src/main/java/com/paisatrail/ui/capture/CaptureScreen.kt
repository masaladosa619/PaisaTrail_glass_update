package com.paisatrail.ui.capture

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisatrail.domain.model.TxnType
import com.paisatrail.domain.util.Money
import com.paisatrail.ui.components.EmptyState
import com.paisatrail.ui.components.KeyValueRow
import com.paisatrail.ui.components.LocalReduceMotion
import com.paisatrail.ui.components.MoneyField
import com.paisatrail.ui.components.ScreenScaffold
import com.paisatrail.ui.components.SectionCard
import com.paisatrail.ui.components.SectionLabel
import com.paisatrail.ui.components.TypeToggle
import com.paisatrail.ui.components.formatDateTime
import com.paisatrail.ui.components.popIn
import com.paisatrail.ui.components.riseIn
import com.paisatrail.ui.components.shimmer
import com.paisatrail.ui.theme.AppColors
import kotlinx.coroutines.delay

@Composable
fun CaptureScreen(
    onClose: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.process(uri)
    }

    ScreenScaffold(title = "Share to track", onBack = onClose) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val current = state) {
                CaptureState.Idle -> {
                    EmptyState(
                        icon = Icons.Filled.Image,
                        title = "Log a payment from its screenshot",
                        body = "In any UPI or bank app, tap Share on the payment-success screen and choose PaisaTrail. " +
                            "Or pick a screenshot you already have."
                    )
                    Button(onClick = { pickImage.launch("image/*") }) { Text("Choose a screenshot") }
                }

                CaptureState.Reading -> {
                    Spacer(modifier = Modifier.height(80.dp))
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Reading the screenshot on your phone…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.shimmer()
                    )
                }

                is CaptureState.Logged -> {
                    StatusBadge(icon = { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(34.dp)) })
                    Text("Logged.", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.riseIn(2))
                    Text(
                        "Just like that.",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.riseIn(4)
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    SectionCard(enterIndex = 6) {
                        val txn = current.txn
                        Text(txn.merchant, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            (if (txn.type == TxnType.EXPENSE) "-" else "+") + Money.format(txn.amountPaise),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (txn.type == TxnType.EXPENSE) AppColors.Expense else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        KeyValueRow("Category", txn.category.label)
                        KeyValueRow("When", formatDateTime(txn.timestamp))
                        txn.txnId?.let { KeyValueRow("Reference", it) }
                        current.paidFrom?.let { KeyValueRow("Paid from", it) }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { onEdit(current.txn.id) }, modifier = Modifier.weight(1f)) {
                            Text("Fix details")
                        }
                        OutlinedButton(onClick = viewModel::undo, modifier = Modifier.weight(1f)) {
                            Text("Undo")
                        }
                    }
                }

                is CaptureState.Duplicate -> {
                    StatusBadge(
                        color = AppColors.Warning,
                        icon = { Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(30.dp)) }
                    )
                    Text("Already logged", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "A transaction with reference ${current.txnId} is already in your history, so nothing was added.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
                }

                is CaptureState.NeedsFix -> FixForm(current, onSave = viewModel::saveFixed)

                CaptureState.Undone -> {
                    StatusBadge(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                        icon = { Icon(Icons.Filled.Undo, contentDescription = null, modifier = Modifier.size(30.dp)) }
                    )
                    Text("Removed", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("That transaction was taken back out.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    icon: @Composable () -> Unit
) {
    // The badge pops in with an overshoot while a ring of its colour expands outwards once.
    val reduceMotion = LocalReduceMotion.current
    val ring = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            delay(160)
            ring.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
    Box(
        modifier = Modifier
            .size(72.dp)
            .drawBehind {
                val p = ring.value
                if (p > 0f && p < 1f) {
                    drawCircle(
                        color = color.copy(alpha = (1f - p) * 0.45f),
                        radius = size.minDimension / 2f * (1f + p * 0.9f)
                    )
                }
            }
            .popIn()
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun FixForm(state: CaptureState.NeedsFix, onSave: (String, String, TxnType) -> Boolean) {
    val parsed = state.parsed
    var amount by remember(parsed) { mutableStateOf(parsed.amountPaise?.let { Money.toInput(it) }.orEmpty()) }
    var merchant by remember(parsed) { mutableStateOf(parsed.merchant.orEmpty()) }
    var type by remember(parsed) { mutableStateOf(parsed.type) }
    var amountError by remember(parsed) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Needs a quick check", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(state.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)

        MoneyField(
            value = amount,
            onValueChange = {
                amount = it
                amountError = false
            },
            label = "Amount",
            isError = amountError
        )
        if (amountError) {
            Text("Enter an amount greater than zero.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            label = { Text("Paid to / received from") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        TypeToggle(selected = type, onSelect = { type = it })

        parsed.timestamp?.let { KeyValueRow("When", formatDateTime(it)) }
        parsed.txnId?.let { KeyValueRow("Reference", it) }

        Button(
            onClick = { amountError = !onSave(amount, merchant, type) },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Log transaction")
        }

        if (parsed.rawText.isNotBlank()) {
            SectionCard {
                SectionLabel("Text found in the image")
                Spacer(modifier = Modifier.height(6.dp))
                Text(parsed.rawText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
