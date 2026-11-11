package com.paisatrail.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisatrail.data.prefs.AppSettings
import com.paisatrail.data.prefs.SettingsStore
import com.paisatrail.data.prefs.ThemeMode
import com.paisatrail.data.repo.BackupRepository
import com.paisatrail.data.repo.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val transactionCount: Int = 0,
    val learnedCount: Int = 0,
    val loaded: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val backup: BackupRepository,
    private val transactions: TransactionRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        settings.settings,
        transactions.observeCount(),
        transactions.observeOverrideCount()
    ) { appSettings, count, learned ->
        SettingsUiState(appSettings, count, learned, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** One-shot text for a toast; the screen calls [messageShown] after showing it. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    fun messageShown() {
        _message.value = null
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setTheme(mode) }
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch { settings.setReduceMotion(enabled) }
    }

    fun saveProfile(name: String, upiId: String) {
        viewModelScope.launch {
            settings.setProfile(name, upiId)
            _message.value = "Saved."
        }
    }

    fun saveGemini(apiKey: String, model: String) {
        viewModelScope.launch {
            settings.setGemini(apiKey, model)
            _message.value = if (apiKey.isBlank()) "Gemini key removed." else "Gemini key saved on this phone."
        }
    }

    fun forgetLearnedCategories() {
        viewModelScope.launch {
            transactions.clearOverrides()
            _message.value = "Learned categories cleared."
        }
    }

    fun exportBackup(uri: Uri) = runFileJob("Backup saved.") {
        writeText(uri, backup.exportJson())
    }

    fun exportCsv(uri: Uri) = runFileJob("CSV saved.") {
        writeText(uri, backup.exportCsv())
    }

    fun restoreBackup(uri: Uri) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            _message.value = try {
                val summary = backup.importJson(readText(uri))
                "Restored ${summary.transactions} transactions, ${summary.friends} friends and " +
                    "${summary.commitments} commitments."
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                e.message ?: "That file couldn't be restored."
            } catch (e: Exception) {
                "That file couldn't be restored. Nothing was changed."
            }
            _busy.value = false
        }
    }

    private fun runFileJob(successMessage: String, job: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            _message.value = try {
                job()
                successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Couldn't write that file. Pick another location and try again."
            }
            _busy.value = false
        }
    }

    private suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No output stream")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("No input stream")
        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
