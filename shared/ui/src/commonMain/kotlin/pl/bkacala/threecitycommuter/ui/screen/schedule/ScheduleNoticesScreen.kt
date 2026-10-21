package pl.bkacala.threecitycommuter.ui.screen.schedule

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.Composable
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.InformationCard
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.ProviderFilters
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.SchedulePage

@Composable
fun ScheduleNoticesScreen(state: ScheduleUiState, onAction: (ScheduleAction) -> Unit, onBack: () -> Unit) {
    val notices = state.notices.filter { notice -> notice.lineIds.any { state.provider == null || state.line(it).provider == state.provider } }
    SchedulePage("Komunikaty", "Zmiany i utrudnienia", onBack) {
        item { ProviderFilters(state.provider) { onAction(ScheduleAction.ProviderChanged(it)) } }
        items(notices) { notice ->
            InformationCard(notice.title, "Linie: ${notice.lineIds.joinToString { state.line(it).number }}\n${notice.description}", Icons.Rounded.WarningAmber)
        }
        if (notices.isEmpty()) item { InformationCard("Komunikaty niedostępne", state.noticesMessage, Icons.Rounded.Info) }
    }
}
