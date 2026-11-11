package com.paisatrail.domain.util

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Due-date maths for monthly commitments (a due day of 31 falls on the last day of shorter months). */
object DueDates {

    fun nextDue(dueDay: Int, today: LocalDate): LocalDate {
        val day = dueDay.coerceIn(1, 31)
        val thisMonth = YearMonth.from(today)
        val candidate = thisMonth.atDay(minOf(day, thisMonth.lengthOfMonth()))
        if (!candidate.isBefore(today)) return candidate
        val nextMonth = thisMonth.plusMonths(1)
        return nextMonth.atDay(minOf(day, nextMonth.lengthOfMonth()))
    }

    fun daysUntil(dueDay: Int, today: LocalDate): Int =
        ChronoUnit.DAYS.between(today, nextDue(dueDay, today)).toInt()
}
