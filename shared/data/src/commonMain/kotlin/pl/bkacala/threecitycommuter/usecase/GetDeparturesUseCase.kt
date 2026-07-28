package pl.bkacala.threecitycommuter.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.transit.TransitStopId
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import pl.bkacala.threecitycommuter.repository.vehicles.VehiclesRepository

class GetDeparturesUseCase(
    private val busStopsRepository: BusStopsRepository,
    private val vehiclesRepository: VehiclesRepository,
) {
    fun getDepartures(stopId: Int): Flow<List<Pair<Departure, Vehicle?>>> {
        val provider = TransitStopId.providerOf(stopId)
        return busStopsRepository
            .getDepartures(stopId)
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
