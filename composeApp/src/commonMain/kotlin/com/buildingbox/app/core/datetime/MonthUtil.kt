package com.buildingbox.app.core.datetime

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/** ISO date today, "YYYY-MM-DD". */
fun today(): String = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()

/** Current month, "YYYY-MM". */
fun currentMonth(): String = today().take(7)

/** "2026-05-12" → "2026-05". */
fun monthOf(date: String): String = date.take(7)

/** Shift a "YYYY-MM" month by [delta] months. */
fun shiftMonth(month: String, delta: Int): String {
    val (y, m) = month.split("-").let { it[0].toInt() to it[1].toInt() }
    val total = y * 12 + (m - 1) + delta
    val ny = total / 12
    val nm = total % 12 + 1
    return "$ny-${nm.toString().padStart(2, '0')}"
}

/** "2026-05" → "May 2026". */
fun formatMonth(month: String): String {
    val parts = month.split("-")
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return month
    val name = MONTH_NAMES.getOrElse(m - 1) { month }
    return "$name ${parts[0]}"
}

/** "2026-05-12" → "Tue, 12 May". */
fun formatDayLong(date: String): String {
    val (y, m, d) = date.split("-").map { it.toInt() }
    val wd = LocalDate(y, m, d).dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    return "$wd, $d ${MONTH_NAMES.getOrElse(m - 1) { "" }.take(3)}"
}

fun daysInMonth(month: String): Int {
    val (y, m) = month.split("-").map { it.toInt() }
    return LocalDate(y, m, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
}

/** Monday-based index (0 = Mon … 6 = Sun) of the first day of [month]. */
fun firstWeekdayIndex(month: String): Int {
    val (y, m) = month.split("-").map { it.toInt() }
    return LocalDate(y, m, 1).dayOfWeek.isoDayNumber - 1
}

/** "YYYY-MM-DD" for a month + day-of-month. */
fun dateOf(month: String, day: Int): String = "$month-${day.toString().padStart(2, '0')}"
