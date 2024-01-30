package pl.bkacala.threecitycommuter.ui.screen.map.mapper

import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.ui.screen.map.component.DepartureRowModel
import pl.bkacala.threecitycommuter.ui.screen.map.component.DepartureRowModel.VehicleType

object DeparturesMapper {

    fun Departure.mapToUiRow() = DepartureRowModel(
        isNear = true,
        vehicleType = if(this.routeId < 100) VehicleType.Tram else VehicleType.Bus,
        departureTime = this.estimatedTime.toString(),
        lineNumber = this.routeId.toString(),
        direction = this.headsign,
        disabledFriendly = true,
        bikesAllowed = false
    )
}