package pl.bkacala.threecitycommuter.repository.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import pl.bkacala.threecitycommuter.model.location.UserLocation

internal class IosLocationRepository : LocationRepository {
    // TODO: Implement with CLLocationManager + callbackFlow
    override fun getLocation(): Flow<UserLocation> = flowOf(UserLocation.default())
}
