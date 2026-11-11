package com.paisatrail.ui.importer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisatrail.data.local.TransactionEntity
import com.paisatrail.data.pdf.PdfTextExtractor
import com.paisatrail.data.pdf.PdfTextResult
import com.paisatrail.data.repo.TransactionRepository
import com.paisatrail.data.share.ShareInbox
import com.paisatrail.domain.categorize.Categorizer
import com.paisatrail.domain.model.Category
import com.paisatrail.domain.model.TxnSource
import com.paisatrail.domain.model.TxnType
import com.paisatrail.domain.parse.StatementParser
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportRow(
    val key: Int,
    val date: LocalDate,
    val merchant: String,
    val description: String,
    val amountPaise: Long,
    val type: TxnType,
    val category: Category,
    val fingerprint: String,
    val included: Boolean = true,
    val categoryEdited: Boolean = false
)

sealed interface ImportState {
    data object Idle : ImportState
    data class Working(val message: String) : ImportState
    data class NeedsPassword(val wrongPassword: Boolean) : ImportState
    data class Review(val rows: List<ImportRow>, val skippedLines: Int) : ImportState
    data class Done(val saved: Int, val duplicates: Int) : ImportState
    data class Error(val message: String) : ImportState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val pdf: PdfTextExtractor,
    private val repository: TransactionRepository,
    inbox: ShareInbox
) : ViewModel() {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state

    private var currentUri: Uri? = null

    init {
        inbox.take(wantPdf = true)?.let { load(it.uri) }
    }

    fun load(uri: Uri, password: String? = null) {
        currentUri = uri
        _state.value = ImportState.Working("Reading the PDF…")
        viewModelScope.launch {
            when (val result = pdf.extract(uri, password)) {
                PdfTextResult.PasswordRequired ->
                    _state.value = ImportState.NeedsPassword(wrongPassword = !password.isNullOrEmpty())

                is PdfTextResult.Failure -> _state.value = ImportState.Error(result.message)

                is PdfTextResult.Success -> {
                    _state.value = ImportState.Working("Finding transactions in ${result.pages} page(s)…")
                    val parsed = withContext(Dispatchers.Default) { StatementParser.parse(result.text) }
                    val span = StatementParser.spanDays(parsed.rows)
                    _state.value = when {
                        parsed.rows.isEmpty() -> ImportState.Error(
                            "No transactions could be read from this PDF. Scanned (image-only) statements " +
                                "have no text to read. You can still add entries by hand with the + button."
                        )

                        span > StatementParser.MAX_SPAN_DAYS -> ImportState.Error(
                            "This statement covers $span days. Only 1-month statements are supported, so " +
                                "download a single-month statement from your bank and try again."
                        )

                        else -> {
                            val overrides = repository.overrides()
                            ImportState.Review(
                                rows = parsed.rows.mapIndexed { index, row ->
                                    ImportRow(
                                        key = index,
                                        date = row.date,
                                        merchant = row.merchant,
                                        description = row.description,
                                        amountPaise = row.amountPaise,
                                        type = row.type,
                                        category = Categorizer.categorize(row.merchant, row.description, overrides),
                                        fingerprint = StatementParser.fingerprint(row)
                                    )
                                },
                                skippedLines = parsed.skippedLines
                            )
                        }
                    }
                }
            }
        }
    }

    fun retryWithPassword(password: String) {
        currentUri?.let { load(it, password) }
    }

    fun reset() {
        currentUri = null
        _state.value = ImportState.Idle
    }

    fun toggle(key: Int) = updateRow(key) { it.copy(included = !it.included) }

    fun edit(key: Int, merchant: String, amountPaise: Long, type: TxnType, category: Category) = updateRow(key) {
        it.copy(
            merchant = merchant.trim().ifEmpty { it.merchant },
            amountPaise = if (amountPaise > 0) amountPaise else it.amountPaise,
            type = type,
            category = category,
            categoryEdited = it.categoryEdited || category != it.category
        )
    }

    private fun updateRow(key: Int, transform: (ImportRow) -> ImportRow) = _state.update { current ->
        if (current is ImportState.Review) {
            current.copy(rows = current.rows.map { if (it.key == key) transform(it) else it })
        } else {
            current
        }
    }

    fun save() {
        val review = _state.value as? ImportState.Review ?: return
        val chosen = review.rows.filter { it.included }
        if (chosen.isEmpty()) return
        _state.value = ImportState.Working("Saving ${chosen.size} transactions…")
        viewModelScope.launch {
            chosen.filter { it.categoryEdited }.forEach { repository.learn(it.merchant, it.category) }
            val zone = ZoneId.systemDefault()
            val entities = chosen.map { row ->
                TransactionEntity(
                    amountPaise = row.amountPaise,
                    type = row.type,
                    merchant = row.merchant,
                    category = row.category,
                    // Statements carry no time of day, so rows are filed at noon.
                    timestamp = row.date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
                    txnId = row.fingerprint,
                    source = TxnSource.STATEMENT,
                    note = row.description
                )
            }
            val saved = repository.addAll(entities)
            _state.value = ImportState.Done(saved = saved, duplicates = entities.size - saved)
        }
    }
}
