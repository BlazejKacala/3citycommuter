package pl.bkacala.threecitycommuter.ui.screen.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.FavoriteButton
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.InformationCard
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.LineBadge
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.ScheduleDateFilter
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.SchedulePage
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.ScheduleSection
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.scheduleCardColors
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleTrip
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.asScheduleDate
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.asScheduleTime
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.serviceDay

@Composable
fun ScheduleTimetableScreen(state: ScheduleUiState, onAction: (ScheduleAction) -> Unit, navigation: ScheduleNavigation) {
    val departures = state.departures.filter { it.date != state.today || !it.hasKnownBoardingTime || state.boardingMinute(it) + it.delayMinutes >= state.currentMinute }
    val favoriteKey = if (state.stopSearch) "stop:${state.stopId}" else "line:${state.lineId}"
    SchedulePage(
        title = if (state.stopSearch) state.selectedStop?.name.orEmpty() else "Linia ${state.selectedLine?.number.orEmpty()}",
        subtitle = if (state.savedScheduleId != null) "Zapisany rozkład · offline" else "Rozkład przewoźnika",
        onBack = navigation.back,
        action = { FavoriteButton(favoriteKey in state.preferences.favorites) { onAction(ScheduleAction.ToggleFavorite(favoriteKey)) } },
    ) {
        item { TimetableSelectors(state, onAction) }
        item {
            if (state.savedScheduleId == null) {
                ScheduleDateFilter(state.date, state.today) { onAction(ScheduleAction.DateChanged(it)) }
            } else {
                InformationCard("Rozkład offline", "${state.date.asScheduleDate()} · ${state.date.serviceDay().label}. Godziny planowe, bez aktualizacji opóźnień.", Icons.Rounded.CloudDownload)
            }
        }
        if (state.activeNotices.isNotEmpty() && state.savedScheduleId == null) {
            item {
                TextButton(onClick = navigation.notices) {
                    Icon(Icons.Rounded.WarningAmber, null)
                    Text("Komunikaty dla trasy (${state.activeNotices.size})", Modifier.padding(start = 8.dp))
                }
            }
        }
        item {
            ScheduleSection(if (state.date == state.today) "Najbliższe odjazdy" else "Odjazdy w tym dniu") {
                if (state.selectedLine != null && state.savedScheduleId == null) {
                    TextButton(onClick = { onAction(ScheduleAction.SaveOffline) }) { Text("Zapisz offline") }
                }
            }
        }
        item { ScheduleLoadingStatus(state.departuresState) { onAction(ScheduleAction.Retry) } }
        if (departures.isEmpty() && state.departuresState == ScheduleLoadState.Success) {
            item { InformationCard("Brak odjazdów", "To koniec trasy lub kursów w tym dniu. Zmień kierunek, przystanek albo datę.", Icons.Rounded.Schedule) }
        }
        val nearestId = departures.firstOrNull { !it.cancelled && it.hasKnownBoardingTime && state.boardingMinute(it) + it.delayMinutes >= state.currentMinute }?.id
        items(departures, key = { it.id }) { trip ->
            DepartureCard(state, trip, trip.id == nearestId && state.date == state.today) {
                onAction(ScheduleAction.OpenTrip(trip))
                navigation.trip()
            }
        }
    }
}

@Composable
private fun TimetableSelectors(state: ScheduleUiState, onAction: (ScheduleAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.stopSearch) {
            ScheduleDropdown(
                label = "Linia",
                selected = state.selectedLine?.number ?: "Wszystkie linie",
                options = listOf("" to "Wszystkie linie") + state.availableLines.map { it.id to "${it.number} · ${it.name}" },
                onSelect = { onAction(ScheduleAction.LineChanged(it.ifEmpty { null })) },
            )
        }
        val line = state.selectedLine
        if (state.savedScheduleId == null && line != null) {
            ScheduleDropdown(
                label = "Kierunek",
                selected = state.selectedDirection?.headsign ?: "Wybierz kierunek",
                options = line.directions.map { it.id to it.headsign },
                onSelect = { onAction(ScheduleAction.DirectionChanged(it)) },
            )
        } else if (line != null) {
            Text("Kierunek: ${state.destination(line)}", style = MaterialTheme.typography.titleMedium)
        }
        if (!state.stopSearch && state.savedScheduleId == null) {
            ScheduleDropdown("Przystanek", state.selectedStop?.name.orEmpty(), state.routeStops.map { it.id to "${it.name} · ${it.code}" }) {
                onAction(ScheduleAction.StopChanged(it))
            }
        }
    }
}

@Composable
private fun ScheduleDropdown(label: String, selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
                Text(selected)
            }
            Icon(Icons.Rounded.ExpandMore, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = {
                    onSelect(id)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun DepartureCard(state: ScheduleUiState, trip: ScheduleTrip, nearest: Boolean, onClick: () -> Unit) {
    val line = state.line(trip.lineId)
    val scheduled = state.boardingMinute(trip)
    val expected = scheduled + trip.delayMinutes
    val colors = if (nearest) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary) else scheduleCardColors()
    Card(onClick = onClick, colors = colors, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (nearest) Text("NAJBLIŻSZY KURS", style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                LineBadge(line)
                Column(Modifier.weight(1f)) {
                    Text(trip.headsign.ifBlank { "Kierunek niedostępny" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(departureStatus(trip), style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (trip.hasKnownBoardingTime) expected.asScheduleTime() else "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textDecoration = if (trip.cancelled) TextDecoration.LineThrough else null)
                    if (trip.hasKnownBoardingTime && !trip.cancelled && trip.date == state.today) {
                        val minutes = expected - state.currentMinute
                        Text(if (minutes <= 0) "teraz" else "za $minutes min", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (trip.delayMinutes > 0) Text("Planowo ${scheduled.asScheduleTime()} · opóźnienie +${trip.delayMinutes} min", style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun departureStatus(trip: ScheduleTrip): String = when {
    trip.cancelled -> "Kurs odwołany"
    trip.live -> "Prognoza na żywo"
    !trip.hasKnownBoardingTime -> "Godzina niedostępna"
    else -> "Odjazd planowy"
}
