package pl.bkacala.threecitycommuter.database

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSHomeDirectory

actual fun getDatabaseBuilder(): RoomDatabase.Builder<CommuterDatabase> {
    val dbFilePath = NSHomeDirectory() + "/commuter_database.db"
    return Room.databaseBuilder<CommuterDatabase>(
        name = dbFilePath,
    )
}
