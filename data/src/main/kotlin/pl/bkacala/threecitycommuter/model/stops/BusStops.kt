package pl.bkacala.threecitycommuter.model.stops
data class BusStopData(
    val stopId: Int,
    val stopCode: String?,
    val stopName: String?,
    val stopShortName: String?,
    val stopDesc: String?,
    val subName: String?,
    val date: String?,
    val zoneId: Int,
    val zoneName: String?,
    val virtual: Int,
    val nonpassenger: Int,
    val depot: Int,
    val ticketZoneBorder: Int,
    val onDemand: Boolean,
    val activationDate: String?,
    val stopLat: Double,
    val stopLon: Double,
    val stopUrl: String?,
    val locationType: String?,
    val parentStation: String?,
    val stopTimezone: String?,
    val wheelchairBoarding: String?,
    val isForBuses: Boolean,
    val isForTrams: Boolean,
    @Transient
    val name: String = stopName ?: stopShortName ?: stopDesc ?: ""
)