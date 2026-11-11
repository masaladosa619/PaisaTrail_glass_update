package com.paisatrail.domain.parse

import com.paisatrail.domain.model.StatementParseResult
import com.paisatrail.domain.model.StatementRow
import com.paisatrail.domain.model.TxnType
import com.paisatrail.domain.util.Money
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Bank-agnostic heuristic parser for the text of a PDF statement.
 *
 * A transaction row is any line that starts with a date and ends with one to three money
 * columns (amount [+ balance], or withdrawal + deposit + balance). Wrapped description lines
 * are folded into the row above. Debit vs credit is decided, in order, by an explicit Dr/Cr
 * marker, by how the running balance moved, and finally by keywords in the description.
 */
object StatementParser {

    const val MAX_SPAN_DAYS = 31L

    private val AMOUNT = Regex(
        """(?<![\d.,])((?:\d{1,3}(?:,\d{2,3})+|\d+)\.\d{2})(?![\d,])\s*\(?\s*(?:(Cr|Dr|CR|DR|cr|dr)(?![A-Za-z]))?\.?\s*\)?\s*"""
    )
    private val NOISE = Regex(
        """(?i)(opening balance|closing balance|balance b/?f|brought forward|carried forward|page\s*(no|\d)|""" +
            """statement of account|statement period|\btotal\b|generated on|end of statement|computer generated|""" +
            """customer care|branch address|\bifsc\b|\bmicr\b|nominee)"""
    )
    private val OPENING = Regex("""(?i)(opening balance|balance b/?f|brought forward)""")
    private val CREDIT_HINT = Regex(
        """(?i)(\bCR\b|/CR/|UPI/CR|UPIIN|\bcredit(ed)?\b|\bsalary\b|\brefund\b|\breversal\b|\bcashback\b|""" +
            """\bdeposit\b|\breceived\b|\bby transfer\b|\bby cash\b|\bint\.?\s?pd\b)"""
    )
    private val IFSC = Regex("""^[A-Z]{4}0[A-Z0-9]{6}$""")
    private val MASKED = Regex("""^[xX*]+\d*$""")
    private val SPACES = Regex("""\s+""")
    private val NOISE_WORDS = setOf(
        "UPI", "DR", "CR", "IMPS", "NEFT", "RTGS", "POS", "ATM", "ACH", "NACH", "ECS", "MMT", "INB", "BIL",
        "PAYMENT", "PAY", "P2M", "P2A", "P2P", "TRANSFER", "TO", "BY", "FROM", "MB", "IB", "NA", "OK",
        "UPIOUT", "UPIIN", "ECOM", "PUR", "VIN", "VPS", "IPS", "BILLPAY", "SENT", "WDL", "TFR", "DEP",
        "COLLECT", "MOB", "NET", "TXN", "REF", "NO", "DEBIT", "CREDIT", "CARD", "PURCHASE"
    )
    private val BANK_CODES = setOf(
        "HDFC", "ICIC", "SBIN", "UTIB", "KKBK", "YESB", "PUNB", "BARB", "IDFB", "INDB", "CNRB", "UBIN",
        "IOBA", "MAHB", "AIRP", "PYTM", "FDRL", "IDIB", "BKID", "JIOP", "AUBL"
    )

    private class Column(val paise: Long, val marker: String?)

    private class Draft(val date: LocalDate, val description: StringBuilder, var columns: List<Column>) {
        var extraLines = 0
    }

