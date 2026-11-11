package com.paisatrail.ui.capture

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisatrail.data.local.TransactionEntity
import com.paisatrail.data.ocr.OcrEngine
import com.paisatrail.data.repo.TransactionRepository
import com.paisatrail.data.share.ShareInbox
import com.paisatrail.domain.model.ParsedPayment
import com.paisatrail.domain.model.TxnSource
import com.paisatrail.domain.model.TxnType
import com.paisatrail.domain.parse.UpiScreenshotParser
import com.paisatrail.domain.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface CaptureState {
    /** Nothing shared yet: offer to pick an image. */
    data object Idle : CaptureState
    data object Reading : CaptureState
    data class Logged(val txn: TransactionEntity, val paidFrom: String?) : CaptureState
    data class Duplicate(val txnId: String) : CaptureState
    /** The parser couldn't find enough; the user completes the entry by hand. */
    data class NeedsFix(val reason: String, val parsed: ParsedPayment) : CaptureState
    data object Undone : CaptureState
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val ocr: OcrEngine,
    private val repository: TransactionRepository,
    inbox: ShareInbox
) : ViewModel() {

    private val _state = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = _state

    init {
        inbox.take(wantPdf = false)?.let { process(it.uri) }
    }

    fun process(uri: Uri) {
        _state.value = CaptureState.Reading
        viewModelScope.launch {
            val lines = try {
                ocr.recognize(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = CaptureState.NeedsFix("Couldn't read that image. You can fill it in yourself.", EMPTY)
                return@launch
            }
            val parsed = UpiScreenshotParser.parse(lines)
            val amount = parsed.amountPaise
            if (amount == null || amount <= 0) {
                val reason = if (lines.isEmpty()) {
                    "No text was found in that image."
                } else {
                    "Couldn't find the amount. Check the details below."
                }
                _state.value = CaptureState.NeedsFix(reason, parsed)
                return@launch
            }
            log(parsed, amount, parsed.merchant?.takeIf { it.isNotBlank() } ?: "Unknown", parsed.type)
        }
    }

    /** Called from the manual-fix form. Returns false (and stays on the form) for a bad amount. */
    fun saveFixed(amountText: String, merchant: String, type: TxnType): Boolean {
        val needsFix = _state.value as? CaptureState.NeedsFix ?: return false
        val amount = Money.parseToPaise(amountText)
        if (amount == null || amount <= 0) return false
        viewModelScope.launch {
            log(needsFix.parsed, amount, merchant.trim().ifEmpty { "Unknown" }, type)
        }
        return true
    }

    fun undo() {
        val logged = _state.value as? CaptureState.Logged ?: return
        viewModelScope.launch {
            repository.delete(logged.txn.id)
            _state.value = CaptureState.Undone
        }
    }

    private suspend fun log(parsed: ParsedPayment, amountPaise: Long, merchant: String, type: TxnType) {
        val entity = TransactionEntity(
            amountPaise = amountPaise,
            type = type,
            merchant = merchant,
            category = repository.categorize(merchant),
            timestamp = parsed.timestamp ?: System.currentTimeMillis(),
            txnId = parsed.txnId,
            source = TxnSource.SCREENSHOT,
            note = parsed.paidFrom?.let { "Paid from $it" }.orEmpty()
        )
        val id = repository.add(entity)
        _state.value = if (id == -1L) {
            CaptureState.Duplicate(parsed.txnId.orEmpty())
        } else {
            CaptureState.Logged(entity.copy(id = id), parsed.paidFrom)
        }
    }

    private companion object {
        val EMPTY = ParsedPayment(null, null, null, null, null, TxnType.EXPENSE, "")
    }
}
