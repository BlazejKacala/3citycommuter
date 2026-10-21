package pl.bkacala.threecitycommuter.ui.screen.schedule.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleAlert
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleTrip

@Composable
fun ScheduleAlertDialog(trip: ScheduleTrip, existing: ScheduleAlert?, onDismiss: () -> Unit, onSave: (ScheduleAlert) -> Unit) {
    var minutes by rememberSaveable { mutableStateOf(existing?.minutesBefore ?: 5) }
    var notifyDelay by rememberSaveable { mutableStateOf(existing?.notifyDelay ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Przypomnienie o kursie") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ile minut przed odjazdem z Twojego przystanku?")
                listOf(1, 3, 5, 10).forEach { value ->
                    Row(Modifier.fillMaxWidth().selectable(selected = minutes == value, role = Role.RadioButton, onClick = { minutes = value }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = minutes == value, onClick = null)
                        Text("$value min wcześniej")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Informuj o opóźnieniu", Modifier.weight(1f))
                    Switch(checked = notifyDelay, onCheckedChange = { notifyDelay = it })
                }
                Text("Zapisujemy ustawienia alertu w aplikacji. Powiadomienia systemowe nie są jeszcze dostępne.")
            }
        },
        confirmButton = { TextButton(onClick = { onSave(ScheduleAlert(trip, minutes, notifyDelay)) }) { Text("Zapisz alert") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
