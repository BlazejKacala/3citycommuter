package pl.bkacala.threecitycommuter.repository.schedule

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import pl.bkacala.threecitycommuter.client.TimetableDataSource
import pl.bkacala.threecitycommuter.model.schedule.LineTimetable
import pl.bkacala.threecitycommuter.model.schedule.TimetableJourney
import pl.bkacala.threecitycommuter.model.schedule.TimetableLine
import pl.bkacala.threecitycommuter.model.schedule.TimetableStopTime
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RealScheduleRepositoryTest {
    @Test
    fun `repository delegates provider line and dated station timetable requests`() = runTest {
        val stop = TransitStopKey(TransitProvider.PLK, 9001)
        val line = TimetableLine("PLK:SKMT", TransitProvider.PLK, "SKMT", "SKM", "SKM", listOf(stop))
        val date = LocalDate(2026, 10, 20)
        val timetable = LineTimetable(
            line = line,
            date = date,
            journeys = listOf(
                TimetableJourney(
                    id = "plk:12:3:$date",
                    lineId = line.id,
                    routeId = 12,
                    tripId = 3,
                    directionId = "9001>9002",
                    headsign = "Gdynia Główna",
                    stops = listOf(TimetableStopTime(stop, 0, departureTime = Instant.parse("2026-10-20T08:00:00Z"))),
                ),
            ),
        )
        var requestedProvider: TransitProvider? = null
        var requestedLine: TimetableLine? = null
        var requestedDate: LocalDate? = null
        var requestedStop: TransitStopKey? = null
        val source = object : TimetableDataSource {
            override suspend fun getLines(provider: TransitProvider): List<TimetableLine> {
                requestedProvider = provider
                return listOf(line)
            }

            override suspend fun getTimetable(
                line: TimetableLine,
                date: LocalDate,
                stopKey: TransitStopKey?,
            ): LineTimetable {
                requestedLine = line
                requestedDate = date
                requestedStop = stopKey
                return timetable
            }
        }
        val repository = RealScheduleRepository(source)

        assertEquals(listOf(line), repository.getLines(TransitProvider.PLK).first())
        assertEquals(timetable, repository.getTimetable(line, date, stop).first())
        assertEquals(TransitProvider.PLK, requestedProvider)
        assertEquals(line, requestedLine)
        assertEquals(date, requestedDate)
        assertEquals(stop, requestedStop)
    }

    @Test
    fun `repository propagates source failures to collectors`() = runTest {
        val expected = IllegalStateException("schedule feed unavailable")
        val source = object : TimetableDataSource {
            override suspend fun getLines(provider: TransitProvider): List<TimetableLine> = throw expected
            override suspend fun getTimetable(
                line: TimetableLine,
                date: LocalDate,
                stopKey: TransitStopKey?,
            ): LineTimetable = throw expected
        }
        val repository = RealScheduleRepository(source)

        val lineError = assertFailsWith<IllegalStateException> { repository.getLines(TransitProvider.GDANSK).first() }
        val timetableError = assertFailsWith<IllegalStateException> {
            repository.getTimetable(
                TimetableLine("GDANSK:6", TransitProvider.GDANSK, "6", "6", "6"),
                LocalDate(2026, 10, 20),
            ).first()
        }
        assertEquals(expected.message, lineError.message)
        assertEquals(expected.message, timetableError.message)
    }
}
