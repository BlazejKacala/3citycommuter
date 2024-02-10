package pl.bkacala.threecitycommuter.model.vehicles

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(

    val photo: String,
    @PrimaryKey
    val vehicleCode: String,
    val carrirer: String,
    val transportationType: String,
    val vehicleCharacteristics: String,
    val bidirectional: Boolean,
    val historicVehicle: Boolean,
    val length: Double,
    val brand: String,
    val model: String,
    val productionYear: Int,
    val seats: Int,
    val standingPlaces: Int,
    val airConditioning: Boolean,
    val monitoring: Boolean,
    val internalMonitor: Boolean,
    val floorHeight: String,
    val kneelingMechanism: Boolean,
    val wheelchairsRamp: Boolean,
    val usb: Boolean,
    val voiceAnnouncements: Boolean,
    val aed: Boolean,
    val bikeHolders: Int,
    val ticketMachine: Boolean,
    val patron: String,
    val url: String,
    val passengersDoors: Int
)