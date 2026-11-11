package com.paisatrail.domain.split

/** Participant id used for "you" in every split. Friends use their database id (always > 0). */
const val SELF_ID = 0L

data class ShareInput(val participantId: Long, val sharePaise: Long)

data class ExpenseInput(
    val paidById: Long,
    val amountPaise: Long,
    val shares: List<ShareInput>,
    val groupId: Long? = null
)

data class SettlementInput(
    val fromId: Long,
    val toId: Long,
    val amountPaise: Long,
    val groupId: Long? = null
)

object SplitCalculator {

    /**
     * Splits [totalPaise] evenly. Leftover paise go to the first participants one at a time,
     * so the shares always add up to exactly the total.
     */
    fun equalSplit(totalPaise: Long, participantIds: List<Long>): List<ShareInput> {
        if (participantIds.isEmpty() || totalPaise <= 0) return emptyList()
        val base = totalPaise / participantIds.size
        val remainder = (totalPaise % participantIds.size).toInt()
        return participantIds.mapIndexed { index, id ->
            ShareInput(id, base + if (index < remainder) 1 else 0)
        }
    }

    fun isValidCustomSplit(totalPaise: Long, shares: List<ShareInput>): Boolean =
        shares.isNotEmpty() && shares.all { it.sharePaise >= 0 } && shares.sumOf { it.sharePaise } == totalPaise

    /**
     * Net position between you and every other participant.
     * Positive = they owe you, negative = you owe them. Debts between two friends are not yours
     * to track, so they are ignored.
     */
    fun balancesWithSelf(
        expenses: List<ExpenseInput>,
        settlements: List<SettlementInput>
    ): Map<Long, Long> {
        val balances = LinkedHashMap<Long, Long>()
        fun add(id: Long, delta: Long) {
            balances[id] = (balances[id] ?: 0L) + delta
        }
        for (expense in expenses) {
            if (expense.paidById == SELF_ID) {
                expense.shares.filter { it.participantId != SELF_ID }.forEach { add(it.participantId, it.sharePaise) }
            } else {
                val myShare = expense.shares.firstOrNull { it.participantId == SELF_ID }?.sharePaise ?: 0L
                if (myShare != 0L) add(expense.paidById, -myShare)
            }
        }
        for (settlement in settlements) {
            when {
                settlement.toId == SELF_ID && settlement.fromId != SELF_ID -> add(settlement.fromId, -settlement.amountPaise)
                settlement.fromId == SELF_ID && settlement.toId != SELF_ID -> add(settlement.toId, settlement.amountPaise)
            }
        }
        return balances
    }

    fun totalOwedToYou(balances: Map<Long, Long>): Long = balances.values.filter { it > 0 }.sum()

    fun totalYouOwe(balances: Map<Long, Long>): Long = -balances.values.filter { it < 0 }.sum()
}
