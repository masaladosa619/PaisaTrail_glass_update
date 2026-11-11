package com.paisatrail.ui.split

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisatrail.domain.util.Money
import com.paisatrail.domain.util.UpiLink
import com.paisatrail.ui.components.FilterPill
import com.paisatrail.ui.components.MoneyField
import com.paisatrail.ui.components.QrCode
import com.paisatrail.ui.components.ScreenScaffold
import com.paisatrail.ui.components.SectionCard
import com.paisatrail.ui.components.popIn
import kotlin.math.abs

@Composable
fun SettleScreen(
    friendId: Long,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: SplitViewModel = hiltViewModel()
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ScreenScaffold(title = "Settle up", onBack = onClose) { padding ->
        if (!snapshot.loaded) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@ScreenScaffold
        }
        val entry = snapshot.friends.firstOrNull { it.friend.id == friendId }
        if (entry == null) {
            Box(modifier = Modifier.padding(padding)) {
                CenteredAction("That friend is no longer in your list.", "Go back", onClose)
            }
            return@ScreenScaffold
        }

        val friend = entry.friend
        val balance = entry.balancePaise
        var friendPays by remember(balance) { mutableStateOf(balance >= 0) }
        var amount by remember(balance) { mutableStateOf(if (balance == 0L) "" else Money.toInput(abs(balance))) }
        var friendUpiDraft by remember(friend.id) { mutableStateOf("") }
        val amountPaise = Money.parseToPaise(amount) ?: 0L

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                when {
                    balance > 0 -> "${friend.name} owes you ${Money.format(balance)}"
                    balance < 0 -> "You owe ${friend.name} ${Money.format(-balance)}"
                    else -> "You and ${friend.name} are settled up"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("${friend.name} pays me", friendPays, { friendPays = true })
                FilterPill("I pay ${friend.name}", !friendPays, { friendPays = false })
            }

            MoneyField(value = amount, onValueChange = { amount = it }, label = "Amount")

            val payeeVpa = if (friendPays) profile.userUpiId else friend.upiId.orEmpty()
            val payeeName = if (friendPays) profile.userName else friend.name

            if (UpiLink.isValidVpa(payeeVpa) && amountPaise > 0) {
                val link = UpiLink.build(payeeVpa, payeeName, amountPaise, "Settling up")
                QrCode(content = link, modifier = Modifier.size(240.dp).popIn())
                Text(
                    if (friendPays) {
                        "Ask ${friend.name} to scan this with any UPI app. It pays $payeeVpa."
                    } else {
                        "Scan from another phone, or open your UPI app to pay $payeeVpa."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!friendPays) {
                        Button(onClick = { openUpiApp(context, link) }, modifier = Modifier.weight(1f)) {
                            Text("Open UPI app")
                        }
                    }
                    OutlinedButton(onClick = { shareLink(context, link) }, modifier = Modifier.weight(1f)) {
                        Text("Share pay link")
                    }
                }
            } else if (amountPaise > 0 && friendPays) {
                SectionCard {
                    Text("Add your UPI ID to show a QR", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Save your own UPI ID in Settings and a scannable QR appears here. " +
                            "You can still record the payment below without it.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenSettings) { Text("Open Settings") }
                }
            } else if (amountPaise > 0) {
                SectionCard {
                    Text("Add ${friend.name}'s UPI ID to pay by QR", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = friendUpiDraft,
                        onValueChange = { friendUpiDraft = it.trim() },
                        label = { Text("UPI ID (name@bank)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.setFriendUpi(friend, friendUpiDraft) },
                        enabled = UpiLink.isValidVpa(friendUpiDraft)
                    ) { Text("Save UPI ID") }
                }
            }

            Button(
                onClick = {
                    viewModel.settle(friend.id, amountPaise, friendPays)
                    onClose()
                },
                enabled = amountPaise > 0,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    if (friendPays) "Mark ${Money.format(amountPaise)} as received"
                    else "Mark ${Money.format(amountPaise)} as paid"
                )
            }
            Text(
                "PaisaTrail never moves money. Paying happens in your UPI app; this only records it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun openUpiApp(context: Context, link: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No UPI app found on this phone.", Toast.LENGTH_SHORT).show()
    }
}

private fun shareLink(context: Context, link: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
    try {
        context.startActivity(Intent.createChooser(send, "Share payment link"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Nothing on this phone can share that.", Toast.LENGTH_SHORT).show()
    }
}
