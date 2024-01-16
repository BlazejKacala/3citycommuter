package pl.bkacala.threecitycommuter.repository.stops

import kotlinx.coroutines.flow.Flow
import pl.bkacala.threecitycommuter.model.stops.StopData

interface StopsRepository {
    suspend fun getBusStops(): List<StopData>
}