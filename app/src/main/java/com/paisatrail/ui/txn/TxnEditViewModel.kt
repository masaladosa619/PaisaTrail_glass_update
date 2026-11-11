package com.paisatrail.ui.txn

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisatrail.data.local.TransactionEntity
import com.paisatrail.data.repo.TransactionRepository
import com.paisatrail.domain.categorize.Categorizer
import com.paisatrail.domain.model.Category
import com.paisatrail.domain.model.TxnSource
import com.paisatrail.domain.model.TxnType
import com.paisatrail.domain.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TxnForm(
    val isNew: Boolean = true,
    val loaded: Boolean = false,
    val amount: String = "",
    val type: TxnType = TxnType.EXPENSE,
    val merchant: String = "",
    val category: Category = Category.OTHERS,
    val categoryTouched: Boolean = false,
    val dateTime: LocalDateTime = LocalDateTime.now().withSecond(0).withNano(0),
    val note: String = "",
    val txnId: String? = null,
    val source: TxnSource = TxnSource.MANUAL,
    val error: String? = null,
    val finished: Boolean = false
)

@HiltViewModel
class TxnEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TransactionRepository
) : ViewModel() {

    private val id: Long = savedStateHandle.get<Long>("id") ?: -1L
    private var original: TransactionEntity? = null
    private var overrides: Map<String, Category> = emptyMap()

    private val _form = MutableStateFlow(TxnForm(isNew = id <= 0))
    val form: StateFlow<TxnForm> = _form

    init {
        viewModelScope.launch {
            overrides = repository.overrides()
            val existing = if (id > 0) repository.get(id) else null
            if (existing == null) {
                _form.update { it.copy(isNew = true, loaded = true) }
            } else {
                original = existing
                _form.value = TxnForm(
                    isNew = false,
                    loaded = true,
                    amount = Money.toInput(existing.amountPaise),
                    type = existing.type,
                    merchant = existing.merchant,
                    category = existing.category,
                    dateTime = Instant.ofEpochMilli(existing.timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime(),
                    note = existing.note,
                    txnId = existing.txnId,
                    source = existing.source
                )
            }
        }
    }

    fun setAmount(value: String) = _form.update { it.copy(amount = value, error = null) }

    fun setType(type: TxnType) = _form.update { it.copy(type = type) }

    fun setMerchant(value: String) = _form.update { form ->
        // While adding a new entry, suggest a category as the name is typed (until the user picks one).
        val suggested = if (form.isNew && !form.categoryTouched) {
            Categorizer.categorize(value, overrides = overrides)
        } else {
            form.category
        }
        form.copy(merchant = value, category = suggested)
    }

    fun setCategory(category: Category) = _form.update { it.copy(category = category, categoryTouched = true) }

    fun setDate(date: LocalDate) = _form.update { it.copy(dateTime = LocalDateTime.of(date, it.dateTime.toLocalTime())) }

    fun setTime(time: LocalTime) = _form.update { it.copy(dateTime = LocalDateTime.of(it.dateTime.toLocalDate(), time)) }

    fun setNote(value: String) = _form.update { it.copy(note = value) }

    fun save() {
        val form = _form.value
        val paise = Money.parseToPaise(form.amount)
        if (paise == null || paise <= 0) {
            _form.update { it.copy(error = "Enter an amount greater than zero.") }
            return
        }
        val merchant = form.merchant.trim().ifEmpty { if (form.type == TxnType.INCOME) "Income" else "Unnamed" }
        val timestamp = form.dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        viewModelScope.launch {
            val existing = original
            if (existing == null) {
                repository.add(
                    TransactionEntity(
                        amountPaise = paise,
                        type = form.type,
                        merchant = merchant,
                        category = form.category,
                        timestamp = timestamp,
                        source = TxnSource.MANUAL,
                        note = form.note.trim()
                    )
                )
            } else {
                repository.update(
                    existing.copy(
                        amountPaise = paise,
                        type = form.type,
                        merchant = merchant,
                        category = form.category,
                        timestamp = timestamp,
                        note = form.note.trim()
                    )
                )
            }
            // A category the user chose by hand becomes the rule for this merchant from now on.
            if (form.categoryTouched) repository.learn(merchant, form.category)
            _form.update { it.copy(finished = true) }
        }
    }

    fun delete() {
        val existing = original ?: return
        viewModelScope.launch {
            repository.delete(existing.id)
            _form.update { it.copy(finished = true) }
        }
    }
}
