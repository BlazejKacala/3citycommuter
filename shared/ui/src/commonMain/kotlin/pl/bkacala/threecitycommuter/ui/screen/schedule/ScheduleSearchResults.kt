package pl.bkacala.threecitycommuter.ui.screen.schedule

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.FavoriteButton
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.InformationCard
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.LineBadge
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.ScheduleRow
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.SearchCategory

internal fun LazyListScope.searchResults(state: ScheduleUiState, onAction: (ScheduleAction) -> Unit, navigation: ScheduleNavigation) {
    if (state.category == SearchCategory.LINES) {
        items(state.filteredLines, key = { it.id }) { line ->
            ScheduleRow(
                title = line.name.ifBlank { "Linia ${line.number}" },
                subtitle = "${line.provider.label} · ${line.name}",
                onClick = {
                    onAction(ScheduleAction.OpenLine(line.id))
                    navigation.timetable()
                },
                leading = { LineBadge(line) },
                trailing = { FavoriteButton("line:${line.id}" in state.preferences.favorites) { onAction(ScheduleAction.ToggleFavorite("line:${line.id}")) } },
            )
        }
    } else {
        items(state.filteredStops, key = { it.id }) { stop ->
            ScheduleRow(
                title = stop.name,
                subtitle = "Przystanek ${stop.code}",
                onClick = {
                    onAction(ScheduleAction.OpenStop(stop.id))
                    navigation.timetable()
                },
                leading = { Icon(Icons.Rounded.LocationOn, null, tint = MaterialTheme.colorScheme.secondary) },
                trailing = { FavoriteButton("stop:${stop.id}" in state.preferences.favorites) { onAction(ScheduleAction.ToggleFavorite("stop:${stop.id}")) } },
            )
        }
    }
    val loadState = if (state.category == SearchCategory.LINES) state.catalogState else state.stopsState
    item { ScheduleLoadingStatus(loadState) { onAction(ScheduleAction.Retry) } }
    if (loadState == ScheduleLoadState.Success && ((state.category == SearchCategory.LINES && state.filteredLines.isEmpty()) || (state.category == SearchCategory.STOPS && state.filteredStops.isEmpty()))) {
        item { InformationCard("Brak wyników", "Zmień nazwę lub numer albo wybierz innego przewoźnika.", Icons.Rounded.Search) }
    }
}
