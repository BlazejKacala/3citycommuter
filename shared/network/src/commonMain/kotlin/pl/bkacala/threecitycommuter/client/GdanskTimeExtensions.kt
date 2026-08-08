package pl.bkacala.threecitycommuter.client

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

internal fun String.toGdanskInstantOrNull(): Instant? = runCatching {
    val datePart = substringBefore('T')
    val time = substringAfter('T').substringBefore('.').let(LocalTime::parse)
    val timeZone = TimeZone.of("Europe/Warsaw")
    val date = datePart.toLocalDateOrNull()
        ?.takeUnless { it.year == 1899 }
        ?: Clock.System.now().toLocalDateTime(timeZone).date
    LocalDateTime(date, time).toInstant(timeZone)
}.getOrNull()

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
