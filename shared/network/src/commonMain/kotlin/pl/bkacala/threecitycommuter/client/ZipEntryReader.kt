package pl.bkacala.threecitycommuter.client

internal expect class ZipEntryReader() {
    fun readEntryLines(zipBytes: ByteArray, entryName: String, onLine: (String) -> Unit): Boolean

    fun readEntryLines(zipFilePath: String, entryName: String, onLine: (String) -> Unit): Boolean
}
