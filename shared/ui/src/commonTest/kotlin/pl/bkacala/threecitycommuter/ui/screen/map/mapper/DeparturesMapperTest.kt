package pl.bkacala.threecitycommuter.ui.screen.map.mapper

import kotlinx.datetime.Clock
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.rail.RailNetwork
import pl.bkacala.threecitycommuter.model.stops.TransitStopData
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeparturesMapperTest {

    @Test
    fun `does not expose delay label when delay is below one minute`() {
        val model = DeparturesMapper.mapToBottomSheetModel(
            transitStopData = transitStopData(),
            departures = listOf(
                departure(delayInSeconds = 59) to null,
            ),
            selectedDepartureKey = null,
        )

        assertEquals(1, model.departures.size)
        assertNull(model.departures.single().statusLabel)
    }

    @Test
    fun `uses rail network name instead of line number for rail departures`() {
        val model = DeparturesMapper.mapToBottomSheetModel(
            transitStopData = transitStopData().copy(
                stopKey = TransitStopKey(TransitProvider.PLK, 257540),
                railNetwork = RailNetwork.PKM,
            ),
            departures = listOf(departure(delayInSeconds = 0) to null),
            selectedDepartureKey = null,
        )

        assertEquals("PKM", model.departures.single().lineNumber)
    }

    private fun transitStopData(): TransitStopData =
        TransitStopData(
            stopKey = TransitStopKey(TransitProvider.GDANSK, 1234),
            stopCode = "01",
            stopName = "Test Stop",
            stopShortName = "Test Stop",
            stopDesc = null,
            subName = null,
            date = null,
            zoneId = 1,
            zoneName = "A",
            virtual = 0,
            nonpassenger = 0,
            depot = 0,
            ticketZoneBorder = 0,
            onDemand = false,
            activationDate = null,
            stopLat = 54.0,
            stopLon = 18.0,
            stopUrl = null,
            locationType = null,
            parentStation = null,
            stopTimezone = null,
            wheelchairBoarding = null,
            isForBuses = true,
            isForTrams = false,
        )

    private fun departure(delayInSeconds: Int): Departure =
        Departure(
            id = "dep-1",
            delayInSeconds = delayInSeconds,
            estimatedTime = Clock.System.now(),
            headsign = "Test direction",
            lineNumber = "122",
            routeId = 122,
            scheduledTripStartTime = null,
            tripId = 99,
            status = "REALTIME",
            theoreticalTime = Clock.System.now(),
            timestamp = Clock.System.now(),
            trip = 99L,
            vehicleCode = 1,
            vehicleId = 1001L,
            vehicleService = "122",
        )
}
