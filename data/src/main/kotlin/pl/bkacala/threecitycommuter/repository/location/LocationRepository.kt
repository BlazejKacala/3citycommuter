package pl.bkacala.threecitycommuter.repository.location

import kotlinx.coroutines.flow.Flow
import pl.bkacala.threecitycommuter.model.location.UserLocation

interface LocationRepository {
    fun getLocation(): Flow<UserLocation>
}