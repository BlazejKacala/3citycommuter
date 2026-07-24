package pl.bkacala.threecitycommuter.utils

import kotlinx.datetime.LocalDateTime

fun LocalDateTime.toddMMyyyyString(): String {
    return buildString {
        append(this@toddMMyyyyString.year)
        append("-")
        if (this@toddMMyyyyString.monthNumber < 10) {
            append(0)
        }
        append(this@toddMMyyyyString.monthNumber)
        append("-")
        if (this@toddMMyyyyString.dayOfMonth < 10) {
            append(0)
        }
        append(this@toddMMyyyyString.dayOfMonth)
    }
}
