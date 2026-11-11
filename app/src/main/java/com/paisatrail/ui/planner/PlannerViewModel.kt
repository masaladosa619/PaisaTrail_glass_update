package com.paisatrail.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisatrail.data.local.BudgetSettingsEntity
import com.paisatrail.data.local.CommitmentEntity
import com.paisatrail.data.prefs.SettingsStore
import com.paisatrail.data.repo.PlannerRepository
import com.paisatrail.data.repo.TransactionRepository
import com.paisatrail.domain.model.CommitmentType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlannerUiState(
    val budget: BudgetSettingsEntity = BudgetSettingsEntity(),
    val spentThisMonthPaise: Long = 0,
    val commitments: List<CommitmentEntity> = emptyList(),
    val remindersEnabled: Boolean = false
) {
    fun ofType(type: CommitmentType): List<CommitmentEntity> = commitments.filter { it.type == type }

    /** Fixed monthly outgo: subscriptions plus EMIs (card balances vary, so they are left out). */
    val monthlyCommittedPaise: Long
        get() = commitments.filter { it.type != CommitmentType.CREDIT_CARD }.sumOf { it.amountPaise }
}

@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val planner: PlannerRepository,
    transactions: TransactionRepository,
    private val settings: SettingsStore
) : ViewModel() {

    val state: StateFlow<PlannerUiState> = combine(
        planner.observeBudget(),
        transactions.observeExpenseTotal(YearMonth.now()),
        planner.observeCommitments(),
        settings.settings
    ) { budget, spent, commitments, appSettings ->
        PlannerUiState(budget, spent, commitments, appSettings.remindersEnabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlannerUiState())

    fun saveBudget(monthlyBudgetPaise: Long, monthlyIncomePaise: Long) {
        viewModelScope.launch { planner.saveBudget(monthlyBudgetPaise, monthlyIncomePaise) }
    }

    fun saveCommitment(commitment: CommitmentEntity) {
        viewModelScope.launch { planner.saveCommitment(commitment) }
    }

    fun deleteCommitment(id: Long) {
        viewModelScope.launch { planner.deleteCommitment(id) }
    }

    fun setReminders(enabled: Boolean) {
        viewModelScope.launch { settings.setReminders(enabled) }
    }
}
