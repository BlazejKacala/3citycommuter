package pl.bkacala.threecitycommuter.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

private lateinit var appContext: Context

fun initDatabaseContext(context: Context) {
    appContext = context.applicationContext
}

actual fun getDatabaseBuilder(): RoomDatabase.Builder<CommuterDatabase> {
    val dbFile = appContext.getDatabasePath("commuter_database.db")
    return Room.databaseBuilder<CommuterDatabase>(
        context = appContext,
        name = dbFile.absolutePath
    )
}
