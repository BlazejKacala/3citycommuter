package pl.bkacala.threecitycommuter.client

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours

/** Converts a GTFS time, including values beyond 24:00, to today's Gdynia time. */
internal fun String.toGdyniaInstantOrNull(): Instant? = runCatching {
    val parts = split(":")
    val hour = parts[0].toInt()
    val minute = parts[1].toInt()
    val second = parts.getOrNull(2)?.toIntOrNull() ?: 0
    val timeZone = TimeZone.currentSystemDefault()
    val date = Clock.System.now().toLocalDateTime(timeZone).date
    LocalDateTime(date, LocalTime(hour % 24, minute, second))
        .toInstant(timeZone)
        .plus((hour / 24).hours)
}.getOrNull()
