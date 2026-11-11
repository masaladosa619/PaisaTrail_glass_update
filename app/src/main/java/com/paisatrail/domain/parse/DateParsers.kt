package com.paisatrail.domain.parse

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime

data class DateMatch(val date: LocalDate, val range: IntRange)

/** Finds Indian-style dates (day first) and clock times inside free text. */
object DateParsers {
    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    private val ISO = Regex("""(?<!\d)(\d{4})-(\d{1,2})-(\d{1,2})(?!\d)""")
    private val NUMERIC = Regex("""(?<!\d)(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4}|\d{2})(?!\d)""")
    private val DAY_MONTH_NAME =
        Regex("""(?<!\d)(\d{1,2})(?:st|nd|rd|th)?[\s\-/]+([A-Za-z]{3,9})\.?,?[\s\-/]+(\d{4}|\d{2})(?!\d)""")
    private val MONTH_NAME_DAY =
        Regex("""(?<![A-Za-z])([A-Za-z]{3,9})\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})(?!\d)""")
    private val TIME = Regex("""(?<![\d:])(\d{1,2}):(\d{2})(?::\d{2})?(?:\s*([AaPp])\.?\s?[Mm])?""")

    /** Returns the earliest valid date found in [text], or null. */
    fun findDate(text: String): DateMatch? {
        val found = ArrayList<DateMatch>()
        for (m in ISO.findAll(text)) {
            val g = m.groupValues
            build(g[1].toInt(), g[2].toInt(), g[3].toInt())?.let { found.add(DateMatch(it, m.range)) }
        }
        for (m in NUMERIC.findAll(text)) {
            val g = m.groupValues
            build(year(g[3]), g[2].toInt(), g[1].toInt())?.let { found.add(DateMatch(it, m.range)) }
        }
        for (m in DAY_MONTH_NAME.findAll(text)) {
            val g = m.groupValues
            val month = month(g[2]) ?: continue
            build(year(g[3]), month, g[1].toInt())?.let { found.add(DateMatch(it, m.range)) }
        }
        for (m in MONTH_NAME_DAY.findAll(text)) {
            val g = m.groupValues
            val month = month(g[1]) ?: continue
            build(g[3].toInt(), month, g[2].toInt())?.let { found.add(DateMatch(it, m.range)) }
        }
        return found.minByOrNull { it.range.first }
    }

    /** Returns the first clock time in [text] ("3:18 PM", "15:18", "03:18:22 pm"), or null. */
    fun findTime(text: String): LocalTime? {
        for (m in TIME.findAll(text)) {
            var hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toInt()
            val meridiem = m.groupValues[3].lowercase()
            if (minute > 59) continue
            if (meridiem == "p" && hour < 12) hour += 12
            if (meridiem == "a" && hour == 12) hour = 0
            if (hour > 23) continue
            return LocalTime.of(hour, minute)
        }
        return null
    }

    private fun month(name: String): Int? = MONTHS[name.take(3).lowercase()]

    private fun year(raw: String): Int = if (raw.length == 2) 2000 + raw.toInt() else raw.toInt()

    private fun build(year: Int, month: Int, day: Int): LocalDate? {
        if (year !in 1990..2100 || month !in 1..12 || day !in 1..31) return null
        return try {
            LocalDate.of(year, month, day)
        } catch (e: DateTimeException) {
            null
        }
    }
}
