package pl.bkacala.threecitycommuter.repository.schedule

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import pl.bkacala.threecitycommuter.model.schedule.LineTimetable
import pl.bkacala.threecitycommuter.model.schedule.TimetableLine
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey

interface ScheduleRepository {
    fun getLines(provider: TransitProvider): Flow<List<TimetableLine>>

    fun getTimetable(line: TimetableLine, date: LocalDate, stopKey: TransitStopKey? = null): Flow<LineTimetable>
}
