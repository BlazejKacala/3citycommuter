package pl.bkacala.threecitycommuter.ui.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.InformationCard
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.LineBadge
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.ScheduleAlertDialog
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.SchedulePage
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.ScheduleSection
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleStop
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleStopTime
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleTrip
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.asScheduleDate
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.asScheduleTime
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.minuteOfServiceDay

@Composable
fun ScheduleTripScreen(state: ScheduleUiState, onAction: (ScheduleAction) -> Unit, onBack: () -> Unit) {
    val trip = state.trip
    if (trip == null) {
        SchedulePage("Szczegóły kursu", "Rozkład przewoźnika", onBack) {
            item { InformationCard("Wybierz kurs", "Wróć do rozkładu i wybierz odjazd, aby zobaczyć jego trasę.", Icons.Rounded.Schedule) }
        }
        return
    }
    val line = state.line(trip.lineId)
    val stopTimes = trip.stopTimes.sortedBy { it.sequence }
    val alert = state.preferences.alerts.firstOrNull { it.trip.id == trip.id }
    var showAlert by rememberSaveable(trip.id) { mutableStateOf(false) }
    val upcoming = trip.hasKnownBoardingTime && (trip.date > state.today || (trip.date == state.today && state.boardingMinute(trip) + trip.delayMinutes > state.currentMinute))
    SchedulePage("Szczegóły kursu", "${trip.date.asScheduleDate()} · ${line.provider.label}", onBack) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                LineBadge(line)
                Column {
                    Text("Kierunek", style = MaterialTheme.typography.labelMedium)
                    Text(trip.headsign.ifBlank { "Kierunek niedostępny" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { ScheduleLoadingStatus(state.tripState) { onAction(ScheduleAction.Retry) } }
        item {
            val plannedTime = if (trip.hasKnownBoardingTime) "${if (trip.hasPlannedBoardingTime) "Planowo" else "Prognoza"} ${state.boardingMinute(trip).asScheduleTime()}" else "Godzina niedostępna"
            InformationCard(departureStatus(trip), "Twój przystanek: ${state.stop(trip.boardingStopId).name}\n$plannedTime", Icons.Rounded.Schedule)
        }
        item {
            Button(onClick = { showAlert = true }, enabled = !trip.cancelled && upcoming, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.NotificationsNone, null)
                Text(if (alert == null) "Przypomnij o odjeździe" else "Edytuj alert · ${alert.minutesBefore} min wcześniej", Modifier.padding(8.dp))
            }
            Text("Ustawienie zapisane w aplikacji. Powiadomienia systemowe nie są jeszcze dostępne.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(state.notices.filter { trip.lineId in it.lineIds }) { notice ->
            InformationCard(notice.title, notice.description, Icons.Rounded.WarningAmber)
        }
        item { ScheduleSection("Trasa kursu · ${stopTimes.size} przystanków") }
        itemsIndexed(stopTimes) { index, stopTime ->
            RouteStopRow(state.stop(stopTime.stopId), trip, stopTime, stopTime.stopId == trip.boardingStopId, index == stopTimes.lastIndex)
        }
    }
    if (showAlert) {
        ScheduleAlertDialog(trip, alert, onDismiss = { showAlert = false }) {
            onAction(ScheduleAction.SaveAlert(it))
            showAlert = false
        }
    }
}

@Composable
private fun RouteStopRow(stop: ScheduleStop, trip: ScheduleTrip, stopTime: ScheduleStopTime, boarding: Boolean, last: Boolean) {
    val color = if (boarding) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(if (boarding) 16.dp else 12.dp).background(color, CircleShape))
            if (!last) Box(Modifier.size(width = 2.dp, height = 44.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
        }
        Column(Modifier.weight(1f)) {
            Text(stop.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (boarding) FontWeight.Bold else FontWeight.Normal)
            Text(if (boarding) "Twój przystanek · ${stop.code}" else "Przystanek ${stop.code}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            val time = (stopTime.departure ?: stopTime.arrival)?.minuteOfServiceDay(trip.date)
            Text(time?.asScheduleTime() ?: "—", fontWeight = FontWeight.SemiBold)
            if (boarding && time != null && trip.live && trip.delayMinutes != 0) {
                Text("prognoza ${(time + trip.delayMinutes).asScheduleTime()}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
