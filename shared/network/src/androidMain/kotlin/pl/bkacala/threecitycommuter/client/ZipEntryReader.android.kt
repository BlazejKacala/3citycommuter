package pl.bkacala.threecitycommuter.client

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

internal actual class ZipEntryReader {
    actual fun readEntry(zipBytes: ByteArray, entryName: String): String? {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == entryName) {
                    return zip.bufferedReader().use { it.readText() }
                }
                entry = zip.nextEntry
            }
        }
        return null
    }
}