    fun parse(text: String): StatementParseResult {
        val drafts = ArrayList<Draft>()
        var pending: Draft? = null
        var openingBalance: Long? = null

        for (rawLine in text.lineSequence()) {
            val line = rawLine.replace(' ', ' ').trim()
            if (line.isEmpty()) continue

            val dateMatch = DateParsers.findDate(line)
            if (dateMatch != null && dateMatch.range.first <= 4) {
                pending?.let { drafts.add(it) }
                var rest = line.substring(dateMatch.range.last + 1).trimStart()
                // Many statements print a value date right after the transaction date.
                val second = DateParsers.findDate(rest)
                if (second != null && second.range.first == 0) {
                    rest = rest.substring(second.range.last + 1).trimStart()
                }
                val (columns, descriptionEnd) = trailingColumns(rest)
                pending = Draft(dateMatch.date, StringBuilder(rest.substring(0, descriptionEnd).trim()), columns)
                continue
            }

            if (NOISE.containsMatchIn(line)) {
                if (OPENING.containsMatchIn(line) && openingBalance == null) {
                    openingBalance = trailingColumns(line).first.lastOrNull()?.paise
                }
                pending?.let { drafts.add(it) }
                pending = null
                continue
            }

            val current = pending ?: continue
            if (current.extraLines >= 3) continue
            val (columns, descriptionEnd) = trailingColumns(line)
            if (columns.isEmpty()) {
                append(current.description, line)
            } else if (current.columns.isEmpty()) {
                current.columns = columns
                append(current.description, line.substring(0, descriptionEnd))
            }
            current.extraLines++
        }
        pending?.let { drafts.add(it) }

        // Some banks list newest first; balance maths needs oldest first.
        if (drafts.size >= 2 && drafts.first().date.isAfter(drafts.last().date)) {
            drafts.reverse()
            openingBalance = null
        }

        val rows = ArrayList<StatementRow>()
        var skipped = 0
        var previousBalance = openingBalance
        for (draft in drafts) {
            val columns = draft.columns
            if (columns.isEmpty()) {
                skipped++
                continue
            }
            val description = draft.description.toString().replace(SPACES, " ").trim()
            var type: TxnType? = null
            val amount: Long
            val marker: String?
            val balance: Long?
            when (columns.size) {
                1 -> {
                    amount = columns[0].paise; marker = columns[0].marker; balance = null
                }
                2 -> {
                    amount = columns[0].paise; marker = columns[0].marker; balance = columns[1].paise
                }
                else -> {
                    val withdrawal = columns[0]
                    val deposit = columns[1]
                    balance = columns[2].paise
                    if (withdrawal.paise > 0 && deposit.paise == 0L) {
                        amount = withdrawal.paise; marker = null; type = TxnType.EXPENSE
                    } else if (deposit.paise > 0 && withdrawal.paise == 0L) {
                        amount = deposit.paise; marker = null; type = TxnType.INCOME
                    } else {
                        amount = deposit.paise; marker = deposit.marker
                    }
                }
            }
            val resolved = type ?: when {
                marker == "DR" -> TxnType.EXPENSE
                marker == "CR" -> TxnType.INCOME
                previousBalance != null && balance != null && balance == previousBalance - amount -> TxnType.EXPENSE
                previousBalance != null && balance != null && balance == previousBalance + amount -> TxnType.INCOME
                CREDIT_HINT.containsMatchIn(description) -> TxnType.INCOME
                else -> TxnType.EXPENSE
            }
            if (balance != null) previousBalance = balance
            if (amount <= 0) continue
            rows.add(
                StatementRow(
                    date = draft.date,
                    description = description,
                    merchant = extractMerchant(description),
                    amountPaise = amount,
                    type = resolved,
                    balancePaise = balance
                )
            )
        }
        return StatementParseResult(rows, skipped)
    }

    /** Number of days between the first and last transaction, inclusive. 0 for an empty list. */
    fun spanDays(rows: List<StatementRow>): Long {
        if (rows.isEmpty()) return 0
        val first = rows.minOf { it.date }
        val last = rows.maxOf { it.date }
        return ChronoUnit.DAYS.between(first, last) + 1
    }

    /** Stable id for a statement row so importing the same statement twice does not duplicate it. */
    fun fingerprint(row: StatementRow): String =
        "stmt:${row.date}:${row.type.name}:${row.amountPaise}:${row.balancePaise ?: "x"}:" +
            Integer.toHexString(row.description.hashCode())

    /** Pulls a readable payee out of a bank narration such as "UPI/DR/4123/SWIGGY/UTIB/x@axis". */
    fun extractMerchant(description: String): String {
        val tokens = description.split('/', '-', '|', '*').map { it.trim() }.filter { it.isNotEmpty() }
        for (token in tokens) {
            if (token.contains('@')) continue
            if (IFSC.matches(token.uppercase())) continue
            val words = token.split(SPACES).dropWhile { word ->
                word.uppercase().trim('.', ':', ',') in NOISE_WORDS || word.any { it.isDigit() } || MASKED.matches(word)
            }
            val candidate = words.take(4).joinToString(" ").trim(' ', '.', ',', ':')
            if (candidate.count { it.isLetter() } >= 3 && candidate.uppercase() !in BANK_CODES) {
                return prettify(candidate).take(40)
            }
        }
        return description.trim().take(40).ifEmpty { "Unknown" }
    }

    private fun trailingColumns(text: String): Pair<List<Column>, Int> {
        val columns = ArrayList<Column>()
        var boundary = text.length
        for (m in AMOUNT.findAll(text).toList().asReversed()) {
            if (text.substring(m.range.last + 1, boundary).isNotBlank()) break
            val paise = Money.parseToPaise(m.groupValues[1]) ?: break
            columns.add(0, Column(paise, m.groupValues[2].ifEmpty { null }?.uppercase()))
            boundary = m.range.first
            if (columns.size == 3) break
        }
        return columns to boundary
    }

    private fun append(target: StringBuilder, text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || target.length > 160) return
        if (target.isNotEmpty()) target.append(' ')
        target.append(clean)
    }

    private fun prettify(text: String): String =
        if (text.any { it.isLowerCase() }) text
        else text.lowercase().split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
}
