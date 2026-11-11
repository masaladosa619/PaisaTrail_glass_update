package com.paisatrail.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    /** Returns the new row id, or -1 when a transaction with the same txnId already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(txn: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(txns: List<TransactionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAll(txns: List<TransactionEntity>)

    @Update
    suspend fun update(txn: TransactionEntity): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC, id DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM transactions WHERE type = 'EXPENSE' AND timestamp >= :from AND timestamp < :to")
    fun observeExpenseTotal(from: Long, to: Long): Flow<Long>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface CategoryOverrideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: CategoryOverrideEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAll(overrides: List<CategoryOverrideEntity>)

    @Query("SELECT * FROM category_overrides")
    suspend fun getAll(): List<CategoryOverrideEntity>

    @Query("SELECT COUNT(*) FROM category_overrides")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM category_overrides")
    suspend fun deleteAll()
}

@Dao
abstract class SplitDao {
    @Query("SELECT * FROM friends ORDER BY name COLLATE NOCASE")
    abstract fun observeFriends(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM split_groups ORDER BY createdAt DESC")
    abstract fun observeGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM group_members")
    abstract fun observeMembers(): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM shared_expenses ORDER BY timestamp DESC, id DESC")
    abstract fun observeExpenses(): Flow<List<SharedExpenseEntity>>

    @Query("SELECT * FROM splits")
    abstract fun observeSplits(): Flow<List<SplitEntity>>

    @Query("SELECT * FROM settlements ORDER BY timestamp DESC, id DESC")
    abstract fun observeSettlements(): Flow<List<SettlementEntity>>

    @Insert
    abstract suspend fun insertFriend(friend: FriendEntity): Long

    @Update
    abstract suspend fun updateFriend(friend: FriendEntity): Int

    @Query("DELETE FROM friends WHERE id = :id")
    abstract suspend fun deleteFriendRow(id: Long)

    @Query("DELETE FROM group_members WHERE friendId = :friendId")
    abstract suspend fun deleteMemberships(friendId: Long)

    @Insert
    abstract suspend fun insertGroup(group: GroupEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertMembers(members: List<GroupMemberEntity>)

    @Query("DELETE FROM split_groups WHERE id = :id")
    abstract suspend fun deleteGroup(id: Long)

    @Insert
    abstract suspend fun insertExpense(expense: SharedExpenseEntity): Long

    @Insert
    abstract suspend fun insertSplits(splits: List<SplitEntity>)

    @Query("DELETE FROM shared_expenses WHERE id = :id")
    abstract suspend fun deleteExpense(id: Long)

    @Insert
    abstract suspend fun insertSettlement(settlement: SettlementEntity): Long

    @Query("DELETE FROM settlements WHERE id = :id")
    abstract suspend fun deleteSettlement(id: Long)

    @Transaction
    open suspend fun insertExpenseWithSplits(expense: SharedExpenseEntity, splits: List<SplitEntity>): Long {
        val expenseId = insertExpense(expense)
        insertSplits(splits.map { it.copy(id = 0, expenseId = expenseId) })
        return expenseId
    }

    @Transaction
    open suspend fun createGroup(group: GroupEntity, friendIds: List<Long>): Long {
        val groupId = insertGroup(group)
        insertMembers(friendIds.map { GroupMemberEntity(groupId, it) })
        return groupId
    }

    @Transaction
    open suspend fun deleteFriend(id: Long) {
        deleteMemberships(id)
        deleteFriendRow(id)
    }

    // --- backup / restore ---

    @Query("SELECT * FROM friends") abstract suspend fun getFriends(): List<FriendEntity>
    @Query("SELECT * FROM split_groups") abstract suspend fun getGroups(): List<GroupEntity>
    @Query("SELECT * FROM group_members") abstract suspend fun getMembers(): List<GroupMemberEntity>
    @Query("SELECT * FROM shared_expenses") abstract suspend fun getExpenses(): List<SharedExpenseEntity>
    @Query("SELECT * FROM splits") abstract suspend fun getSplits(): List<SplitEntity>
    @Query("SELECT * FROM settlements") abstract suspend fun getSettlements(): List<SettlementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun restoreFriends(rows: List<FriendEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun restoreGroups(rows: List<GroupEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun restoreMembers(rows: List<GroupMemberEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun restoreExpenses(rows: List<SharedExpenseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun restoreSplits(rows: List<SplitEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun restoreSettlements(rows: List<SettlementEntity>)

    @Query("DELETE FROM splits") abstract suspend fun clearSplits()
    @Query("DELETE FROM shared_expenses") abstract suspend fun clearExpenses()
    @Query("DELETE FROM group_members") abstract suspend fun clearMembers()
    @Query("DELETE FROM split_groups") abstract suspend fun clearGroups()
    @Query("DELETE FROM friends") abstract suspend fun clearFriends()
    @Query("DELETE FROM settlements") abstract suspend fun clearSettlements()
}

@Dao
interface PlannerDao {
    @Query("SELECT * FROM commitments ORDER BY type, dueDay, name COLLATE NOCASE")
    fun observeCommitments(): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM commitments")
    suspend fun getCommitments(): List<CommitmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCommitment(commitment: CommitmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreCommitments(rows: List<CommitmentEntity>)

    @Query("DELETE FROM commitments WHERE id = :id")
    suspend fun deleteCommitment(id: Long)

    @Query("DELETE FROM commitments")
    suspend fun clearCommitments()

    @Query("SELECT * FROM budget_settings WHERE id = 1")
    fun observeBudget(): Flow<BudgetSettingsEntity?>

    @Query("SELECT * FROM budget_settings WHERE id = 1")
    suspend fun getBudget(): BudgetSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBudget(budget: BudgetSettingsEntity)
}
