package com.paisatrail.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        TransactionEntity::class,
        CategoryOverrideEntity::class,
        FriendEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        SharedExpenseEntity::class,
        SplitEntity::class,
        SettlementEntity::class,
        CommitmentEntity::class,
        BudgetSettingsEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryOverrideDao(): CategoryOverrideDao
    abstract fun splitDao(): SplitDao
    abstract fun plannerDao(): PlannerDao

    companion object {
        const val NAME = "paisatrail.db"

        /**
         * Schema changes: bump [Database.version], add a Migration here, and commit the JSON that
         * Room writes to app/schemas/. Example for a future version 2:
         *
         *     val MIGRATION_1_2 = object : Migration(1, 2) {
         *         override fun migrate(db: SupportSQLiteDatabase) {
         *             db.execSQL("ALTER TABLE transactions ADD COLUMN paymentMethod TEXT")
         *         }
         *     }
         *
         * then list it below. There is deliberately no destructive fallback: a missing migration
         * should fail loudly rather than wipe someone's financial history.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()
    }
}
