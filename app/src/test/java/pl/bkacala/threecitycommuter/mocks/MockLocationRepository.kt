package pl.bkacala.threecitycommuter.mocks

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.repository.location.LocationRepository

object MockLocationRepository {
    val mockLocationRepository = object: LocationRepository {
        override fun getLocation(): Flow<UserLocation> = callbackFlow {
            delay(200)
            trySend(UserLocation.default().copy(isFixed = false))
            awaitClose()
        }

    }
}