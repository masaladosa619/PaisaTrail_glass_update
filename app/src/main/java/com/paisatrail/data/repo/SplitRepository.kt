package com.paisatrail.data.repo

import com.paisatrail.data.local.FriendEntity
import com.paisatrail.data.local.GroupEntity
import com.paisatrail.data.local.GroupMemberEntity
import com.paisatrail.data.local.SettlementEntity
import com.paisatrail.data.local.SharedExpenseEntity
import com.paisatrail.data.local.SplitDao
import com.paisatrail.data.local.SplitEntity
import com.paisatrail.domain.split.ExpenseInput
import com.paisatrail.domain.split.SELF_ID
import com.paisatrail.domain.split.SettlementInput
import com.paisatrail.domain.split.ShareInput
import com.paisatrail.domain.split.SplitCalculator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class FriendBalance(
    val friend: FriendEntity,
    /** Positive: they owe you. Negative: you owe them. */
    val balancePaise: Long
)

data class GroupSummary(
    val group: GroupEntity,
    val memberIds: List<Long>,
    val memberNames: List<String>,
    val balancePaise: Long,
    val expenseCount: Int,
    val totalSpentPaise: Long
)

data class SettlementView(val settlement: SettlementEntity, val title: String)

data class ExpenseView(val expense: SharedExpenseEntity, val paidByName: String, val groupName: String?)

data class SplitSnapshot(
    val friends: List<FriendBalance> = emptyList(),
    val groups: List<GroupSummary> = emptyList(),
    val settlements: List<SettlementView> = emptyList(),
    val expenses: List<ExpenseView> = emptyList(),
    val owedToYouPaise: Long = 0,
    val youOwePaise: Long = 0,
    val loaded: Boolean = false
)

@Singleton
class SplitRepository @Inject constructor(private val dao: SplitDao) {

    private data class People(
        val friends: List<FriendEntity>,
        val groups: List<GroupEntity>,
        val members: List<GroupMemberEntity>
    )

    private data class Ledger(
        val expenses: List<SharedExpenseEntity>,
        val splits: List<SplitEntity>,
        val settlements: List<SettlementEntity>
    )

    fun observe(): Flow<SplitSnapshot> {
        val people = combine(dao.observeFriends(), dao.observeGroups(), dao.observeMembers()) { f, g, m -> People(f, g, m) }
        val ledger = combine(dao.observeExpenses(), dao.observeSplits(), dao.observeSettlements()) { e, s, t -> Ledger(e, s, t) }
        return combine(people, ledger) { p, l -> build(p, l) }
    }

    private fun build(people: People, ledger: Ledger): SplitSnapshot {
        val splitsByExpense = ledger.splits.groupBy { it.expenseId }
        val expenseInputs = ledger.expenses.map { expense ->
            ExpenseInput(
                paidById = expense.paidById,
                amountPaise = expense.amountPaise,
                shares = splitsByExpense[expense.id].orEmpty().map { ShareInput(it.participantId, it.sharePaise) },
                groupId = expense.groupId
            )
        }
        val settlementInputs = ledger.settlements.map { SettlementInput(it.fromId, it.toId, it.amountPaise, it.groupId) }
        val balances = SplitCalculator.balancesWithSelf(expenseInputs, settlementInputs)
        val names = people.friends.associate { it.id to it.name }
        fun nameOf(id: Long): String = if (id == SELF_ID) "You" else names[id] ?: "Former friend"

        val groupNames = people.groups.associate { it.id to it.name }
        val groups = people.groups.map { group ->
            val memberIds = people.members.filter { it.groupId == group.id }.map { it.friendId }
            val groupExpenses = expenseInputs.filter { it.groupId == group.id }
            val groupSettlements = settlementInputs.filter { it.groupId == group.id }
            GroupSummary(
                group = group,
                memberIds = memberIds,
                memberNames = memberIds.map { nameOf(it) },
                balancePaise = SplitCalculator.balancesWithSelf(groupExpenses, groupSettlements).values.sum(),
                expenseCount = groupExpenses.size,
                totalSpentPaise = groupExpenses.sumOf { it.amountPaise }
            )
        }

        return SplitSnapshot(
            friends = people.friends.map { FriendBalance(it, balances[it.id] ?: 0L) },
            groups = groups,
            settlements = ledger.settlements.map { settlement ->
                val title = if (settlement.toId == SELF_ID) {
                    "${nameOf(settlement.fromId)} paid you"
                } else {
                    "You paid ${nameOf(settlement.toId)}"
                }
                SettlementView(settlement, title)
            },
            expenses = ledger.expenses.map { ExpenseView(it, nameOf(it.paidById), it.groupId?.let { id -> groupNames[id] }) },
            owedToYouPaise = SplitCalculator.totalOwedToYou(balances),
            youOwePaise = SplitCalculator.totalYouOwe(balances),
            loaded = true
        )
    }

    suspend fun addFriend(name: String, upiId: String?): Long =
        dao.insertFriend(FriendEntity(name = name.trim(), upiId = upiId?.trim()?.ifEmpty { null }))

    suspend fun updateFriend(friend: FriendEntity) {
        dao.updateFriend(friend)
    }

    suspend fun deleteFriend(id: Long) = dao.deleteFriend(id)

    suspend fun createGroup(name: String, friendIds: List<Long>): Long =
        dao.createGroup(GroupEntity(name = name.trim(), createdAt = System.currentTimeMillis()), friendIds)

    suspend fun deleteGroup(id: Long) = dao.deleteGroup(id)

    suspend fun addExpense(
        description: String,
        amountPaise: Long,
        paidById: Long,
        shares: List<ShareInput>,
        groupId: Long?
    ): Long = dao.insertExpenseWithSplits(
        SharedExpenseEntity(
            groupId = groupId,
            description = description.trim(),
            amountPaise = amountPaise,
            paidById = paidById,
            timestamp = System.currentTimeMillis()
        ),
        shares.map { SplitEntity(expenseId = 0, participantId = it.participantId, sharePaise = it.sharePaise) }
    )

    suspend fun deleteExpense(id: Long) = dao.deleteExpense(id)

    /** Records that money changed hands. [friendPaidYou] true = the friend paid you. */
    suspend fun settle(friendId: Long, amountPaise: Long, friendPaidYou: Boolean, note: String = ""): Long =
        dao.insertSettlement(
            SettlementEntity(
                fromId = if (friendPaidYou) friendId else SELF_ID,
                toId = if (friendPaidYou) SELF_ID else friendId,
                amountPaise = amountPaise,
                timestamp = System.currentTimeMillis(),
                note = note
            )
        )

    suspend fun deleteSettlement(id: Long) = dao.deleteSettlement(id)
}
