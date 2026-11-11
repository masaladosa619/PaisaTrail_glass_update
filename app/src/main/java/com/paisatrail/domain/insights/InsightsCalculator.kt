package com.paisatrail.domain.insights

import com.paisatrail.domain.util.Money
import java.time.LocalDate
import java.time.YearMonth

enum class Pacing { UNDER, OVER, UNKNOWN }

data class Insights(
    val dailyAveragePaise: Long,
    val projectedPaise: Long,
    val daysElapsed: Int,
    val daysInMonth: Int,
    val pacing: Pacing,
    val summary: String,
    val pacingMessage: String
)

/** Plain-maths spending insights. Everything is computed on the device. */
object InsightsCalculator {

    fun compute(
        spentPaise: Long,
        month: YearMonth,
        today: LocalDate,
        incomePaise: Long,
        budgetPaise: Long
    ): Insights {
        val daysInMonth = month.lengthOfMonth()
        val current = YearMonth.from(today)
        val daysElapsed = when {
            month == current -> today.dayOfMonth
            month.isBefore(current) -> daysInMonth
            else -> 0
        }
        val dailyAverage = if (daysElapsed > 0) spentPaise / daysElapsed else 0L
        val projected = if (daysElapsed > 0) dailyAverage * daysInMonth else 0L

        val limit = if (budgetPaise > 0) budgetPaise else incomePaise
        val limitName = if (budgetPaise > 0) "budget" else "income"

        val summary = when {
            daysElapsed == 0 -> "This month hasn't started yet."
            spentPaise == 0L -> "Nothing logged for this month yet."
            month == current ->
                "${Money.format(spentPaise)} spent so far, about ${Money.format(dailyAverage, false)} a day " +
                    "over $daysElapsed of $daysInMonth days. Keep this up and the month closes near " +
                    "${Money.format(projected, false)}."
            else ->
                "${Money.format(spentPaise)} spent across $daysInMonth days, about " +
                    "${Money.format(dailyAverage, false)} a day."
        }

        if (limit <= 0 || daysElapsed == 0) {
            return Insights(
                dailyAverage, projected, daysElapsed, daysInMonth, Pacing.UNKNOWN, summary,
                "Add a monthly budget or income in Planner to see how you're pacing."
            )
        }

        val pacing = if (projected <= limit) Pacing.UNDER else Pacing.OVER
        val remainingDays = daysInMonth - daysElapsed
        val message = when {
            month != current && spentPaise <= limit ->
                "You closed the month ${Money.format(limit - spentPaise, false)} inside your $limitName."
            month != current ->
                "You closed the month ${Money.format(spentPaise - limit, false)} over your $limitName."
            pacing == Pacing.UNDER ->
                "On track for your $limitName. Keep daily spend around ${Money.format(dailyAverage, false)}."
            spentPaise >= limit ->
                "You're already ${Money.format(spentPaise - limit, false)} past your $limitName this month."
            remainingDays <= 0 ->
                "Heading ${Money.format(projected - limit, false)} over your $limitName."
            else ->
                "Heading ${Money.format(projected - limit, false)} over your $limitName. Try " +
                    "${Money.format((limit - spentPaise) / remainingDays, false)} a day for the rest of the month."
        }
        return Insights(dailyAverage, projected, daysElapsed, daysInMonth, pacing, summary, message)
    }
}
