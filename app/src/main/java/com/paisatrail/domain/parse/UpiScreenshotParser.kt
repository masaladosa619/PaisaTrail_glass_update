package com.paisatrail.domain.parse

import com.paisatrail.domain.model.ParsedPayment
import com.paisatrail.domain.model.TxnType
import com.paisatrail.domain.util.Money
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Turns the OCR lines of a UPI / bank "payment successful" screenshot into a [ParsedPayment].
 *
 * It is deliberately app-agnostic: it looks for labels that most Indian payment apps share
 * ("Paid to", "UTR", "Debited from", a rupee amount...) instead of matching one app's layout.
 * Anything it cannot find stays null so the UI can ask the user to fill it in.
 */
object UpiScreenshotParser {

    private val I = RegexOption.IGNORE_CASE

    private val CURRENCY_AMOUNT = Regex("""(?:₹|\bRs\.?|\bINR)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", I)
    private val BARE_AMOUNT_LINE = Regex("""^\D{0,2}([0-9][0-9,]*(?:\.[0-9]{1,2})?)$""")
    private val TRAILING_AMOUNT = Regex("""\s*(?:₹|\bRs\.?|\bINR)\s*[0-9][0-9,.]*\s*$""", I)

    private val INCOME_HINT =
        Regex("""\b(received from|money received|payment received|you received|credited to your|added to your)\b""", I)

    private const val SEP = """(?:\s*[:\-]\s*|\s+|$)(.*)$"""
    private val PAID_TO = Regex("^(?:paid to|sent to|payment to|transferred to|transfer to|paying)$SEP", I)
    private val TO = Regex("^to$SEP", I)
    private val RECEIVED_FROM = Regex("^(?:received from|money received from|payment received from)$SEP", I)
    private val FROM = Regex("^from$SEP", I)
    private val PAID_FROM = Regex("^(?:debited from|paid from|paid using|paid via|from account|from)$SEP", I)

    private val UPI_HANDLE = Regex("""([A-Za-z0-9._\-]{2,})@[A-Za-z]{2,}""")
    private val MASKED_ACCOUNT = Regex("""[xX*•]{2,}\s*\d{2,6}""")

    private val ID_LABELS = listOf(
        Regex("""\bUTR\b(?:\s*(?:no|number|id)\b)?\.?\s*[:\-#]?\s*(.*)$""", I),
        Regex("""\bUPI\s*(?:transaction|txn|ref(?:erence)?)\.?\s*(?:(?:id|no|number)\b)?\.?\s*[:\-#]?\s*(.*)$""", I),
        Regex("""\b(?:transaction|txn)\.?\s*(?:id|no|number)\b\.?\s*[:\-#]?\s*(.*)$""", I),
        Regex("""\b(?:reference|ref)\.?\s*(?:id|no|number)\b\.?\s*[:\-#]?\s*(.*)$""", I)
    )
    private val ID_TOKEN = Regex("""[A-Za-z0-9]{8,}""")
    private val TWELVE_DIGITS = Regex("""(?<!\d)\d{12}(?!\d)""")

    private val NOT_A_NAME_PREFIX = Regex("""^(payment|transaction|upi|debited|credited|paid|sent|banking name)\b""", I)
    private val NOT_A_NAME_WORDS =
        setOf("completed", "successful", "success", "message", "details", "share", "done", "to", "from")

    fun parse(
        rawLines: List<String>,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone)
    ): ParsedPayment {
        val lines = rawLines.map { it.replace(' ', ' ').trim() }.filter { it.isNotEmpty() }
        val type = if (lines.any { INCOME_HINT.containsMatchIn(it) }) TxnType.INCOME else TxnType.EXPENSE
        return ParsedPayment(
            amountPaise = findAmount(lines),
            merchant = findMerchant(lines, type),
            timestamp = findTimestamp(lines, zone, today),
            txnId = findTxnId(lines),
            paidFrom = if (type == TxnType.EXPENSE) findPaidFrom(lines) else null,
            type = type,
            rawText = lines.joinToString("\n")
        )
    }

