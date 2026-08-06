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
    val hours = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minutes = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val seconds = parts.getOrNull(2)?.toIntOrNull() ?: 0
    val adjustedDate = date.plus(DatePeriod(days = hours / 24))
    return adjustedDate.atTime(hours % 24, minutes, seconds).toInstant(TimeZone.currentSystemDefault())
}

internal fun String.toPlkOperationInstant(): Instant {
    val date = LocalDate.parse(substringBefore('T'))
    val time = LocalTime.parse(substringAfter('T'))
    return time.atDate(date).toInstant(TimeZone.currentSystemDefault())
}

internal fun Instant.toLocalDateInSystemZone(): LocalDate = toLocalDateTime(TimeZone.currentSystemDefault()).date
