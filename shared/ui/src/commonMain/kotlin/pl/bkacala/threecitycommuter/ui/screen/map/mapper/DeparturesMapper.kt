package pl.bkacala.threecitycommuter.ui.screen.map.mapper

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.rail.RailNetwork
import pl.bkacala.threecitycommuter.model.stops.TransitStopData
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
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
        transitStopData: TransitStopData,
        departures: List<Pair<Departure, Vehicle?>>,
        selectedDepartureKey: String?,
    ): DeparturesBottomSheetModel {
        return DeparturesBottomSheetModel(
            provider = transitStopData.provider,
            header = DeparturesHeaderModel(
                transitStopName = transitStopData.name,
                isForDemand = transitStopData.onDemand,
            ),
            departures = departures.map {
                val (departure, vehicle) = it
                departure.mapToUiRow(
                    vehicle = vehicle,
                    selectedDepartureKey = selectedDepartureKey,
                    supportsLiveTracking = transitStopData.provider.supportsLiveVehicleTracking,
                    provider = transitStopData.provider,
                    railNetwork = transitStopData.railNetwork,
                )
            },
        )
    }

    private fun Departure.mapToUiRow(
        vehicle: Vehicle?,
        selectedDepartureKey: String?,
        supportsLiveTracking: Boolean,
        provider: TransitProvider,
        railNetwork: RailNetwork?,
    ): DepartureRowModel {
        val now = Clock.System.now().epochSeconds
        val minutesToArrival = minutesToArrival(this.estimatedTime, now)
        val departureKey = selectionKey()
        val showGpsIndicator = supportsLiveTracking
        return DepartureRowModel(
            departureKey = departureKey,
            isNear = minutesToArrival == 0,
            vehicleType = vehicleType(provider),
            departureTime = departureTime(minutesToArrival),
            lineNumber = railNetwork?.displayName ?: this.lineNumber,
            direction = this.headsign ?: "",
            disabledFriendly = vehicle?.wheelchairsRamp ?: false,
            bikesAllowed = vehicle?.bikeHolders == 1,
            showGpsIndicator = showGpsIndicator,
            gpsPosition = showGpsIndicator && this.vehicleId != null,
            isSelected = departureKey == selectedDepartureKey,
            vehicleId = this.vehicleId,
            routeId = this.routeId,
            tripId = this.tripId,
            statusLabel = statusLabel(),
        )
    }

    private fun Departure.vehicleType(provider: TransitProvider): VehicleType =
        when (provider) {
            TransitProvider.PLK -> VehicleType.Train
            TransitProvider.GDANSK, TransitProvider.GDYNIA -> if (this.routeId < 100) VehicleType.Tram else VehicleType.Bus
        }

    private fun Departure.statusLabel(): String? {
        val delay = delayInSeconds?.takeIf { it != 0 } ?: return null
        val minutes = kotlin.math.abs(delay) / 60
        if (minutes == 0) {
            return null
        }
        return if (delay > 0) {
            "opóźnienie $minutes min"
        } else {
            "przyśpieszenie $minutes min"
        }
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

    private val RailNetwork.displayName: String
        get() = name.uppercase()
}