    private fun findAmount(lines: List<String>): Long? {
        for (line in lines) {
            val m = CURRENCY_AMOUNT.find(line) ?: continue
            val paise = Money.parseToPaise(m.groupValues[1])
            if (paise != null && paise > 0) return paise
        }
        // OCR often drops or mangles the rupee sign, so fall back to a line that is just a number.
        val bare = lines.mapNotNull { line ->
            val raw = BARE_AMOUNT_LINE.find(line)?.groupValues?.get(1) ?: return@mapNotNull null
            if (raw.substringBefore('.').count { it.isDigit() } > 7) return@mapNotNull null
            val paise = Money.parseToPaise(raw) ?: return@mapNotNull null
            if (paise > 0) raw to paise else null
        }
        val formatted = bare.firstOrNull { it.first.contains(',') || it.first.contains('.') }
        return (formatted ?: bare.firstOrNull())?.second
    }

    private fun findMerchant(lines: List<String>, type: TxnType): String? {
        val labelled = if (type == TxnType.INCOME) {
            afterLabel(lines, RECEIVED_FROM) ?: afterLabel(lines, FROM)
        } else {
            afterLabel(lines, PAID_TO) ?: afterLabel(lines, TO)
        }
        if (labelled != null) return labelled
        for ((i, line) in lines.withIndex()) {
            val m = UPI_HANDLE.find(line) ?: continue
            val previous = lines.getOrNull(i - 1)?.let { cleanName(it) }
            return if (previous != null && looksLikeName(previous)) previous else m.groupValues[1]
        }
        return null
    }

    private fun afterLabel(lines: List<String>, label: Regex): String? {
        for ((i, line) in lines.withIndex()) {
            val m = label.find(line) ?: continue
            val sameLine = cleanName(m.groupValues[1])
            if (looksLikeName(sameLine)) return sameLine
            for (j in i + 1..minOf(i + 2, lines.lastIndex)) {
                val candidate = cleanName(lines[j])
                if (looksLikeName(candidate)) return candidate
            }
        }
        return null
    }

    private fun cleanName(raw: String): String =
        raw.replace(TRAILING_AMOUNT, "").replace(Regex("\\s+"), " ").trim(' ', ':', '-', ',', '.')

    private fun looksLikeName(text: String): Boolean {
        if (text.count { it.isLetter() } < 2) return false
        if (text.contains('@')) return false
        if (text.lowercase() in NOT_A_NAME_WORDS) return false
        return !NOT_A_NAME_PREFIX.containsMatchIn(text)
    }

    private fun findTimestamp(lines: List<String>, zone: ZoneId, today: LocalDate): Long? {
        for ((i, line) in lines.withIndex()) {
            val match = DateParsers.findDate(line) ?: continue
            if (match.date.isAfter(today.plusDays(1))) continue
            val remainder = line.removeRange(match.range)
            val time = DateParsers.findTime(remainder)
                ?: lines.getOrNull(i + 1)?.let { DateParsers.findTime(it) }
                ?: LocalTime.NOON
            return LocalDateTime.of(match.date, time).atZone(zone).toInstant().toEpochMilli()
        }
        return null
    }

    private fun findTxnId(lines: List<String>): String? {
        for (label in ID_LABELS) {
            for ((i, line) in lines.withIndex()) {
                val m = label.find(line) ?: continue
                idToken(m.groupValues[1])?.let { return it }
                lines.getOrNull(i + 1)?.let { idToken(it) }?.let { return it }
            }
        }
        return lines.firstNotNullOfOrNull { TWELVE_DIGITS.find(it)?.value }
    }

    private fun idToken(text: String): String? =
        ID_TOKEN.findAll(text).map { it.value }.firstOrNull { token -> token.any { it.isDigit() } }

    private fun findPaidFrom(lines: List<String>): String? {
        for ((i, line) in lines.withIndex()) {
            val m = PAID_FROM.find(line) ?: continue
            val sameLine = stripAmount(m.groupValues[1])
            if (sameLine.isNotBlank()) return sameLine
            val next = lines.getOrNull(i + 1)?.let { stripAmount(it) }
            if (!next.isNullOrBlank()) return next
        }
        return lines.firstOrNull { MASKED_ACCOUNT.containsMatchIn(it) }?.let { stripAmount(it) }
    }

    private fun stripAmount(text: String): String = text.replace(TRAILING_AMOUNT, "").trim()
}
