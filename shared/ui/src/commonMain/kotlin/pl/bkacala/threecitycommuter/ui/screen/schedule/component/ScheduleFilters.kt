package pl.bkacala.threecitycommuter.ui.screen.schedule.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleProvider
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.asScheduleDate
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.serviceDay

@Composable
fun ProviderFilters(selected: ScheduleProvider?, onSelected: (ScheduleProvider?) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelected(null) }, label = { Text("Wszystkie") })
        ScheduleProvider.entries.forEach { provider ->
            FilterChip(selected = selected == provider, onClick = { onSelected(provider) }, label = { Text(provider.label) })
        }
    }
}

@Composable
fun ScheduleDateFilter(date: LocalDate, today: LocalDate, onDate: (LocalDate) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val tomorrow = today + DatePeriod(days = 1)
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(date == today, { onDate(today) }, { Text("Dzisiaj") })
            FilterChip(date == tomorrow, { onDate(tomorrow) }, { Text("Jutro") })
            FilterChip(date != today && date != tomorrow, { showPicker = true }, { Text("Wybierz datę") }, leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null) })
        }
        Text("${date.asScheduleDate()} · ${date.serviceDay().label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showPicker) {
        ScheduleDateDialog(date, { showPicker = false }) {
            onDate(it)
            showPicker = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDateDialog(date: LocalDate, onDismiss: () -> Unit, onDate: (LocalDate) -> Unit) {
    val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { onDate(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date) }
            }) { Text("Wybierz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    ) { DatePicker(state = picker, title = { Text("Data podróży") }, headline = null) }
}
