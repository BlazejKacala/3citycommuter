package pl.bkacala.threecitycommuter.client

internal actual class ZipEntryReader {
    actual fun readEntryLines(zipBytes: ByteArray, entryName: String, onLine: (String) -> Unit): Boolean = false

    actual fun readEntryLines(zipFilePath: String, entryName: String, onLine: (String) -> Unit): Boolean = false
}
