package pl.bkacala.threecitycommuter.ui.screen.schedule.data

import kotlinx.datetime.LocalDate
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleLine
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleNotice
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleProvider
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleStop
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleTrip
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.serviceDay

/** Deliberately small, fictional services. Never calls transport or location APIs. */
class DemoScheduleDataSource {
    val stops = listOf(
        ScheduleStop("glowny", "Gdańsk Główny", "1001", 420),
        ScheduleStop("stocznia", "Gdańsk Stocznia", "1002", 1200),
        ScheduleStop("politechnika", "Gdańsk Politechnika", "1003", 2100),
        ScheduleStop("wrzeszcz", "Gdańsk Wrzeszcz", "1004", 2800),
        ScheduleStop("zaspa", "Gdańsk Zaspa", "1005", 3900),
        ScheduleStop("oliwa", "Gdańsk Oliwa", "1006", 6500),
        ScheduleStop("sopot", "Sopot", "2001", 9800),
        ScheduleStop("gdynia", "Gdynia Główna", "3001", 18000),
        ScheduleStop("brama", "Brama Wyżynna", "1010", 180),
        ScheduleStop("siedlce", "Siedlce", "1011", 2200),
        ScheduleStop("jasien", "Jasień PKM", "1012", 5600),
        ScheduleStop("morena", "Morena", "1013", 4300),
        ScheduleStop("wzgorze", "Wzgórze św. Maksymiliana", "3010", 16500),
        ScheduleStop("redlowo", "Redłowo SKM", "3011", 15000),
        ScheduleStop("karwiny", "Karwiny", "3012", 14000),
    )

    val lines = listOf(
        ScheduleLine(
            "skm",
            "SKM",
            "Przez trzy miasta",
            ScheduleProvider.SKM,
            listOf("glowny", "stocznia", "politechnika", "wrzeszcz", "zaspa", "oliwa", "sopot", "gdynia"),
        ),
        ScheduleLine(
            "gd-6",
            "6",
            "Śródmieście · Wrzeszcz · Oliwa",
            ScheduleProvider.GDANSK,
            listOf("brama", "glowny", "politechnika", "wrzeszcz", "oliwa"),
        ),
        ScheduleLine(
            "gd-12",
            "12",
            "Siedlce · centrum · Wrzeszcz",
            ScheduleProvider.GDANSK,
            listOf("siedlce", "brama", "glowny", "politechnika", "wrzeszcz"),
        ),
        ScheduleLine(
            "gd-127",
            "127",
            "Jasień · Morena · Wrzeszcz",
            ScheduleProvider.GDANSK,
            listOf("jasien", "morena", "wrzeszcz"),
        ),
        ScheduleLine(
            "gdy-23",
            "23",
            "Karwiny · Redłowo · centrum",
            ScheduleProvider.GDYNIA,
            listOf("karwiny", "redlowo", "wzgorze", "gdynia"),
        ),
        ScheduleLine(
            "gdy-181",
            "181",
            "Sopot · Karwiny · Gdynia",
            ScheduleProvider.GDYNIA,
            listOf("sopot", "karwiny", "redlowo", "wzgorze", "gdynia"),
        ),
    )

    val notices = listOf(
        ScheduleNotice("Prace przy torach w Oliwie", "Przykładowy komunikat: pociągi SKM mogą przyjechać 3 min później. Sprawdź status wybranego kursu.", setOf("skm")),
        ScheduleNotice("Zmiana trasy linii 12", "Przykładowy objazd przez Bramę Wyżynną. Kolejność przystanków w szczegółach kursu uwzględnia zmianę.", setOf("gd-12")),
        ScheduleNotice("Wybrane kursy odwołane", "Przykładowe odwołania na linii 23 oznaczono w rozkładzie. Wybierz kolejny dostępny odjazd.", setOf("gdy-23")),
    )

    fun stop(id: String) = stops.first { it.id == id }
    fun line(id: String) = lines.first { it.id == id }
    fun destination(line: ScheduleLine, reversed: Boolean) = stop(line.stops(reversed).last()).name
    fun boardingMinute(trip: ScheduleTrip): Int = trip.startMinute + line(trip.lineId).stops(trip.reversed).indexOf(trip.boardingStopId) * STOP_INTERVAL

    fun trips(line: ScheduleLine, date: LocalDate, reversed: Boolean, today: LocalDate): List<ScheduleTrip> =
        (300..1380 step date.serviceDay().intervalMinutes).mapIndexed { index, minute ->
            val live = date == today && line.provider != ScheduleProvider.GDYNIA
            ScheduleTrip(
                lineId = line.id,
                date = date,
                startMinute = minute,
                reversed = reversed,
                boardingStopId = line.stops(reversed).first(),
                delayMinutes = if (live && index % 3 == 0) 3 else 0,
                cancelled = line.id == "gdy-23" && index % 5 == 0,
                live = live,
            )
        }

    companion object {
        const val STOP_INTERVAL = 4
    }
}
