package pl.bkacala.threecitycommuter.database

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

actual fun getDatabaseBuilder(): RoomDatabase.Builder<CommuterDatabase> {
    val dbFile = File(System.getProperty("user.home"), ".3citycommuter/commuter_database.db")
    dbFile.parentFile?.mkdirs()
    return Room.databaseBuilder<CommuterDatabase>(
        name = dbFile.absolutePath,
    )
}
