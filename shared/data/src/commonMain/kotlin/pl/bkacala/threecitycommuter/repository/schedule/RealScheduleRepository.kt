package pl.bkacala.threecitycommuter.repository.schedule

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.LocalDate
import pl.bkacala.threecitycommuter.client.TimetableDataSource
import pl.bkacala.threecitycommuter.model.schedule.LineTimetable
import pl.bkacala.threecitycommuter.model.schedule.TimetableLine
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey

internal class RealScheduleRepository(
    private val timetableDataSource: TimetableDataSource,
) : ScheduleRepository {
    override fun getLines(provider: TransitProvider): Flow<List<TimetableLine>> = flow {
        emit(timetableDataSource.getLines(provider))
    }.flowOn(Dispatchers.IO)

    override fun getTimetable(line: TimetableLine, date: LocalDate, stopKey: TransitStopKey?): Flow<LineTimetable> = flow {
        emit(timetableDataSource.getTimetable(line, date, stopKey))
    }.flowOn(Dispatchers.IO)
}
