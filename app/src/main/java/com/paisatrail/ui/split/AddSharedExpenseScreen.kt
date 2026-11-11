package com.paisatrail.ui.split

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisatrail.domain.split.SELF_ID
import com.paisatrail.domain.split.ShareInput
import com.paisatrail.domain.split.SplitCalculator
import com.paisatrail.domain.util.Money
import com.paisatrail.ui.components.FilterPill
import com.paisatrail.ui.components.MoneyField
import com.paisatrail.ui.components.ScreenScaffold
import com.paisatrail.ui.components.SectionLabel
import com.paisatrail.ui.theme.AppColors

/**
 * @param friendId preselects one friend (or -1).
 * @param groupId files the expense under a group and preselects its members (or -1).
 */
@Composable
fun AddSharedExpenseScreen(
    friendId: Long,
    groupId: Long,
    onClose: () -> Unit,
    viewModel: SplitViewModel = hiltViewModel()
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()

    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var paidBy by remember { mutableStateOf(SELF_ID) }
    var customSplit by remember { mutableStateOf(false) }
    var initialised by remember { mutableStateOf(false) }
    val customShares = remember { mutableStateMapOf<Long, String>() }

    val group = snapshot.groups.firstOrNull { it.group.id == groupId }

    LaunchedEffect(snapshot.loaded) {
        if (snapshot.loaded && !initialised) {
            selected = when {
                group != null -> group.memberIds.toSet()
                friendId > 0 -> setOf(friendId)
                else -> emptySet()
            }
            initialised = true
        }
    }

    ScreenScaffold(title = if (group != null) "Split in ${group.group.name}" else "Split an expense", onBack = onClose) { padding ->
        if (!snapshot.loaded) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@ScreenScaffold
        }
        if (snapshot.friends.isEmpty()) {
            Box(modifier = Modifier.padding(padding)) {
                CenteredAction(
                    message = "Add a friend on the Split tab first, then split a bill with them.",
                    actionLabel = "Go back",
                    onAction = onClose
                )
            }
            return@ScreenScaffold
        }

        // Group expenses can only involve that group's members.
        val candidates = if (group != null) {
            snapshot.friends.filter { it.friend.id in group.memberIds }
        } else {
            snapshot.friends
        }
        val names = snapshot.friends.associate { it.friend.id to it.friend.name }
        val participants = listOf(SELF_ID) + candidates.map { it.friend.id }.filter { it in selected }
        val payer = if (paidBy in participants) paidBy else SELF_ID
        val totalPaise = Money.parseToPaise(amount) ?: 0L
        val shares: List<ShareInput> = if (customSplit) {
            participants.map { ShareInput(it, Money.parseToPaise(customShares[it].orEmpty()) ?: 0L) }
        } else {
            SplitCalculator.equalSplit(totalPaise, participants)
        }
        val assigned = shares.sumOf { it.sharePaise }
        val splitValid = SplitCalculator.isValidCustomSplit(totalPaise, shares)
        val canSave = description.isNotBlank() && totalPaise > 0 && participants.size >= 2 && splitValid

        fun nameOf(id: Long): String = if (id == SELF_ID) "You" else names[id] ?: "Friend"

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("What was it for?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            MoneyField(value = amount, onValueChange = { amount = it }, label = "Total amount")

            SectionLabel("Split with")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("You", true, {})
                candidates.forEach { entry ->
                    val id = entry.friend.id
                    FilterPill(entry.friend.name, id in selected, {
                        selected = if (id in selected) selected - id else selected + id
                    })
                }
            }

            SectionLabel("Paid by")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                participants.forEach { id ->
                    FilterPill(nameOf(id), payer == id, { paidBy = id })
                }
            }

            SectionLabel("How to split")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("Equally", !customSplit, { customSplit = false })
                FilterPill("Custom amounts", customSplit, { customSplit = true })
            }

            if (customSplit) {
                participants.forEach { id ->
                    MoneyField(
                        value = customShares[id].orEmpty(),
                        onValueChange = { customShares[id] = it },
                        label = "${nameOf(id)}'s share"
                    )
                }
                val remaining = totalPaise - assigned
                Text(
                    when {
                        totalPaise <= 0 -> "Enter the total amount first."
                        remaining == 0L -> "Shares add up to the total."
                        remaining > 0 -> "${Money.format(remaining)} still to assign."
                        else -> "${Money.format(-remaining)} more than the total."
                    },
                    color = if (remaining == 0L && totalPaise > 0) MaterialTheme.colorScheme.primary else AppColors.Warning,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else if (totalPaise > 0 && participants.size >= 2) {
                Column {
                    shares.forEach { share ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(nameOf(share.participantId), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(Money.format(share.sharePaise, showPaise = true), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (participants.size < 2) {
                Text("Pick at least one friend to split with.", color = AppColors.Warning, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    viewModel.addExpense(description, totalPaise, payer, shares, group?.group?.id)
                    onClose()
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Save split")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
