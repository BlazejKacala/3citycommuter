package pl.bkacala.threecitycommuter.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.repository.stops.BusStopsRepository
import pl.bkacala.threecitycommuter.repository.vehicles.VehiclesRepository
import javax.inject.Inject

class GetDeparturesUseCase @Inject constructor(
    private val busStopsRepository: BusStopsRepository,
    private val vehiclesRepository: VehiclesRepository
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getDepartures(stopId: Int) : Flow<List<Pair<Departure, Vehicle?>>> {
        val flow = busStopsRepository.getDepartures(stopId).flatMapLatest { departures ->
            val flows = departures.map { departure ->
                if(departure.vehicleId == null) {
                    flowOf(Pair<Departure, Vehicle?>(departure, null))
                } else {
                    vehiclesRepository.getVehicle(departure.vehicleId.toInt()).map { vehicle ->
                        Pair<Departure, Vehicle?>(departure, vehicle)
                    }
                }
            }
            combine(flows) { it.toList() }
        }
        return flow
    }
}