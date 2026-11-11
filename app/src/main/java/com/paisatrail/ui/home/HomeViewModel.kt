package com.paisatrail.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisatrail.data.ai.GeminiClient
import com.paisatrail.data.local.TransactionEntity
import com.paisatrail.data.prefs.SettingsStore
import com.paisatrail.data.repo.PlannerRepository
import com.paisatrail.data.repo.TransactionRepository
import com.paisatrail.domain.insights.Insights
import com.paisatrail.domain.insights.InsightsCalculator
import com.paisatrail.domain.model.Category
import com.paisatrail.domain.model.TxnType
import com.paisatrail.domain.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryTotal(val category: Category, val amountPaise: Long)

data class HomeUiState(
    val month: YearMonth = YearMonth.now(),
    val isCurrentMonth: Boolean = true,
    val transactions: List<TransactionEntity> = emptyList(),
    val expenseTotalPaise: Long = 0,
    val incomeLoggedPaise: Long = 0,
    val previousExpensePaise: Long = 0,
    val byCategory: List<CategoryTotal> = emptyList(),
    val budgetPaise: Long = 0,
    val incomePaise: Long = 0,
    val insights: Insights? = null,
    val loading: Boolean = true
)

sealed interface AiTip {
    data object Idle : AiTip
    data object Loading : AiTip
    data class Ready(val text: String) : AiTip
    data object Failed : AiTip
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    planner: PlannerRepository,
    private val settings: SettingsStore,
    private val gemini: GeminiClient
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())

    val state: StateFlow<HomeUiState> = month.flatMapLatest { selected ->
        combine(
            transactions.observeMonth(selected),
            transactions.observeExpenseTotal(selected.minusMonths(1)),
            planner.observeBudget()
        ) { txns, previousTotal, budget ->
            val expenses = txns.filter { it.type == TxnType.EXPENSE }
            val total = expenses.sumOf { it.amountPaise }
            HomeUiState(
                month = selected,
                isCurrentMonth = selected == YearMonth.now(),
                transactions = txns,
                expenseTotalPaise = total,
                incomeLoggedPaise = txns.filter { it.type == TxnType.INCOME }.sumOf { it.amountPaise },
                previousExpensePaise = previousTotal,
                byCategory = expenses.groupBy { it.category }
                    .map { (category, rows) -> CategoryTotal(category, rows.sumOf { it.amountPaise }) }
                    .sortedByDescending { it.amountPaise },
                budgetPaise = budget.monthlyBudgetPaise,
                incomePaise = budget.monthlyIncomePaise,
                insights = InsightsCalculator.compute(
                    spentPaise = total,
                    month = selected,
                    today = LocalDate.now(),
                    incomePaise = budget.monthlyIncomePaise,
                    budgetPaise = budget.monthlyBudgetPaise
                ),
                loading = false
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val hasAiKey: StateFlow<Boolean> = settings.settings
        .map { it.geminiApiKey.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _aiTip = MutableStateFlow<AiTip>(AiTip.Idle)
    val aiTip: StateFlow<AiTip> = _aiTip

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
        _aiTip.value = AiTip.Idle
    }

    fun nextMonth() {
        if (month.value.isBefore(YearMonth.now())) {
            month.value = month.value.plusMonths(1)
            _aiTip.value = AiTip.Idle
        }
    }

    /**
     * Optional: asks Gemini for a short tip. Only monthly totals per category are sent,
     * never individual transactions, merchants or reference ids.
     */
    fun askAi() {
        val snapshot = state.value
        if (_aiTip.value == AiTip.Loading) return
        _aiTip.value = AiTip.Loading
        viewModelScope.launch {
            val current = settings.current()
            val categories = snapshot.byCategory.joinToString(", ") {
                "${it.category.label} ${Money.format(it.amountPaise, false)}"
            }
            val prompt = buildString {
                append("You are a concise personal-finance coach for someone in India. ")
                append("Month: ${snapshot.month}. Spent so far: ${Money.format(snapshot.expenseTotalPaise, false)}. ")
                snapshot.insights?.let { append("Projected month-end spend: ${Money.format(it.projectedPaise, false)}. ") }
                if (snapshot.incomePaise > 0) append("Monthly income: ${Money.format(snapshot.incomePaise, false)}. ")
                if (snapshot.budgetPaise > 0) append("Monthly budget: ${Money.format(snapshot.budgetPaise, false)}. ")
                append("Spend by category: ${categories.ifEmpty { "none yet" }}. ")
                append("Give two short, specific, practical tips in under 60 words total. Plain text, no markdown.")
            }
            val answer = gemini.generate(current.geminiApiKey, current.geminiModel, prompt)
            _aiTip.value = if (answer == null) AiTip.Failed else AiTip.Ready(answer)
        }
    }
}
