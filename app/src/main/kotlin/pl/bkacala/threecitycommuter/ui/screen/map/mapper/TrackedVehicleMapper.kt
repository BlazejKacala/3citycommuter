package pl.bkacala.threecitycommuter.ui.screen.map.mapper

import com.google.android.gms.maps.model.LatLng
import pl.bkacala.threecitycommuter.model.vehicles.VehiclePosition
import pl.bkacala.threecitycommuter.ui.screen.map.component.DepartureRowModel
import pl.bkacala.threecitycommuter.ui.screen.map.component.GpsQuality
import pl.bkacala.threecitycommuter.ui.screen.map.component.TrackedVehicle


object TrackedVehicleMapper {

    fun mapToTrackedVehicle(
        vehiclePosition: VehiclePosition,
        departure: DepartureRowModel
    ) : TrackedVehicle {
        return TrackedVehicle(
            type = departure.vehicleType,
            delay = vehiclePosition.delay.toString(),
            position = LatLng(vehiclePosition.lat, vehiclePosition.lon),
            number = departure.lineNumber,
            gpsQuality = getGpsQuality(vehiclePosition.gpsQuality)

        )
    }

    private fun getGpsQuality(gpsQuality: Int?): GpsQuality {
        return when(gpsQuality) {
            3,4 -> GpsQuality.Strong
            2 -> GpsQuality.Weak
            else -> GpsQuality.NoSignal
        }
    }
}
