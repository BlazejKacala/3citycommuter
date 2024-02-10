package pl.bkacala.threecitycommuter.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import pl.bkacala.threecitycommuter.repository.vehicles.VehiclesRepository
import javax.inject.Inject

class GetDeparturesUseCase @Inject constructor(
    private val busStopsRepository: BusStopsRepository,
    private val vehiclesRepository: VehiclesRepository
) {

    fun getDepartures(stopId: Int) : Flow<List<Pair<Departure, Vehicle?>>> {
        return busStopsRepository
            .getDepartures(stopId)
            .combine(vehiclesRepository.vehicles()) { departures, vehicles ->
                departures.map { departure ->
                    Pair(
                        departure,
                        vehicles.firstOrNull { it.vehicleCode.toInt() == departure.vehicleCode })
                }
            }
    }
}