package pl.bkacala.threecitycommuter.ui.screen.map.mapper

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.ui.screen.map.component.DepartureRowModel
import pl.bkacala.threecitycommuter.ui.screen.map.component.DepartureRowModel.VehicleType

object DeparturesMapper {

    private fun minutesToArrival(estimatedTime: Instant, now: Long): Int {

        val secondsToArrival = estimatedTime.epochSeconds - now
        return secondsToArrival.toInt()/60
    }
    fun Departure.mapToUiRow() : DepartureRowModel {

        val now = Clock.System.now().epochSeconds
        val minutesToArrival = minutesToArrival(this.estimatedTime, now)
        return DepartureRowModel(
            isNear = minutesToArrival == 0,
            vehicleType = if (this.routeId < 100) VehicleType.Tram else VehicleType.Bus,
            departureTime = if(minutesToArrival == 0) "teraz" else "$minutesToArrival min",
            lineNumber = this.routeId.toString(),
            direction = this.headsign,
            disabledFriendly = true,
            bikesAllowed = false
        )
    }
}