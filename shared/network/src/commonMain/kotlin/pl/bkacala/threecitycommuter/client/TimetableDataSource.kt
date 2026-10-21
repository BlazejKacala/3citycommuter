package pl.bkacala.threecitycommuter.client

import kotlinx.datetime.LocalDate
import pl.bkacala.threecitycommuter.model.schedule.LineTimetable
import pl.bkacala.threecitycommuter.model.schedule.TimetableLine
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey

interface TimetableDataSource {
    suspend fun getLines(provider: TransitProvider): List<TimetableLine>

    /** Rail schedules are station-scoped; city timetables contain the selected line's trips. */
    suspend fun getTimetable(line: TimetableLine, date: LocalDate, stopKey: TransitStopKey? = null): LineTimetable
}
