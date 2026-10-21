package pl.bkacala.threecitycommuter.ui.screen.schedule

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.FavoriteButton
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.InformationCard
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.LineBadge
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.SchedulePage
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.ScheduleRow
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleCollection
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.asScheduleDate
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.asScheduleTime

@Composable
fun ScheduleCollectionScreen(state: ScheduleUiState, onAction: (ScheduleAction) -> Unit, navigation: ScheduleNavigation) {
    SchedulePage(state.collection.title, "Twój rozkład pod ręką", navigation.back, action = {
        if (state.collection == ScheduleCollection.HISTORY && state.preferences.history.isNotEmpty()) {
            TextButton(onClick = { onAction(ScheduleAction.ClearHistory) }) { Text("Wyczyść") }
        }
    }) {
        when (state.collection) {
            ScheduleCollection.FAVORITES -> favoriteAndHistoryItems(state.preferences.favorites.toList(), state, onAction, navigation)
            ScheduleCollection.HISTORY -> favoriteAndHistoryItems(state.preferences.history, state, onAction, navigation)
            ScheduleCollection.NEARBY -> nearbyItems(state, onAction, navigation)
            ScheduleCollection.OFFLINE -> offlineItems(state, onAction, navigation)
            ScheduleCollection.ALERTS -> alertItems(state, onAction, navigation)
        }
    }
}

private fun LazyListScope.favoriteAndHistoryItems(keys: List<String>, state: ScheduleUiState, onAction: (ScheduleAction) -> Unit, navigation: ScheduleNavigation) {
    if (keys.isEmpty()) {
        item {
            InformationCard("Tu pojawią się Twoje rozkłady", "Otwórz linię lub przystanek w wyszukiwarce. Gwiazdką dodasz je do ulubionych.", Icons.Rounded.StarBorder)
        }
    }
    items(keys, key = { it }) { key ->
        val id = key.substringAfter(':')
        val line = state.lines.firstOrNull { it.id == id && key.startsWith("line:") }
        val stop = state.stops.firstOrNull { it.id == id && key.startsWith("stop:") }
        if (line != null || stop != null) {
            ScheduleRow(
                title = line?.let { "Linia ${it.number}" } ?: stop?.name.orEmpty(),
                subtitle = line?.name ?: "Przystanek ${stop?.code}",
                onClick = {
                    onAction(if (line != null) ScheduleAction.OpenLine(id) else ScheduleAction.OpenStop(id))
                    navigation.timetable()
                },
                leading = { if (line != null) LineBadge(line) else Icon(Icons.Rounded.LocationOn, null) },
                trailing = { FavoriteButton(key in state.preferences.favorites) { onAction(ScheduleAction.ToggleFavorite(key)) } },
            )
        }
    }
}

private fun LazyListScope.nearbyItems(state: ScheduleUiState, onAction: (ScheduleAction) -> Unit, navigation: ScheduleNavigation) {
    item {
        InformationCard("Przystanki do 3 km", state.nearbyMessage ?: "Odległości w linii prostej od bieżącej lokalizacji.", Icons.Rounded.MyLocation)
    }
    items(state.stops.filter { it.distanceMeters <= 3000 }.sortedBy { it.distanceMeters }, key = { it.id }) { stop ->
        ScheduleRow(
            title = stop.name,
            subtitle = "${stop.distanceMeters} m · przystanek ${stop.code}",
            onClick = {
                onAction(ScheduleAction.OpenStop(stop.id))
                navigation.timetable()
            },
            leading = { Icon(Icons.Rounded.LocationOn, null) },
        )
    }
}

private fun LazyListScope.offlineItems(state: ScheduleUiState, onAction: (ScheduleAction) -> Unit, navigation: ScheduleNavigation) {
    item {
        InformationCard("Dostępne bez internetu", "Zapisz wybraną linię, kierunek i datę w jej rozkładzie. Przechowujemy 12 ostatnich zapisów. Opóźnienia wymagają połączenia z internetem.", Icons.Rounded.CloudDownload)
    }
    if (state.preferences.saved.isEmpty()) item { Text("Nie masz jeszcze zapisanych rozkładów.") }
    items(state.preferences.saved.reversed(), key = { it.id }) { saved ->
        val line = state.line(saved.lineId)
        val destination = saved.trips.firstOrNull()?.headsign?.ifBlank { null } ?: line.name
        ScheduleRow(
            title = destination,
            subtitle = "${saved.date.asScheduleDate()} · ${if (saved.date < state.today) "archiwalny" else "zapisany na ten dzień"}",
            onClick = {
                onAction(ScheduleAction.OpenSaved(saved))
                navigation.timetable()
            },
            leading = { LineBadge(line) },
            trailing = { IconButton(onClick = { onAction(ScheduleAction.RemoveSaved(saved.id)) }) { Icon(Icons.Rounded.DeleteOutline, "Usuń zapisany rozkład") } },
        )
    }
}

private fun LazyListScope.alertItems(state: ScheduleUiState, onAction: (ScheduleAction) -> Unit, navigation: ScheduleNavigation) {
    item { InformationCard("Spokojnie zdążysz", "Wybierz kurs i ustaw przypomnienie. Ustawienia są zapisywane w aplikacji. Powiadomienia systemowe nie są jeszcze dostępne.", Icons.Rounded.NotificationsNone) }
    if (state.preferences.alerts.isEmpty()) item { Text("Nie masz jeszcze ustawionych alertów.") }
    items(state.preferences.alerts, key = { it.trip.id }) { alert ->
        val trip = alert.trip
        val expired = trip.date < state.today || (trip.date == state.today && state.boardingMinute(trip) + trip.delayMinutes <= state.currentMinute)
        ScheduleRow(
            title = "${state.stop(trip.boardingStopId).name} · ${state.boardingMinute(trip).asScheduleTime()}",
            subtitle = "${trip.date.asScheduleDate()} · ${if (expired) "zakończony" else "${alert.minutesBefore} min wcześniej"}" + if (alert.notifyDelay) " · alert o opóźnieniu" else "",
            onClick = {
                onAction(ScheduleAction.OpenTrip(trip))
                navigation.trip()
            },
            leading = { LineBadge(state.line(trip.lineId)) },
            trailing = { IconButton(onClick = { onAction(ScheduleAction.RemoveAlert(trip.id)) }) { Icon(Icons.Rounded.DeleteOutline, "Usuń alert") } },
        )
    }
}
