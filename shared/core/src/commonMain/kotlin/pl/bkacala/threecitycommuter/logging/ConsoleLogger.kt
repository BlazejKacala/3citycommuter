package pl.bkacala.threecitycommuter.logging

fun logError(
    tag: String,
    message: String,
    throwable: Throwable? = null,
) {
    println("ERROR [$tag] $message")
    throwable?.let { error ->
        println("ERROR [$tag] ${error::class.simpleName}: ${error.message ?: "<no message>"}")
        var cause = error.cause
        while (cause != null) {
            println("ERROR [$tag] caused by ${cause::class.simpleName}: ${cause.message ?: "<no message>"}")
            cause = cause.cause
        }
    }
}
