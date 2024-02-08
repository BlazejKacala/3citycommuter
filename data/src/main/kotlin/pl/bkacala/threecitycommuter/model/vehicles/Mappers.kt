package pl.bkacala.threecitycommuter.model.vehicles

import pl.bkacala.threecitycommuter.model.VehicleNetworkData

fun VehicleNetworkData.toVehicleEntity(): VehicleEntity {
    return VehicleEntity(
        photo = this.photo,
        vehicleCode = this.vehicleCode,
        carrirer = this.carrirer,
        transportationType = this.transportationType,
        vehicleCharacteristics = this.vehicleCharacteristics,
        bidirectional = this.bidirectional,
        historicVehicle = this.historicVehicle,
        length = this.length,
        brand = this.brand,
        model = this.model,
        productionYear = this.productionYear,
        seats = this.seats,
        standingPlaces = this.standingPlaces,
        airConditioning = this.airConditioning,
        monitoring = this.monitoring,
        internalMonitor = this.internalMonitor,
        floorHeight = this.floorHeight,
        kneelingMechanism = this.kneelingMechanism,
        wheelchairsRamp = this.wheelchairsRamp,
        usb = this.usb,
        voiceAnnouncements = this.voiceAnnouncements,
        aed = this.aed,
        bikeHolders = this.bikeHolders,
        ticketMachine = this.ticketMachine,
        patron = this.patron,
        url = this.url,
        passengersDoors = this.passengersDoors
    )
}

fun VehicleEntity.toVehicle(): Vehicle {
    return Vehicle(
        photo = this.photo,
        vehicleCode = this.vehicleCode,
        carrirer = this.carrirer,
        transportationType = this.transportationType,
        vehicleCharacteristics = this.vehicleCharacteristics,
        bidirectional = this.bidirectional,
        historicVehicle = this.historicVehicle,
        length = this.length,
        brand = this.brand,
        model = this.model,
        productionYear = this.productionYear,
        seats = this.seats,
        standingPlaces = this.standingPlaces,
        airConditioning = this.airConditioning,
        monitoring = this.monitoring,
        internalMonitor = this.internalMonitor,
        floorHeight = this.floorHeight,
        kneelingMechanism = this.kneelingMechanism,
        wheelchairsRamp = this.wheelchairsRamp,
        usb = this.usb,
        voiceAnnouncements = this.voiceAnnouncements,
        aed = this.aed,
        bikeHolders = this.bikeHolders,
        ticketMachine = this.ticketMachine,
        patron = this.patron,
        url = this.url,
        passengersDoors = this.passengersDoors
    )
}