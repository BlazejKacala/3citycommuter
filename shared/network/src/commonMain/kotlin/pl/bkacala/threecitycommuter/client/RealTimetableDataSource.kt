package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.model.schedule.LineTimetable
import pl.bkacala.threecitycommuter.model.schedule.TimetableLine
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.model.transit.TransitStopKey

internal class RealTimetableDataSource(
    httpClient: HttpClient,
    json: Json,
    gdanskApiClient: GdanskApiClient,
    plkApiClient: PlkApiClient,
    railStaticCatalog: RailStaticCatalog,
    private val gdyniaTimetableSource: GdyniaTimetableSource,
) : TimetableDataSource {
    private val gdansk = GdanskTimetableSource(httpClient, json, gdanskApiClient)
    private val rail = RailTimetableSource(plkApiClient, railStaticCatalog)

    override suspend fun getLines(provider: TransitProvider): List<TimetableLine> = when (provider) {
        TransitProvider.GDANSK -> gdansk.getLines()
        TransitProvider.GDYNIA -> gdyniaTimetableSource.getLines()
        TransitProvider.PLK -> rail.getLines()
    }

    override suspend fun getTimetable(line: TimetableLine, date: LocalDate, stopKey: TransitStopKey?): LineTimetable =
        when (line.provider) {
            TransitProvider.GDANSK -> gdansk.getTimetable(line, date)
            TransitProvider.GDYNIA -> gdyniaTimetableSource.getTimetable(line, date)
            TransitProvider.PLK -> rail.getTimetable(line, date, stopKey)
        }
}
