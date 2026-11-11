package com.paisatrail.domain.util

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.roundToLong

/** All money in the app is stored as paise (Long) so sums never drift. */
object Money {
    const val RUPEE = "₹"

    private val CURRENCY_WORDS = Regex("(?i)rs\\.?|inr|₹")
    private val NOT_NUMERIC = Regex("[^0-9.]")

    /** "100000" -> "1,00,000" (Indian digit grouping). */
    fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val parts = ArrayList<String>()
        var end = rest.length
        while (end > 0) {
            val start = maxOf(0, end - 2)
            parts.add(rest.substring(start, end))
            end = start
        }
        return parts.reversed().joinToString(",") + "," + last3
    }

    /** 10000050 -> "₹1,00,000.50". Paise are shown only when they are non-zero unless forced. */
    fun format(paise: Long, showPaise: Boolean = paise % 100L != 0L): String {
        val absPaise = abs(paise)
        val body = groupIndian((absPaise / 100).toString())
        val fraction = if (showPaise) "." + (absPaise % 100).toString().padStart(2, '0') else ""
        return (if (paise < 0) "-" else "") + RUPEE + body + fraction
    }

    /** Formats a rupee amount (calculator results), rounded to the nearest rupee. */
    fun formatRupees(rupees: Double): String = format(rupees.roundToLong() * 100, showPaise = false)

    /** "₹1,23,456.78" / "Rs. 749" / "749" -> paise. Returns null when it is not a number. */
    fun parseToPaise(input: String): Long? {
        val cleaned = input.replace(CURRENCY_WORDS, "").replace(",", "").replace(NOT_NUMERIC, "")
        if (cleaned.isEmpty() || cleaned == "." || cleaned.count { it == '.' } > 1) return null
        return try {
            BigDecimal(cleaned).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Plain editable text for input fields: 74900 -> "749", 74950 -> "749.50". */
    fun toInput(paise: Long): String {
        val rupees = abs(paise) / 100
        val rem = abs(paise) % 100
        return if (rem == 0L) rupees.toString() else rupees.toString() + "." + rem.toString().padStart(2, '0')
    }
}
