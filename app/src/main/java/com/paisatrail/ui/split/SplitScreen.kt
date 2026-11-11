package com.paisatrail.ui.split

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisatrail.data.repo.ExpenseView
import com.paisatrail.data.repo.FriendBalance
import com.paisatrail.data.repo.GroupSummary
import com.paisatrail.data.repo.SettlementView
import com.paisatrail.data.repo.SplitSnapshot
import com.paisatrail.domain.util.Money
import com.paisatrail.domain.util.UpiLink
import com.paisatrail.ui.components.AnimatedAmount
import com.paisatrail.ui.components.ChartSlice
import com.paisatrail.ui.components.DonutChart
import com.paisatrail.ui.components.EmptyState
import com.paisatrail.ui.components.FilterPill
import com.paisatrail.ui.components.PersonPalette
import com.paisatrail.ui.components.SectionLabel
import com.paisatrail.ui.components.bouncyClickable
import com.paisatrail.ui.components.formatDate
import com.paisatrail.ui.components.riseIn
import com.paisatrail.ui.theme.AppColors

private enum class SplitTab(val label: String) { FRIENDS("FRIENDS"), GROUPS("GROUPS"), SETTLED("SETTLED") }

@Composable
fun SplitScreen(
    onAddExpense: (friendId: Long, groupId: Long) -> Unit,
    onSettle: (friendId: Long) -> Unit,
    viewModel: SplitViewModel = hiltViewModel()
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(SplitTab.FRIENDS) }
    var showAddFriend by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var friendMenu by remember { mutableStateOf<Long?>(null) }
    var groupMenu by remember { mutableStateOf<Long?>(null) }
    var settlementToDelete by remember { mutableStateOf<Long?>(null) }
    var expenseToDelete by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item { SplitHeader(snapshot) }
        item {
            FlowRow(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterPill("+ Add friend", false, { showAddFriend = true })
                FilterPill("+ Create group", false, { showCreateGroup = true })
                FilterPill("+ Add expense", true, { onAddExpense(-1L, -1L) })
            }
        }
        item { OwedChart(snapshot) }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SplitTab.entries.forEach { entry ->
                    FilterPill(entry.label, tab == entry, { tab = entry })
                }
            }
        }

        when (tab) {
            SplitTab.FRIENDS -> {
                if (snapshot.friends.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.PersonAdd,
                            title = "No friends yet",
                            body = "Add the people you share bills with. Splits are unlimited and stay on this phone."
                        )
                    }
                }
                items(snapshot.friends, key = { "f" + it.friend.id }) { entry ->
                    Box(modifier = Modifier.animateItem()) {
                        FriendRow(entry, colorFor(snapshot, entry.friend.id), onClick = { friendMenu = entry.friend.id })
                    }
                }
                if (snapshot.expenses.isNotEmpty()) {
                    item { SectionLabel("Shared expenses", modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp)) }
                    items(snapshot.expenses, key = { "e" + it.expense.id }) { entry ->
                        Box(modifier = Modifier.animateItem()) {
                            ExpenseRow(entry, onClick = { expenseToDelete = entry.expense.id })
                        }
                    }
                }
            }

            SplitTab.GROUPS -> {
                if (snapshot.groups.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.Groups,
                            title = "No groups yet",
                            body = "Make a group for a trip, a flat or a team lunch and split inside it."
                        )
                    }
                }
                items(snapshot.groups, key = { "g" + it.group.id }) { entry ->
                    Box(modifier = Modifier.animateItem()) {
                        GroupRow(entry, onClick = { groupMenu = entry.group.id })
                    }
                }
            }

            SplitTab.SETTLED -> {
                if (snapshot.settlements.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.CheckCircle,
                            title = "Nothing settled yet",
                            body = "Payments you record with Settle up are listed here."
                        )
                    }
                }
                items(snapshot.settlements, key = { "s" + it.settlement.id }) { entry ->
                    Box(modifier = Modifier.animateItem()) {
                        SettlementRow(entry, onClick = { settlementToDelete = entry.settlement.id })
                    }
                }
            }
        }
    }

    if (showAddFriend) {
        AddFriendDialog(
            onDismiss = { showAddFriend = false },
            onConfirm = { name, upi ->
                viewModel.addFriend(name, upi)
                showAddFriend = false
            }
        )
    }

    if (showCreateGroup) {
        CreateGroupDialog(
            friends = snapshot.friends,
            onDismiss = { showCreateGroup = false },
            onConfirm = { name, ids ->
                viewModel.createGroup(name, ids)
                showCreateGroup = false
            }
        )
    }

    snapshot.friends.firstOrNull { it.friend.id == friendMenu }?.let { entry ->
        AlertDialog(
            onDismissRequest = { friendMenu = null },
            title = { Text(entry.friend.name) },
            text = {
                Column {
                    Text(balanceLine(entry.balancePaise), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        friendMenu = null
                        onAddExpense(entry.friend.id, -1L)
                    }) { Text("Add a shared expense") }
                    TextButton(onClick = {
                        friendMenu = null
                        onSettle(entry.friend.id)
                    }) { Text("Settle up") }
                    if (entry.balancePaise == 0L) {
                        TextButton(onClick = {
                            friendMenu = null
                            viewModel.deleteFriend(entry.friend.id)
                        }) { Text("Remove friend", color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { friendMenu = null }) { Text("Close") } }
        )
    }

    snapshot.groups.firstOrNull { it.group.id == groupMenu }?.let { entry ->
        AlertDialog(
            onDismissRequest = { groupMenu = null },
            title = { Text(entry.group.name) },
            text = {
                Column {
                    Text(
                        "You, " + entry.memberNames.joinToString(", "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${entry.expenseCount} expense(s) · ${Money.format(entry.totalSpentPaise)} total",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        groupMenu = null
                        onAddExpense(-1L, entry.group.id)
                    }) { Text("Add an expense to this group") }
                    TextButton(onClick = {
                        groupMenu = null
                        viewModel.deleteGroup(entry.group.id)
                    }) { Text("Delete group and its expenses", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { groupMenu = null }) { Text("Close") } }
        )
    }

    settlementToDelete?.let { id ->
        ConfirmDialog(
            title = "Remove this settlement?",
            body = "The balance with this friend goes back to what it was before.",
            confirmLabel = "Remove",
            onDismiss = { settlementToDelete = null },
            onConfirm = {
                viewModel.deleteSettlement(id)
                settlementToDelete = null
            }
        )
    }

    expenseToDelete?.let { id ->
        ConfirmDialog(
            title = "Delete this shared expense?",
            body = "Everyone's share of it is removed from the balances.",
            confirmLabel = "Delete",
            onDismiss = { expenseToDelete = null },
            onConfirm = {
                viewModel.deleteExpense(id)
                expenseToDelete = null
            }
        )
    }
}

private fun balanceLine(balancePaise: Long): String = when {
    balancePaise > 0 -> "Owes you ${Money.format(balancePaise)}"
    balancePaise < 0 -> "You owe ${Money.format(-balancePaise)}"
    else -> "All settled up"
}

private fun colorFor(snapshot: SplitSnapshot, friendId: Long): Color {
    val index = snapshot.friends.indexOfFirst { it.friend.id == friendId }.coerceAtLeast(0)
    return PersonPalette[index % PersonPalette.size]
}

@Composable
private fun SplitHeader(snapshot: SplitSnapshot) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).riseIn()) {
        val owed = snapshot.owedToYouPaise
        val owe = snapshot.youOwePaise
        when {
            owed > 0 -> {
                Text("You're owed", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                AnimatedAmount(paise = owed, fontSize = 40.sp)
                if (owe > 0) {
                    Text("and you owe ${Money.format(owe)}", color = AppColors.Expense, fontSize = 13.sp)
                }
            }

            owe > 0 -> {
                Text("You owe", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                AnimatedAmount(paise = owe, fontSize = 40.sp, color = AppColors.Expense)
            }

            else -> {
                Text("Split & settle", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Text("All settled up", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OwedChart(snapshot: SplitSnapshot) {
    val debtors = snapshot.friends.filter { it.balancePaise > 0 }
    if (debtors.isEmpty()) return
    Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        DonutChart(
            slices = debtors.map { ChartSlice(it.balancePaise.toFloat(), colorFor(snapshot, it.friend.id)) },
            emptyColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
            modifier = Modifier.size(130.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            debtors.take(6).forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(colorFor(snapshot, entry.friend.id)))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(entry.friend.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                    Text(Money.format(entry.balancePaise), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Avatar(label: String, color: Color) {
    Box(
        modifier = Modifier.size(42.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(label.trim().take(1).uppercase().ifEmpty { "?" }, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ListRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    trailing: String,
    trailingColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().bouncyClickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(trailing, fontWeight = FontWeight.Bold, color = trailingColor)
    }
}

@Composable
private fun FriendRow(entry: FriendBalance, color: Color, onClick: () -> Unit) {
    val balance = entry.balancePaise
    ListRow(
        leading = { Avatar(entry.friend.name, color) },
        title = entry.friend.name,
        subtitle = when {
            balance > 0 -> "owes you"
            balance < 0 -> "you owe"
            else -> "settled up"
        },
        trailing = if (balance == 0L) "" else Money.format(kotlin.math.abs(balance)),
        trailingColor = if (balance < 0) AppColors.Expense else MaterialTheme.colorScheme.onSurface,
        onClick = onClick
    )
}

@Composable
private fun GroupRow(entry: GroupSummary, onClick: () -> Unit) {
    val balance = entry.balancePaise
    ListRow(
        leading = { Avatar(entry.group.name, MaterialTheme.colorScheme.tertiary) },
        title = entry.group.name,
        subtitle = when {
            balance > 0 -> "you're owed · ${entry.memberNames.size + 1} people"
            balance < 0 -> "you owe · ${entry.memberNames.size + 1} people"
            else -> "settled · ${entry.memberNames.size + 1} people"
        },
        trailing = if (balance == 0L) "" else Money.format(kotlin.math.abs(balance)),
        trailingColor = if (balance < 0) AppColors.Expense else MaterialTheme.colorScheme.onSurface,
        onClick = onClick
    )
}

@Composable
private fun SettlementRow(entry: SettlementView, onClick: () -> Unit) {
    ListRow(
        leading = { Avatar("✓", MaterialTheme.colorScheme.primary) },
        title = entry.title,
        subtitle = formatDate(entry.settlement.timestamp),
        trailing = Money.format(entry.settlement.amountPaise),
        trailingColor = MaterialTheme.colorScheme.onSurface,
        onClick = onClick
    )
}

@Composable
private fun ExpenseRow(entry: ExpenseView, onClick: () -> Unit) {
    ListRow(
        leading = { Avatar(entry.expense.description, MaterialTheme.colorScheme.outline) },
        title = entry.expense.description.ifBlank { "Shared expense" },
        subtitle = "${entry.paidByName} paid · " + (entry.groupName?.let { "$it · " } ?: "") + formatDate(entry.expense.timestamp),
        trailing = Money.format(entry.expense.amountPaise),
        trailingColor = MaterialTheme.colorScheme.onSurface,
        onClick = onClick
    )
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddFriendDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var upi by remember { mutableStateOf("") }
    val upiInvalid = upi.isNotBlank() && !UpiLink.isValidVpa(upi)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a friend") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = upi,
                    onValueChange = { upi = it.trim() },
                    label = { Text("UPI ID (optional)") },
                    supportingText = { Text(if (upiInvalid) "That doesn't look like a UPI ID (name@bank)." else "Lets you pay them back from a QR or UPI app.") },
                    isError = upiInvalid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, upi) }, enabled = name.isNotBlank() && !upiInvalid) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CreateGroupDialog(friends: List<FriendBalance>, onDismiss: () -> Unit, onConfirm: (String, List<Long>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a group") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (friends.isEmpty()) {
                    Text("Add at least one friend first, then put them in a group.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SectionLabel("Members (you're always included)")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        friends.forEach { entry ->
                            val id = entry.friend.id
                            FilterPill(entry.friend.name, id in selected, {
                                selected = if (id in selected) selected - id else selected + id
                            })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, selected.toList()) },
                enabled = name.isNotBlank() && selected.isNotEmpty()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Small helper so other split screens can show a centred call to action. */
@Composable
internal fun CenteredAction(message: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}
