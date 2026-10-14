package pl.bkacala.threecitycommuter.client

import android.content.Context
import java.io.File

internal class FileBackedGdyniaGtfsSnapshotStorage(context: Context) : GdyniaGtfsSnapshotStorage by
AtomicGdyniaGtfsSnapshotStorage(File(context.filesDir, "gdynia_gtfs"))
