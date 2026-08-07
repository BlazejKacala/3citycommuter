package pl.bkacala.threecitycommuter.repository.rail

import pl.bkacala.threecitycommuter.dao.RailStationsDao
import pl.bkacala.threecitycommuter.model.rail.RailNetwork
import pl.bkacala.threecitycommuter.model.rail.RailStationCatalog
import pl.bkacala.threecitycommuter.model.rail.RailStationSeed

class DatabaseRailStationCatalog(
    private val railStationsDao: RailStationsDao,
) : RailStationCatalog {
    override suspend fun getActiveStations(): List<RailStationSeed> =
        railStationsDao.getActiveRailStations().mapNotNull { station ->
            val network = runCatching { RailNetwork.valueOf(station.network) }.getOrNull()
                ?: return@mapNotNull null
            RailStationSeed(
                plkStationId = station.plkStationId,
                name = station.name,
                latitude = station.latitude,
                longitude = station.longitude,
                network = network,
                isActive = station.isActive,
            )
        }
}
