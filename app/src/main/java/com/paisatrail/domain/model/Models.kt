package com.paisatrail.domain.model

import java.time.LocalDate

enum class TxnType { EXPENSE, INCOME }

enum class TxnSource { MANUAL, SCREENSHOT, STATEMENT }

enum class Category(val label: String) {
    FOOD("Food"),
    SHOPPING("Shopping"),
    TRAVEL("Travel"),
    GROCERY("Grocery"),
    RENT("Rent"),
    INVESTMENTS("Investments"),
    HEALTH("Health"),
    EMI_BILL("EMI/Bill"),
    SUBSCRIPTIONS("Subscriptions"),
    OTHERS("Others");

    companion object {
        fun fromName(name: String?): Category = entries.firstOrNull { it.name == name } ?: OTHERS
    }
}

enum class CommitmentType(val label: String) {
    SUBSCRIPTION("Subscriptions"),
    EMI("EMIs"),
    CREDIT_CARD("Credit card")
}

/** What the on-device parser could read from a payment-success screenshot. Null means "not found". */
data class ParsedPayment(
    val amountPaise: Long?,
    val merchant: String?,
    val timestamp: Long?,
    val txnId: String?,
    val paidFrom: String?,
    val type: TxnType,
    val rawText: String
)

/** One transaction row read from a bank statement. */
data class StatementRow(
    val date: LocalDate,
    val description: String,
    val merchant: String,
    val amountPaise: Long,
    val type: TxnType,
    val balancePaise: Long?
)

data class StatementParseResult(
    val rows: List<StatementRow>,
    /** Lines that started with a date but had no readable amount. */
    val skippedLines: Int
)
