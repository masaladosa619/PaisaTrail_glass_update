package com.paisatrail.data.repo

import com.paisatrail.data.local.BudgetSettingsEntity
import com.paisatrail.data.local.CommitmentEntity
import com.paisatrail.data.local.PlannerDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PlannerRepository @Inject constructor(private val dao: PlannerDao) {

    fun observeBudget(): Flow<BudgetSettingsEntity> = dao.observeBudget().map { it ?: BudgetSettingsEntity() }

    fun observeCommitments(): Flow<List<CommitmentEntity>> = dao.observeCommitments()

    suspend fun saveBudget(monthlyBudgetPaise: Long, monthlyIncomePaise: Long) =
        dao.saveBudget(BudgetSettingsEntity(1, maxOf(0L, monthlyBudgetPaise), maxOf(0L, monthlyIncomePaise)))

    suspend fun saveCommitment(commitment: CommitmentEntity): Long = dao.upsertCommitment(commitment)

    suspend fun deleteCommitment(id: Long) = dao.deleteCommitment(id)
}
