package pl.bkacala.threecitycommuter.ui.screen.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.InformationCard

@Composable
internal fun ScheduleLoadingStatus(state: ScheduleLoadState, onRetry: () -> Unit) {
    when (state) {
        ScheduleLoadState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
        is ScheduleLoadState.Error -> Column {
            InformationCard("Nie udało się pobrać danych", state.message, Icons.Rounded.Info)
            TextButton(onClick = onRetry) { Text("Spróbuj ponownie") }
        }
        is ScheduleLoadState.Unsupported -> InformationCard("Rozkład niedostępny", state.message, Icons.Rounded.Info)
        else -> Unit
    }
}
