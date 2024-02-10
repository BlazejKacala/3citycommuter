package pl.bkacala.threecitycommuter.ui.screen.map.mapper

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.ui.screen.map.component.DepartureRowModel
import pl.bkacala.threecitycommuter.ui.screen.map.component.DepartureRowModel.VehicleType
import pl.bkacala.threecitycommuter.ui.screen.map.component.DeparturesBottomSheetModel
import pl.bkacala.threecitycommuter.ui.screen.map.component.DeparturesHeaderModel

object DeparturesMapper {
    private fun minutesToArrival(
        estimatedTime: Instant,
        now: Long,
    ): Int {
        val secondsToArrival = estimatedTime.epochSeconds - now
        return secondsToArrival.toInt() / 60
    }

    fun mapToBottomSheetModel(
        busStopData: BusStopData,
        departures: List<Pair<Departure, Vehicle?>>
    ): DeparturesBottomSheetModel {
        return DeparturesBottomSheetModel(
            header = DeparturesHeaderModel(
                busStopName = busStopData.name,
                isForDemand = busStopData.onDemand
            ),
            departures = departures.map {
                val (departure, vehicle) = it
                departure.mapToUiRow(vehicle)
            }
        )
    }

    fun Departure.mapToUiRow(vehicle: Vehicle?): DepartureRowModel {

        val now = Clock.System.now().epochSeconds
        val minutesToArrival = minutesToArrival(this.estimatedTime, now)
        return DepartureRowModel(
            isNear = minutesToArrival == 0,
            vehicleType = if (this.routeId < 100) VehicleType.Tram else VehicleType.Bus,
            departureTime = if (minutesToArrival == 0) "teraz" else "$minutesToArrival min",
            lineNumber = this.routeId.toString(),
            direction = this.headsign,
            disabledFriendly = vehicle?.wheelchairsRamp ?: false,
            bikesAllowed = vehicle?.bikeHolders == 1,
            gpsPosition = this.delayInSeconds != null,
        )
    }
}
