package pl.bkacala.threecitycommuter.client

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atDate
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

internal fun String.toPlkRouteInstant(
    operatingDate: String,
    dayOffset: Int,
): Instant {
    val date = LocalDate.parse(operatingDate).plus(DatePeriod(days = dayOffset))
    val normalized = removePrefix("P")
    val parts = normalized.split(":")
    require(parts.size in 2..3) { "Invalid PLK route time" }
    val hours = requireNotNull(parts[0].toIntOrNull()) { "Invalid PLK hour" }
    val minutes = requireNotNull(parts[1].toIntOrNull()) { "Invalid PLK minute" }
    val seconds = if (parts.size == 3) requireNotNull(parts[2].toIntOrNull()) { "Invalid PLK second" } else 0
    require(hours >= 0 && minutes in 0..59 && seconds in 0..59) { "Invalid PLK route time" }
    val adjustedDate = date.plus(DatePeriod(days = hours / 24))
    return adjustedDate.atTime(hours % 24, minutes, seconds).toInstant(TimeZone.of("Europe/Warsaw"))
}

internal fun String.toPlkOperationInstant(): Instant {
    val date = LocalDate.parse(substringBefore('T'))
    val time = LocalTime.parse(substringAfter('T'))
    return time.atDate(date).toInstant(TimeZone.currentSystemDefault())
}

internal fun Instant.toLocalDateInSystemZone(): LocalDate = toLocalDateTime(TimeZone.currentSystemDefault()).date
