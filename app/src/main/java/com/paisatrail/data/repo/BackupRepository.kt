package com.paisatrail.data.repo

import androidx.room.withTransaction
import com.paisatrail.data.local.AppDatabase
import com.paisatrail.data.local.BudgetSettingsEntity
import com.paisatrail.data.local.CategoryOverrideEntity
import com.paisatrail.data.local.CommitmentEntity
import com.paisatrail.data.local.FriendEntity
import com.paisatrail.data.local.GroupEntity
import com.paisatrail.data.local.GroupMemberEntity
import com.paisatrail.data.local.SettlementEntity
import com.paisatrail.data.local.SharedExpenseEntity
import com.paisatrail.data.local.SplitEntity
import com.paisatrail.data.local.TransactionEntity
import com.paisatrail.domain.model.Category
import com.paisatrail.domain.model.CommitmentType
import com.paisatrail.domain.model.TxnSource
import com.paisatrail.domain.model.TxnType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class RestoreSummary(val transactions: Int, val friends: Int, val commitments: Int)

/**
 * Full-data backup as one human-readable JSON document, plus CSV export of transactions.
 * The Gemini API key is deliberately NOT part of a backup.
 */
@Singleton
class BackupRepository @Inject constructor(private val db: AppDatabase) {

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val split = db.splitDao()
        val planner = db.plannerDao()
        val root = JSONObject()
        root.put("app", "PaisaTrail")
        root.put("formatVersion", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("transactions", db.transactionDao().getAll().toJsonArray { it.toJson() })
        root.put("categoryOverrides", db.categoryOverrideDao().getAll().toJsonArray {
            JSONObject().put("merchantKey", it.merchantKey).put("category", it.category.name)
        })
        root.put("friends", split.getFriends().toJsonArray {
            JSONObject().put("id", it.id).put("name", it.name).put("upiId", it.upiId ?: JSONObject.NULL)
        })
        root.put("groups", split.getGroups().toJsonArray {
            JSONObject().put("id", it.id).put("name", it.name).put("createdAt", it.createdAt)
        })
        root.put("groupMembers", split.getMembers().toJsonArray {
            JSONObject().put("groupId", it.groupId).put("friendId", it.friendId)
        })
        root.put("sharedExpenses", split.getExpenses().toJsonArray {
            JSONObject().put("id", it.id).put("groupId", it.groupId ?: JSONObject.NULL)
                .put("description", it.description).put("amountPaise", it.amountPaise)
                .put("paidById", it.paidById).put("timestamp", it.timestamp)
        })
        root.put("splits", split.getSplits().toJsonArray {
            JSONObject().put("id", it.id).put("expenseId", it.expenseId)
                .put("participantId", it.participantId).put("sharePaise", it.sharePaise)
        })
        root.put("settlements", split.getSettlements().toJsonArray {
            JSONObject().put("id", it.id).put("fromId", it.fromId).put("toId", it.toId)
                .put("amountPaise", it.amountPaise).put("timestamp", it.timestamp)
                .put("groupId", it.groupId ?: JSONObject.NULL).put("note", it.note)
        })
        root.put("commitments", planner.getCommitments().toJsonArray {
            JSONObject().put("id", it.id).put("type", it.type.name).put("name", it.name)
                .put("amountPaise", it.amountPaise).put("dueDay", it.dueDay)
                .put("creditLimitPaise", it.creditLimitPaise ?: JSONObject.NULL)
        })
        val budget = planner.getBudget() ?: BudgetSettingsEntity()
        root.put(
            "budget",
            JSONObject().put("monthlyBudgetPaise", budget.monthlyBudgetPaise)
                .put("monthlyIncomePaise", budget.monthlyIncomePaise)
        )
        root.toString(2)
    }

    /**
     * Replaces everything in the database with the backup's contents, atomically.
     * Throws IllegalArgumentException with a readable message if the file is not a PaisaTrail backup.
     */
    suspend fun importJson(json: String): RestoreSummary = withContext(Dispatchers.IO) {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("That file isn't valid JSON.")
        }
        if (root.optString("app") != "PaisaTrail" || !root.has("transactions")) {
            throw IllegalArgumentException("That file isn't a PaisaTrail backup.")
        }
        if (root.optInt("formatVersion", 1) > FORMAT_VERSION) {
            throw IllegalArgumentException("This backup was made by a newer version of the app.")
        }

