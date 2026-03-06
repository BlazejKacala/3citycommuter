package pl.bkacala.threecitycommuter.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VehiclesNetworkData(
    val results: List<VehicleNetworkData>
)

@Serializable
data class VehicleNetworkData(
    val photo: String,
    @SerialName("vehicleCode") val vehicleCode: String,
    val carrirer: String,
    @SerialName("transportationType") val transportationType: String,
    @SerialName("vehicleCharacteristics") val vehicleCharacteristics: String,
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
