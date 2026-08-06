package pl.bkacala.threecitycommuter.utils

import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

fun Long.isOlderThan(duration: Duration): Boolean =
    Clock.System.now().epochSeconds - this > duration.inWholeSeconds

fun Long.isOlderThenOneDay(): Boolean = isOlderThan(1.days)