        val transactions = root.array("transactions").map { it.toTransaction() }
        val overrides = root.array("categoryOverrides").map {
            CategoryOverrideEntity(it.getString("merchantKey"), Category.fromName(it.optString("category")))
        }
        val friends = root.array("friends").map {
            FriendEntity(it.getLong("id"), it.getString("name"), it.stringOrNull("upiId"))
        }
        val groups = root.array("groups").map {
            GroupEntity(it.getLong("id"), it.getString("name"), it.optLong("createdAt"))
        }
        val members = root.array("groupMembers").map {
            GroupMemberEntity(it.getLong("groupId"), it.getLong("friendId"))
        }
        val expenses = root.array("sharedExpenses").map {
            SharedExpenseEntity(
                id = it.getLong("id"),
                groupId = it.longOrNull("groupId"),
                description = it.optString("description"),
                amountPaise = it.getLong("amountPaise"),
                paidById = it.getLong("paidById"),
                timestamp = it.optLong("timestamp")
            )
        }
        val splits = root.array("splits").map {
            SplitEntity(it.getLong("id"), it.getLong("expenseId"), it.getLong("participantId"), it.getLong("sharePaise"))
        }
        val settlements = root.array("settlements").map {
            SettlementEntity(
                id = it.getLong("id"),
                fromId = it.getLong("fromId"),
                toId = it.getLong("toId"),
                amountPaise = it.getLong("amountPaise"),
                timestamp = it.optLong("timestamp"),
                groupId = it.longOrNull("groupId"),
                note = it.optString("note")
            )
        }
        val commitments = root.array("commitments").map {
            CommitmentEntity(
                id = it.getLong("id"),
                type = runCatching { CommitmentType.valueOf(it.getString("type")) }.getOrDefault(CommitmentType.SUBSCRIPTION),
                name = it.optString("name"),
                amountPaise = it.getLong("amountPaise"),
                dueDay = it.optInt("dueDay", 1),
                creditLimitPaise = it.longOrNull("creditLimitPaise")
            )
        }
        val budgetJson = root.optJSONObject("budget")
        val budget = BudgetSettingsEntity(
            id = 1,
            monthlyBudgetPaise = budgetJson?.optLong("monthlyBudgetPaise") ?: 0L,
            monthlyIncomePaise = budgetJson?.optLong("monthlyIncomePaise") ?: 0L
        )

        db.withTransaction {
            val split = db.splitDao()
            val planner = db.plannerDao()
            db.transactionDao().deleteAll()
            db.categoryOverrideDao().deleteAll()
            split.clearSplits()
            split.clearExpenses()
            split.clearMembers()
            split.clearGroups()
            split.clearFriends()
            split.clearSettlements()
            planner.clearCommitments()

            db.transactionDao().restoreAll(transactions)
            db.categoryOverrideDao().restoreAll(overrides)
            split.restoreFriends(friends)
            split.restoreGroups(groups)
            split.restoreMembers(members)
            split.restoreExpenses(expenses)
            split.restoreSplits(splits)
            split.restoreSettlements(settlements)
            planner.restoreCommitments(commitments)
            planner.saveBudget(budget)
        }
        RestoreSummary(transactions.size, friends.size, commitments.size)
    }

    /** Spreadsheet-friendly export. Amounts are in rupees. */
    suspend fun exportCsv(): String = withContext(Dispatchers.IO) {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
        val sb = StringBuilder("date,type,merchant,category,amount_inr,reference_id,source,note\n")
        for (txn in db.transactionDao().getAll()) {
            sb.append(formatter.format(Instant.ofEpochMilli(txn.timestamp))).append(',')
                .append(txn.type.name).append(',')
                .append(csv(txn.merchant)).append(',')
                .append(csv(txn.category.label)).append(',')
                .append(txn.amountPaise / 100).append('.').append((txn.amountPaise % 100).toString().padStart(2, '0')).append(',')
                .append(csv(txn.txnId.orEmpty())).append(',')
                .append(txn.source.name).append(',')
                .append(csv(txn.note)).append('\n')
        }
        sb.toString()
    }

    private fun csv(value: String): String {
        // Leading =, +, - or @ would be run as a formula by spreadsheet apps; neutralise it.
        val safe = if (value.isNotEmpty() && value[0] in "=+-@") "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }

    private fun TransactionEntity.toJson(): JSONObject = JSONObject()
        .put("id", id).put("amountPaise", amountPaise).put("type", type.name)
        .put("merchant", merchant).put("category", category.name).put("timestamp", timestamp)
        .put("txnId", txnId ?: JSONObject.NULL).put("source", source.name).put("note", note)

    private fun JSONObject.toTransaction(): TransactionEntity = TransactionEntity(
        id = getLong("id"),
        amountPaise = getLong("amountPaise"),
        type = runCatching { TxnType.valueOf(getString("type")) }.getOrDefault(TxnType.EXPENSE),
        merchant = optString("merchant"),
        category = Category.fromName(optString("category")),
        timestamp = getLong("timestamp"),
        txnId = stringOrNull("txnId"),
        source = runCatching { TxnSource.valueOf(getString("source")) }.getOrDefault(TxnSource.MANUAL),
        note = optString("note")
    )

    private fun <T> List<T>.toJsonArray(transform: (T) -> JSONObject): JSONArray {
        val array = JSONArray()
        forEach { array.put(transform(it)) }
        return array
    }

    private fun JSONObject.array(name: String): List<JSONObject> {
        val array = optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun JSONObject.stringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).ifEmpty { null }

    private fun JSONObject.longOrNull(name: String): Long? = if (isNull(name)) null else optLong(name)

    private companion object {
        const val FORMAT_VERSION = 1
    }
}
