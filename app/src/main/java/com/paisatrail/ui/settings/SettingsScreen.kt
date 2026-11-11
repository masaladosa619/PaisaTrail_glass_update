package com.paisatrail.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisatrail.data.prefs.AppSettings
import com.paisatrail.data.prefs.ThemeMode
import com.paisatrail.domain.util.UpiLink
import com.paisatrail.ui.components.FilterPill
import com.paisatrail.ui.components.SectionCard
import com.paisatrail.ui.components.SectionLabel
import com.paisatrail.ui.components.riseIn
import java.time.LocalDate

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var confirmForget by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        viewModel.messageShown()
    }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.exportCsv(uri)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        Text("Settings", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.riseIn())

        // --- Appearance ---
        SectionLabel("Appearance", modifier = Modifier.padding(top = 6.dp))
        SectionCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("Dark", state.settings.theme == ThemeMode.DARK, { viewModel.setTheme(ThemeMode.DARK) })
                FilterPill("Light", state.settings.theme == ThemeMode.LIGHT, { viewModel.setTheme(ThemeMode.LIGHT) })
                FilterPill("Match phone", state.settings.theme == ThemeMode.SYSTEM, { viewModel.setTheme(ThemeMode.SYSTEM) })
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Reduce motion", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Stills the drifting backdrop and turns off cascades, bounces and count-ups. " +
                            "Easier on the eyes and on the battery.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(checked = state.settings.reduceMotion, onCheckedChange = viewModel::setReduceMotion)
            }
        }

        // --- UPI details for settle-up QR codes ---
        SectionLabel("Your UPI details", modifier = Modifier.padding(top = 6.dp))
        ProfileCard(settings = state.settings, loaded = state.loaded, onSave = viewModel::saveProfile)

        // --- Optional Gemini key ---
        SectionLabel("Smarter categories (optional)", modifier = Modifier.padding(top = 6.dp))
        GeminiCard(settings = state.settings, loaded = state.loaded, onSave = viewModel::saveGemini)

        // --- Learned categories ---
        SectionLabel("Learned categories", modifier = Modifier.padding(top = 6.dp))
        SectionCard {
            Text(
                if (state.learnedCount == 0) {
                    "When you change a transaction's category, PaisaTrail remembers it for that merchant. " +
                        "Nothing learned yet."
                } else {
                    "${state.learnedCount} merchant(s) always go to the category you picked."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            if (state.learnedCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { confirmForget = true }) { Text("Forget what was learned") }
            }
        }

        // --- Data ownership ---
        SectionLabel("Your data", modifier = Modifier.padding(top = 6.dp))
        SectionCard {
            Text(
                "${state.transactionCount} transaction(s) stored on this phone, and only on this phone.",
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "A backup is one readable JSON file with everything: transactions, splits, planner and " +
                    "learned categories. Your Gemini key is never included.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { saveBackup.launch("paisatrail-backup-${LocalDate.now()}.json") },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Export full backup (JSON)") }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = { saveCsv.launch("paisatrail-transactions-${LocalDate.now()}.csv") },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Export transactions (CSV)") }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = { openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Restore from a backup") }
        }

        // --- About ---
        SectionLabel("About", modifier = Modifier.padding(top = 6.dp))
        SectionCard {
            Text("PaisaTrail ${appVersion(context)}", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Free and open source under the MIT licence. No account, no ads, no analytics, no SMS " +
                    "access and no bank login. The app goes online only if you save a Gemini key above.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Replace everything with this backup?") },
            text = {
                Text(
                    "All transactions, splits and planner entries on this phone will be replaced by the " +
                        "contents of the backup. Export a backup first if you want to keep what's here."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    restoreUri = null
                    viewModel.restoreBackup(uri)
                }) { Text("Replace", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancel") } }
        )
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget learned categories?") },
            text = { Text("Existing transactions keep their category. New ones go back to the built-in rules.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    viewModel.forgetLearnedCategories()
                }) { Text("Forget", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProfileCard(settings: AppSettings, loaded: Boolean, onSave: (String, String) -> Unit) {
    // Keyed on the stored values so the fields fill in once DataStore has loaded.
    var name by remember(loaded, settings.userName) { mutableStateOf(settings.userName) }
    var upi by remember(loaded, settings.userUpiId) { mutableStateOf(settings.userUpiId) }
    val upiInvalid = upi.isNotBlank() && !UpiLink.isValidVpa(upi)
    val changed = name.trim() != settings.userName || upi.trim() != settings.userUpiId

    SectionCard {
        Text(
            "Used only to draw the QR a friend scans to pay you back. It never leaves this phone.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Your name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = upi,
            onValueChange = { upi = it.trim() },
            label = { Text("Your UPI ID (name@bank)") },
            isError = upiInvalid,
            supportingText = {
                Text(if (upiInvalid) "That doesn't look like a UPI ID." else "Example: yourname@okhdfcbank")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { onSave(name, upi) }, enabled = changed && !upiInvalid) { Text("Save") }
    }
}

@Composable
private fun GeminiCard(settings: AppSettings, loaded: Boolean, onSave: (String, String) -> Unit) {
    var key by remember(loaded, settings.geminiApiKey) { mutableStateOf(settings.geminiApiKey) }
    var model by remember(loaded, settings.geminiModel) { mutableStateOf(settings.geminiModel) }
    val hasSavedKey = settings.geminiApiKey.isNotBlank()
    val changed = key.trim() != settings.geminiApiKey || model.trim() != settings.geminiModel

    SectionCard {
        Text(
            "PaisaTrail works fully without this. If you paste your own Gemini API key, a shared payment " +
                "the built-in rules can't place has its merchant name sent to Gemini for a category, and " +
                "the Insights card can ask for a tip using monthly totals only. The key is stored on this " +
                "phone and is left out of backups.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it.trim() },
            label = { Text("Gemini API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = model,
            onValueChange = { model = it.trim() },
            label = { Text("Model name") },
            supportingText = { Text("Default: ${AppSettings.DEFAULT_GEMINI_MODEL}") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onSave(key, model) }, enabled = changed) { Text("Save") }
            if (hasSavedKey) {
                OutlinedButton(onClick = { onSave("", model) }) { Text("Remove key") }
            }
        }
    }
}

private fun appVersion(context: android.content.Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
} catch (e: Exception) {
    ""
}
