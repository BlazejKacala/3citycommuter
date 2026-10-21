package pl.bkacala.threecitycommuter.ui.screen.schedule.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import pl.bkacala.threecitycommuter.model.schedule.LineTimetable
import pl.bkacala.threecitycommuter.model.schedule.TimetableJourney
import pl.bkacala.threecitycommuter.model.schedule.TimetableLine
import pl.bkacala.threecitycommuter.model.schedule.TimetableStopTime
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleMappersTest {
    @Test
    fun providerMainVariantNamesDirectionEvenWhenSchoolTripsAreMoreFrequent() {
        val main = journey("main", "outbound", "Centrum", listOf(1, 2, 4)).copy(isMainVariant = true)
        val school = journey("school", "outbound", "Centrum przez szkole", listOf(1, 2, 3, 4))
        val result = timetable(listOf(main, school, school.copy(id = "school-2")))

        assertEquals("Centrum", result.toScheduleLine().directions.single().headsign)
        assertEquals("Centrum przez szkole", result.toScheduleTrips("outbound", stopId(3)).first().headsign)
    }

    @Test
    fun sameProviderDirectionCollapsesVariantsAndKeepsSelectedTripRoute() {
        val journeys = listOf(
            journey("main-1", "outbound", "Centrum", listOf(1, 2, 4, 5)),
            journey("main-2", "outbound", "Centrum", listOf(1, 2, 4, 5)),
            journey("school", "outbound", "Szkoła", listOf(1, 2, 3, 4, 5, 6)),
            journey("depot", "outbound", "Zajezdnia", listOf(1, 2, 7)),
            journey("return", "inbound", "Pogórze", listOf(8, 5, 4, 2, 1)),
        )

        val timetable = timetable(journeys)
        val line = timetable.toScheduleLine()
        val outbound = line.directions.single { it.id == "outbound" }
        val schoolStop = TransitStopKey(TransitProvider.GDYNIA, 3).toScheduleId()
        val depotStop = TransitStopKey(TransitProvider.GDYNIA, 7).toScheduleId()

        assertEquals(2, line.directions.size)
        assertEquals("Centrum", outbound.headsign)
        val mainRoute = listOf(1, 2, 4, 5).map(::stopId)
        assertEquals(true, mainRoute.zipWithNext().all { (first, second) -> outbound.stopIds.indexOf(first) < outbound.stopIds.indexOf(second) })
        assertEquals(true, schoolStop in outbound.stopIds)
        assertEquals(true, depotStop in outbound.stopIds)

        val schoolTrip = timetable.toScheduleTrips("outbound", schoolStop).single()
        assertEquals("school", schoolTrip.sourceTripId)
        assertEquals("Szkoła", schoolTrip.headsign)
        assertEquals(listOf(1, 2, 3, 4, 5, 6).map(::stopId), schoolTrip.stopTimes.map { it.stopId })
    }

    @Test
    fun tiedHeadsignsChooseTheLongestRouteDeterministically() {
        val journeys = listOf(
            journey("short-a", "outbound", "Rynek", listOf(1, 2, 3)),
            journey("short-b", "outbound", "Rynek", listOf(1, 2, 3)),
            journey("long-a", "outbound", "Dworzec", listOf(1, 2, 4, 5, 6)),
            journey("long-b", "outbound", "Dworzec", listOf(1, 2, 4, 5, 6)),
        )

        val direction = timetable(journeys).toScheduleLine().directions.single()

        assertEquals("Dworzec", direction.headsign)
    }

    private fun timetable(journeys: List<TimetableJourney>) = LineTimetable(
        line = TimetableLine(
            id = "GDYNIA:156",
            provider = TransitProvider.GDYNIA,
            sourceId = "156",
            number = "156",
            name = "Linia 156",
        ),
        date = LocalDate(2026, 10, 21),
        journeys = journeys,
    )

    private fun journey(id: String, directionId: String, headsign: String, stopIds: List<Int>) = TimetableJourney(
        id = id,
        lineId = "GDYNIA:156",
        routeId = 156,
        tripId = stopIds.first(),
        directionId = directionId,
        headsign = headsign,
        stops = stopIds.mapIndexed { index, sourceStopId ->
            TimetableStopTime(
                stopKey = TransitStopKey(TransitProvider.GDYNIA, sourceStopId),
                sequence = index,
                arrivalTime = Instant.parse("2026-10-21T08:${index.toString().padStart(2, '0')}:00Z"),
                departureTime = Instant.parse("2026-10-21T08:${index.toString().padStart(2, '0')}:00Z"),
            )
        },
    )

    private fun stopId(sourceStopId: Int) = TransitStopKey(TransitProvider.GDYNIA, sourceStopId).toScheduleId()
}
