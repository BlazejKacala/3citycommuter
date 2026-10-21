package pl.bkacala.threecitycommuter.ui.screen.schedule

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import pl.bkacala.threecitycommuter.model.departures.Departure
import pl.bkacala.threecitycommuter.model.location.UserLocation
import pl.bkacala.threecitycommuter.model.route.Route
import pl.bkacala.threecitycommuter.model.schedule.LineTimetable
import pl.bkacala.threecitycommuter.model.schedule.TimetableAvailability
import pl.bkacala.threecitycommuter.model.schedule.TimetableJourney
import pl.bkacala.threecitycommuter.model.schedule.TimetableLine
import pl.bkacala.threecitycommuter.model.schedule.TimetableStopTime
import pl.bkacala.threecitycommuter.model.stops.TransitStopData
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey
import pl.bkacala.threecitycommuter.repository.location.LocationRepository
import pl.bkacala.threecitycommuter.repository.location.PermissionChecker
import pl.bkacala.threecitycommuter.repository.routes.RoutesRepository
import pl.bkacala.threecitycommuter.repository.schedule.ScheduleRepository
import pl.bkacala.threecitycommuter.repository.stops.TransitStopsRepository
import pl.bkacala.threecitycommuter.ui.screen.schedule.data.SchedulePreferencesStore
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.SchedulePreferences
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleScreenViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = ViewModelStore()
    private val preferences = MemorySchedulePreferencesStore()
    private val repository = FakeScheduleRepository()
    private val stops = EmptyTransitStopsRepository()
    private val routes = EmptyRoutesRepository()
    private val locations = EmptyLocationRepository()
    private var now = LocalDateTime(2026, 10, 20, 12, 0)
    private lateinit var viewModel: ScheduleScreenViewModel
    private val state get() = viewModel.uiState.value

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = createViewModel()
        models.put("schedule", viewModel)
    }

    @AfterTest
    fun tearDown() {
        models.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `timetable exposes loading then success and successful empty schedules remain empty`() {
        val pending = CompletableDeferred<LineTimetable>()
        repository.timetableHandler = { _, _, _ -> flow { emit(pending.await()) } }

        viewModel.onAction(ScheduleAction.OpenLine(GDANSK_LINE.id))
        assertIs<ScheduleLoadState.Loading>(state.departuresState)

        pending.complete(timetable(now.date, journeys = emptyList()))
        assertIs<ScheduleLoadState.Success>(state.departuresState)
        assertTrue(state.departures.isEmpty())
    }

    @Test
    fun `provider catalog failures stay isolated and retry recovers the failed provider`() {
        var failGdynia = true
        repository.linesHandler = { provider ->
            when (provider) {
                TransitProvider.GDANSK -> flowOf(listOf(GDANSK_LINE))
                TransitProvider.GDYNIA -> if (failGdynia) flow { error("Gdynia unavailable") } else flowOf(listOf(GDYNIA_LINE))
                TransitProvider.PLK -> flowOf(emptyList())
            }
        }
        recreateViewModel()

        assertTrue(state.lines.any { it.id == GDANSK_LINE.id })
        assertEquals("Gdynia unavailable", state.providerErrors[ScheduleProvider.GDYNIA])
        assertIs<ScheduleLoadState.Success>(state.catalogState)

        failGdynia = false
        viewModel.onAction(ScheduleAction.Retry)
        assertTrue(state.lines.any { it.id == GDYNIA_LINE.id })
        assertFalse(ScheduleProvider.GDYNIA in state.providerErrors)
    }

    @Test
    fun `timetable errors can be retried into a real result`() {
        var fail = true
        repository.timetableHandler = { _, date, _ ->
            if (fail) flow { error("temporary failure") } else flowOf(timetable(date))
        }
        viewModel.onAction(ScheduleAction.OpenLine(GDANSK_LINE.id))
        assertIs<ScheduleLoadState.Error>(state.departuresState)

        fail = false
        viewModel.onAction(ScheduleAction.Retry)
        assertIs<ScheduleLoadState.Success>(state.departuresState)
        assertTrue(state.departures.isNotEmpty())
    }

    @Test
    fun `date changes cancel stale timetable collection and keep the newest selection`() {
        val oldResponse = CompletableDeferred<LineTimetable>()
        val newResponse = CompletableDeferred<LineTimetable>()
        var oldCancelled = false
        repository.timetableHandler = { _, date, _ ->
            when (date) {
                LocalDate(2026, 10, 20) -> flow {
                    try {
                        emit(oldResponse.await())
                    } finally {
                        oldCancelled = true
                    }
                }
                else -> flow { emit(newResponse.await()) }
            }
        }

        viewModel.onAction(ScheduleAction.OpenLine(GDANSK_LINE.id))
        viewModel.onAction(ScheduleAction.DateChanged(LocalDate(2026, 10, 21)))
        assertTrue(oldCancelled)
        newResponse.complete(timetable(LocalDate(2026, 10, 21), headsign = "Tuesday destination"))
        oldResponse.complete(timetable(LocalDate(2026, 10, 20), headsign = "Stale destination"))

        assertTrue(state.departures.isNotEmpty())
        assertEquals(LocalDate(2026, 10, 21), state.departures.single().date)
        assertEquals("Tuesday destination", state.departures.single().headsign)
    }

    @Test
    fun `direction selection uses the chosen real variant and date is passed through`() {
        val requestedDates = mutableListOf<LocalDate>()
        repository.timetableHandler = { _, date, _ ->
            requestedDates += date
            flowOf(timetable(date, directions = listOf("east", "west")))
        }
        viewModel.onAction(ScheduleAction.OpenLine(GDANSK_LINE.id))
        assertEquals("east", state.departures.single().directionId)

        viewModel.onAction(ScheduleAction.DirectionChanged("west"))
        assertEquals("west", state.directionId)
        assertTrue(state.departures.isNotEmpty())
        assertTrue(state.departures.all { it.directionId == "west" })

        viewModel.onAction(ScheduleAction.DateChanged(LocalDate(2026, 10, 22)))
        assertEquals(LocalDate(2026, 10, 22), state.departures.single().date)
        assertTrue(LocalDate(2026, 10, 22) in requestedDates)
    }

    @Test
    fun `offline schedule restores its snapshots without fetching the timetable again`() {
        repository.timetableHandler = { _, date, _ -> flowOf(timetable(date)) }
        viewModel.onAction(ScheduleAction.OpenLine(GDANSK_LINE.id))
        viewModel.onAction(ScheduleAction.SaveOffline)
        val saved = preferences.read().saved.single()
        val timetableCallsBeforeRestore = repository.timetableCalls

        val restored = createViewModel()
        models.put("restored", restored)
        restored.onAction(ScheduleAction.OpenSaved(saved))

        assertEquals(saved.id, restored.uiState.value.savedScheduleId)
        assertEquals(saved.trips, restored.uiState.value.departures)
        assertTrue(restored.uiState.value.selectedLine?.stopIds?.isNotEmpty() == true)
        assertEquals(timetableCallsBeforeRestore, repository.timetableCalls)
    }

    @Test
    fun `unsupported date is distinct from a valid empty timetable`() {
        repository.timetableHandler = { _, date, _ ->
            flowOf(
                LineTimetable(
                    line = GDANSK_LINE,
                    date = date,
                    journeys = emptyList(),
                    availability = TimetableAvailability.UNSUPPORTED_DATE,
                    message = "Feed not published",
                ),
            )
        }
        viewModel.onAction(ScheduleAction.OpenLine(GDANSK_LINE.id))
        assertEquals("Feed not published", assertIs<ScheduleLoadState.Unsupported>(state.departuresState).message)
    }

    @Test
    fun `live polling overlays the matching run only and stops when paused`() {
        repository.timetableHandler = { _, date, _ -> flowOf(timetable(date)) }
        stops.departures = listOf(departure(tripId = 999, delayMinutes = 20), departure(tripId = 100, delayMinutes = 4))
        viewModel.onAction(ScheduleAction.OpenLine(GDANSK_LINE.id))
        viewModel.onAction(ScheduleAction.ScreenResumed())
        dispatcher.scheduler.advanceTimeBy(30_001)
        assertEquals(4, state.departures.single().delayMinutes)
        assertTrue(state.departures.single().live)
        assertEquals(1, repository.timetableCalls)
        viewModel.onAction(ScheduleAction.ScreenPaused)
        val calls = stops.departureCalls
        dispatcher.scheduler.advanceTimeBy(120_000)
        assertEquals(calls, stops.departureCalls)
    }

    @Test
    fun `changing direction reuses the selected dated timetable`() {
        repository.timetableHandler = { _, date, _ -> flowOf(timetable(date, directions = listOf("east", "west"))) }
        viewModel.onAction(ScheduleAction.OpenLine(GDANSK_LINE.id))
        viewModel.onAction(ScheduleAction.DirectionChanged("west"))
        viewModel.onAction(ScheduleAction.ScreenResumed())
        assertEquals(1, repository.timetableCalls)
        assertEquals("west", state.departures.single().directionId)
    }

    @Test
    fun `offline snapshots and planned trip details never request live routes`() {
        repository.timetableHandler = { _, date, _ -> flowOf(timetable(date)) }
        viewModel.onAction(ScheduleAction.OpenLine(GDANSK_LINE.id))
        val planned = state.departures.single()
        viewModel.onAction(ScheduleAction.OpenTrip(planned))
        assertEquals(0, routes.calls)
        assertEquals(planned.stopTimes, state.trip?.stopTimes)
        viewModel.onAction(ScheduleAction.SaveOffline)
        viewModel.onAction(ScheduleAction.OpenSaved(preferences.read().saved.single()))
        viewModel.onAction(ScheduleAction.ScreenResumed())
        dispatcher.scheduler.advanceTimeBy(120_001)
        assertEquals(0, stops.departureCalls)
        assertEquals(1, repository.timetableCalls)
    }

    @Test
    fun `arrival at terminal is not listed as a boarding departure`() {
        repository.timetableHandler = { _, date, _ -> flowOf(timetable(date)) }
        viewModel.onAction(ScheduleAction.OpenLine(GDANSK_LINE.id))
        viewModel.onAction(ScheduleAction.StopChanged("GDANSK:11"))
        assertTrue(state.departures.isEmpty())
        assertIs<ScheduleLoadState.Success>(state.departuresState)
    }

    private fun departure(tripId: Int, delayMinutes: Int) = Departure(
        id = "run-$tripId", delayInSeconds = delayMinutes * 60,
        estimatedTime = Instant.parse("2026-10-20T11:${delayMinutes.toString().padStart(2, '0')}:00Z"),
        headsign = "Destination", lineNumber = "6", routeId = 6,
        scheduledTripStartTime = null, tripId = tripId, status = null,
        theoreticalTime = Instant.parse("2026-10-20T11:00:00Z"), timestamp = null,
        trip = null, vehicleCode = null, vehicleId = null, vehicleService = null,
    )

    private fun recreateViewModel() {
        models.clear()
        viewModel = createViewModel()
        models.put("schedule", viewModel)
    }

    private fun createViewModel() = ScheduleScreenViewModel(
        preferencesStore = preferences,
        scheduleRepository = repository,
        stopsRepository = stops,
        routesRepository = routes,
        locationRepository = locations,
        permissionChecker = object : PermissionChecker {
            override fun isLocationPermissionGranted() = false
        },
        now = { now },
    )

    private fun timetable(
        date: LocalDate,
        journeys: List<TimetableJourney>? = null,
        directions: List<String> = listOf("east"),
        headsign: String = "Destination",
    ): LineTimetable {
        val actualJourneys = journeys ?: directions.map { direction ->
            journey(date, direction, if (direction == "east") headsign else "West destination")
        }
        return LineTimetable(GDANSK_LINE, date, actualJourneys)
    }

    private fun journey(date: LocalDate, direction: String, headsign: String) = TimetableJourney(
        id = "gdansk:6:$direction:$date",
        lineId = GDANSK_LINE.id,
        routeId = 6,
        tripId = if (direction == "east") 100 else 200,
        directionId = direction,
        headsign = headsign,
        stops = listOf(
            TimetableStopTime(STOP_A, 0, arrivalTime = Instant.parse("${date}T10:55:00Z"), departureTime = Instant.parse("${date}T11:00:00Z")),
            TimetableStopTime(STOP_B, 1, arrivalTime = Instant.parse("${date}T11:20:00Z"), departureTime = Instant.parse("${date}T11:22:00Z")),
        ),
    )

    private class FakeScheduleRepository : ScheduleRepository {
        var linesHandler: (TransitProvider) -> Flow<List<TimetableLine>> = { provider ->
            when (provider) {
                TransitProvider.GDANSK -> flowOf(listOf(GDANSK_LINE))
                TransitProvider.GDYNIA -> flowOf(listOf(GDYNIA_LINE))
                TransitProvider.PLK -> flowOf(emptyList())
            }
        }
        var timetableCalls = 0
        var timetableHandler: (TimetableLine, LocalDate, TransitStopKey?) -> Flow<LineTimetable> = { line, date, _ ->
            flowOf(LineTimetable(line, date, emptyList()))
        }

        override fun getLines(provider: TransitProvider): Flow<List<TimetableLine>> = linesHandler(provider)

        override fun getTimetable(
            line: TimetableLine,
            date: LocalDate,
            stopKey: TransitStopKey?,
        ): Flow<LineTimetable> {
            timetableCalls++
            return timetableHandler(line, date, stopKey)
        }
    }

    private class EmptyTransitStopsRepository : TransitStopsRepository {
        override fun getTransitStops(): Flow<List<TransitStopData>> = flowOf(emptyList())
        var departureCalls = 0
        var departures = emptyList<Departure>()
        override fun getDepartures(stopKey: TransitStopKey): Flow<List<Departure>> {
            departureCalls++
            return flowOf(departures)
        }
    }

    private class EmptyRoutesRepository : RoutesRepository {
        var calls = 0
        override fun getRoute(provider: TransitProvider, routeId: Int, tripId: Int): Flow<Route> {
            calls++
            return flowOf(Route(emptyList()))
        }
    }

    private class EmptyLocationRepository : LocationRepository {
        override fun getLocation(): Flow<UserLocation> = flowOf(UserLocation.default())
    }

    private class MemorySchedulePreferencesStore : SchedulePreferencesStore {
        private var value = SchedulePreferences()
        override fun read() = value
        override fun write(preferences: SchedulePreferences) {
            value = preferences
        }
    }

    private companion object {
        val STOP_A = TransitStopKey(TransitProvider.GDANSK, 10)
        val STOP_B = TransitStopKey(TransitProvider.GDANSK, 11)
        val GDANSK_LINE = TimetableLine("GDANSK:6", TransitProvider.GDANSK, "6", "6", "Tram 6", listOf(STOP_A, STOP_B))
        val GDYNIA_LINE = TimetableLine("GDYNIA:23", TransitProvider.GDYNIA, "23", "23", "Route 23")
    }
}
