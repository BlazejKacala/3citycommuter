package pl.bkacala.threecitycommuter.repository.stops

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import pl.bkacala.threecitycommuter.model.stops.StopData

class RealStopsRepository(
    private val ioDispatcher: CoroutineDispatcher,

) : StopsRepository {
    override fun searchContents(searchQuery: String): Flow<List<StopData>> {
        TODO("Not yet implemented")
    }
}