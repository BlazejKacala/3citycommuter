package pl.bkacala.threecitycommuter.client

import java.io.ByteArrayInputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

internal actual class ZipEntryReader {
    actual fun readEntryLines(zipBytes: ByteArray, entryName: String, onLine: (String) -> Unit): Boolean =
        readEntryLinesInternal(
            openZip = { ZipInputStream(ByteArrayInputStream(zipBytes)) },
            entryName = entryName,
            onLine = onLine,
        )

    actual fun readEntryLines(zipFilePath: String, entryName: String, onLine: (String) -> Unit): Boolean =
        ZipFile(zipFilePath).use { zip ->
            val entry = zip.getEntry(entryName) ?: return false
            zip.getInputStream(entry).bufferedReader().useLines { lines -> lines.forEach(onLine) }
            true
        }

    private fun readEntryLinesInternal(
        openZip: () -> ZipInputStream,
        entryName: String,
        onLine: (String) -> Unit,
    ): Boolean {
        openZip().use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == entryName) {
                    zip.bufferedReader().useLines { lines -> lines.forEach(onLine) }
                    return true
                }
                entry = zip.nextEntry
            }
        }
        return false
    }
}
