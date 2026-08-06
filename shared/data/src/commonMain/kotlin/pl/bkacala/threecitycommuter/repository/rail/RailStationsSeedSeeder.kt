package pl.bkacala.threecitycommuter.repository.rail

import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.dao.RailStationsDao
import pl.bkacala.threecitycommuter.model.rail.RailStationEntity
import pl.bkacala.threecitycommuter.resource.loadRailStationsSeed

class RailStationsSeedSeeder(
    private val railStationsDao: RailStationsDao,
    private val json: Json,
) {

    suspend fun seedIfEmpty() {
        if (railStationsDao.count() > 0) {
            return
        }

        railStationsDao.upsertRailStations(
            loadRailStationsSeed(json).map { station ->
                RailStationEntity(
                    plkStationId = station.plkStationId,
                    name = station.name,
                    latitude = station.latitude,
                    longitude = station.longitude,
                    network = station.network.name,
                    isActive = station.isActive,
                )
            },
        )
    }
}
