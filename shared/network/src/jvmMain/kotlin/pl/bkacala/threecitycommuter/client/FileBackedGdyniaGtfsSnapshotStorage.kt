package pl.bkacala.threecitycommuter.client

import java.io.File

internal class FileBackedGdyniaGtfsSnapshotStorage : GdyniaGtfsSnapshotStorage by
AtomicGdyniaGtfsSnapshotStorage(File(resolveBaseDirectory(), "gdynia_gtfs"))

private fun resolveBaseDirectory(): File {
    val localAppData = System.getenv("LOCALAPPDATA")
    return if (!localAppData.isNullOrBlank()) {
        File(localAppData, "3citycommuter")
    } else {
        File(System.getProperty("user.home"), ".3citycommuter")
    }
}
