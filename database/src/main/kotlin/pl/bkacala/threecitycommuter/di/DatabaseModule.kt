package pl.bkacala.threecitycommuter.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import pl.bkacala.threecitycommuter.dao.BusStopsDao
import pl.bkacala.threecitycommuter.database.CommuterDatabase

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): CommuterDatabase {
        return Room.databaseBuilder(
            context,
            CommuterDatabase::class.java,
            "database",
        ).build()
    }

    @Provides
    fun providesBusStopsDao(commuterDatabase: CommuterDatabase): BusStopsDao {
        return commuterDatabase.busStopsDao
    }
}