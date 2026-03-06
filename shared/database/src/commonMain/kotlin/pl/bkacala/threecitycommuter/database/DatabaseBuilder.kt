package pl.bkacala.threecitycommuter.database

import androidx.room.RoomDatabase

expect fun getDatabaseBuilder(): RoomDatabase.Builder<CommuterDatabase>
