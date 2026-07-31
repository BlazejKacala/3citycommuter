package pl.bkacala.threecitycommuter.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import pl.bkacala.threecitycommuter.repository.vehicles.VehiclesRepository

class GetDeparturesUseCase(
    private val busStopsRepository: BusStopsRepository,
    private val vehiclesRepository: VehiclesRepository,
) {
    fun getDepartures(stopKey: TransitStopKey): Flow<List<Pair<Departure, Vehicle?>>> {
        val provider = stopKey.provider
        return busStopsRepository
            .getDepartures(stopKey)
            .combine(vehiclesRepository.getVehicles(provider)) { departures, vehicles ->
                departures.map { departure ->
                    Pair(
                        departure,
                        vehicles.firstOrNull { it.vehicleCode.toInt() == departure.vehicleCode },
                    )
                }
            }
    }
}
