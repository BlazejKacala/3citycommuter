package pl.bkacala.threecitycommuter.ui.screen.map.mapper

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.model.transit.supportsLiveVehicleTracking
import pl.bkacala.threecitycommuter.model.vehicles.Vehicle
import pl.bkacala.threecitycommuter.ui.screen.map.component.DepartureRowModel
import pl.bkacala.threecitycommuter.ui.screen.map.component.DeparturesBottomSheetModel
import pl.bkacala.threecitycommuter.ui.screen.map.component.DeparturesHeaderModel
import pl.bkacala.threecitycommuter.ui.screen.map.component.VehicleType

object DeparturesMapper {
    private fun minutesToArrival(
        estimatedTime: Instant?,
        now: Long,
    ): Int {
        if (estimatedTime == null) {
            return -1
        }
        val secondsToArrival = estimatedTime.epochSeconds - now
        return secondsToArrival.toInt() / 60
    }

    fun mapToBottomSheetModel(
        busStopData: BusStopData,
        departures: List<Pair<Departure, Vehicle?>>,
        selectedDepartureKey: String?,
    ): DeparturesBottomSheetModel {
        return DeparturesBottomSheetModel(
            provider = busStopData.provider,
            header = DeparturesHeaderModel(
                busStopName = busStopData.name,
                isForDemand = busStopData.onDemand,
            ),
            departures = departures.map {
                val (departure, vehicle) = it
                departure.mapToUiRow(vehicle, selectedDepartureKey, busStopData.provider.supportsLiveVehicleTracking)
            },
        )
    }

    private fun Departure.mapToUiRow(
        vehicle: Vehicle?,
        selectedDepartureKey: String?,
        supportsLiveTracking: Boolean,
    ): DepartureRowModel {
        val now = Clock.System.now().epochSeconds
        val minutesToArrival = minutesToArrival(this.estimatedTime, now)
        val departureKey = selectionKey()
        val showGpsIndicator = supportsLiveTracking
        return DepartureRowModel(
            departureKey = departureKey,
            isNear = minutesToArrival == 0,
            vehicleType = if (this.routeId < 100) VehicleType.Tram else VehicleType.Bus,
            departureTime = departureTime(minutesToArrival),
            lineNumber = this.lineNumber,
            direction = this.headsign ?: "",
            disabledFriendly = vehicle?.wheelchairsRamp ?: false,
            bikesAllowed = vehicle?.bikeHolders == 1,
            showGpsIndicator = showGpsIndicator,
            gpsPosition = showGpsIndicator && this.vehicleId != null,
            isSelected = departureKey == selectedDepartureKey,
            vehicleId = this.vehicleId,
            routeId = this.routeId,
            tripId = this.tripId,
        )
    }

    private fun Departure.selectionKey(): String =
        listOf(
            id,
            routeId.toString(),
            tripId.toString(),
            vehicleId?.toString().orEmpty(),
            theoreticalTime?.toString().orEmpty(),
            estimatedTime?.toString().orEmpty(),
            headsign.orEmpty(),
        ).joinToString("|")

    private fun departureTime(minutesToArrival: Int) =
        if (minutesToArrival == -1) {
            "brak danych"
        } else if (minutesToArrival == 0) {
            "teraz"
        } else {
            "$minutesToArrival min"
        }
}
