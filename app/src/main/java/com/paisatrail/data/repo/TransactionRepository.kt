package com.paisatrail.data.repo

import com.paisatrail.data.ai.GeminiClient
import com.paisatrail.data.local.CategoryOverrideDao
import com.paisatrail.data.local.CategoryOverrideEntity
import com.paisatrail.data.local.TransactionDao
import com.paisatrail.data.local.TransactionEntity
import com.paisatrail.data.prefs.SettingsStore
import com.paisatrail.domain.categorize.Categorizer
import com.paisatrail.domain.model.Category
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao,
    private val overrideDao: CategoryOverrideDao,
    private val settings: SettingsStore,
    private val gemini: GeminiClient
) {
    fun observeMonth(month: YearMonth): Flow<List<TransactionEntity>> {
        val (from, to) = monthRange(month)
        return dao.observeBetween(from, to)
    }

    fun observeExpenseTotal(month: YearMonth): Flow<Long> {
        val (from, to) = monthRange(month)
        return dao.observeExpenseTotal(from, to)
    }

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun observeOverrideCount(): Flow<Int> = overrideDao.observeCount()

    suspend fun get(id: Long): TransactionEntity? = dao.getById(id)

    /** Returns the new id, or -1 if a transaction with the same reference id is already logged. */
    suspend fun add(txn: TransactionEntity): Long = dao.insert(txn)

    /** Returns how many rows were actually inserted (duplicates are skipped). */
    suspend fun addAll(txns: List<TransactionEntity>): Int = dao.insertAll(txns).count { it != -1L }

    suspend fun update(txn: TransactionEntity) {
        dao.update(txn)
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun overrides(): Map<String, Category> =
        overrideDao.getAll().associate { it.merchantKey to it.category }

    /** Remembers a user correction so this merchant is categorised the same way next time. */
    suspend fun learn(merchant: String, category: Category) {
        val key = Categorizer.merchantKey(merchant)
        if (key.isNotBlank()) overrideDao.upsert(CategoryOverrideEntity(key, category))
    }

    suspend fun clearOverrides() = overrideDao.deleteAll()

    /**
     * Offline rules first. Only if they say "Others", [allowAi] is true and the user has saved a
     * Gemini key do we ask the model, sending just the merchant name and narration.
     */
    suspend fun categorize(merchant: String, description: String = "", allowAi: Boolean = true): Category {
        val local = Categorizer.categorize(merchant, description, overrides())
        if (local != Category.OTHERS || !allowAi || merchant.isBlank()) return local
        val current = settings.current()
        if (current.geminiApiKey.isBlank()) return local
        val prompt = "Classify this Indian payment into exactly one of these categories: " +
            Category.entries.joinToString(", ") { it.label } +
            ". Reply with only the category name.\nMerchant: $merchant\nDetails: $description"
        val answer = gemini.generate(current.geminiApiKey, current.geminiModel, prompt) ?: return local
        return Category.entries.firstOrNull { answer.contains(it.label, ignoreCase = true) } ?: local
    }

    companion object {
        /** [start, end) of a calendar month in epoch millis. */
        fun monthRange(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
            val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return from to to
        }
    }
}
