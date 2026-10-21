package pl.bkacala.threecitycommuter.ui.screen.schedule.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class ServiceDay(val label: String, val intervalMinutes: Int) {
    WEEKDAY("Dzień roboczy", 15),
    SATURDAY("Sobota", 20),
    SUNDAY("Niedziela", 30),
    HOLIDAY("Święto", 30),
}

/** Polish calendar labels, including movable holidays. Service validity comes from provider data. */
fun LocalDate.serviceDay(): ServiceDay {
    val fixedHolidays = setOf(1 to 1, 1 to 6, 5 to 1, 5 to 3, 8 to 15, 11 to 1, 11 to 11, 12 to 25, 12 to 26)
    val easter = easterSunday(year)
    val movableHolidays = listOf(0, 1, 49, 60).map { easter + DatePeriod(days = it) }
    val isChristmasEveHoliday = year >= 2025 && monthNumber == 12 && dayOfMonth == 24
    return when {
        monthNumber to dayOfMonth in fixedHolidays || this in movableHolidays || isChristmasEveHoliday -> ServiceDay.HOLIDAY
        dayOfWeek == DayOfWeek.SATURDAY -> ServiceDay.SATURDAY
        dayOfWeek == DayOfWeek.SUNDAY -> ServiceDay.SUNDAY
        else -> ServiceDay.WEEKDAY
    }
}

// Gregorian computus; kept independent of the UI and device locale.
private fun easterSunday(year: Int): LocalDate {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val value = h + l - 7 * m + 114
    return LocalDate(year, value / 31, value % 31 + 1)
}
