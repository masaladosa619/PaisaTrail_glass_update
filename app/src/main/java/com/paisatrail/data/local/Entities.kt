package com.paisatrail.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.paisatrail.domain.model.Category
import com.paisatrail.domain.model.CommitmentType
import com.paisatrail.domain.model.TxnSource
import com.paisatrail.domain.model.TxnType

/**
 * All money columns are paise (Long). Enums are stored by name (Room does this natively),
 * so never rename an enum constant without a migration.
 */
@Entity(
    tableName = "transactions",
    indices = [Index(value = ["txnId"], unique = true), Index(value = ["timestamp"])]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val type: TxnType,
    val merchant: String,
    val category: Category,
    val timestamp: Long,
    /** UPI / bank reference. Unique when present, which is how duplicates are rejected. */
    val txnId: String? = null,
    val source: TxnSource,
    val note: String = ""
)

/** A correction the user made once: this merchant always belongs to this category. */
@Entity(tableName = "category_overrides")
data class CategoryOverrideEntity(
    @PrimaryKey val merchantKey: String,
    val category: Category
)

@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val upiId: String? = null
)

@Entity(tableName = "split_groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "friendId"],
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["friendId"])]
)
data class GroupMemberEntity(
    val groupId: Long,
    val friendId: Long
)

@Entity(
    tableName = "shared_expenses",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["groupId"])]
)
data class SharedExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long? = null,
    val description: String,
    val amountPaise: Long,
    /** 0 means "you" (SplitCalculator.SELF_ID); otherwise a friend id. */
    val paidById: Long,
    val timestamp: Long
)

@Entity(
    tableName = "splits",
    foreignKeys = [
        ForeignKey(
            entity = SharedExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["expenseId"])]
)
data class SplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val participantId: Long,
    val sharePaise: Long
)

@Entity(tableName = "settlements")
data class SettlementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromId: Long,
    val toId: Long,
    val amountPaise: Long,
    val timestamp: Long,
    val groupId: Long? = null,
    val note: String = ""
)

@Entity(tableName = "commitments")
data class CommitmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: CommitmentType,
    val name: String,
    /** Monthly amount for subscriptions and EMIs; current outstanding for credit cards. */
    val amountPaise: Long,
    /** Day of month (1-31) the payment is due. */
    val dueDay: Int,
    val creditLimitPaise: Long? = null
)

/** Single-row table (id is always 1). */
@Entity(tableName = "budget_settings")
data class BudgetSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val monthlyBudgetPaise: Long = 0,
    val monthlyIncomePaise: Long = 0
)
