package pl.bkacala.threecitycommuter.client

internal expect class ZipEntryReader() {
    fun readEntry(zipBytes: ByteArray, entryName: String): String?
}
