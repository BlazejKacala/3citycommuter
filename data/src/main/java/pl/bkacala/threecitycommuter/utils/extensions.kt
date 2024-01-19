package pl.bkacala.threecitycommuter.utils

import kotlinx.datetime.Clock

private const val SECONDS_IN_DAY = 86400

fun Long.isOlderThenOneDay() = Clock.System.now().epochSeconds - this > 86400