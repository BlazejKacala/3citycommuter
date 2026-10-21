package pl.bkacala.threecitycommuter.ui.screen.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.ProviderFilters
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.SchedulePage
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.ScheduleSection
import pl.bkacala.threecitycommuter.ui.screen.schedule.component.scheduleCardColors
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.ScheduleCollection
import pl.bkacala.threecitycommuter.ui.screen.schedule.model.SearchCategory

data class ScheduleNavigation(
    val timetable: () -> Unit,
    val trip: () -> Unit,
    val collection: () -> Unit,
    val notices: () -> Unit,
    val back: () -> Unit,
)

@Composable
fun ScheduleScreen(state: ScheduleUiState, onAction: (ScheduleAction) -> Unit, navigation: ScheduleNavigation) {
    SchedulePage("Rozkład jazdy", "Trójmiasto w Twoim rytmie", action = {
        IconButton(onClick = {
            onAction(ScheduleAction.OpenCollection(ScheduleCollection.ALERTS))
            navigation.collection()
        }) {
            Icon(Icons.Rounded.NotificationsNone, "Moje alerty")
        }
    }) {
        state.providerErrors.forEach { (provider, _) ->
            item { ScheduleLoadingStatus(ScheduleLoadState.Error("Nie udało się wczytać linii: ${provider.label}.")) { onAction(ScheduleAction.Retry) } }
        }
        item { ScheduleSearch(state, onAction) }
        item { ProviderFilters(state.provider) { onAction(ScheduleAction.ProviderChanged(it)) } }
        if (state.query.isBlank()) {
            item { HomeShortcuts(onAction, navigation) }
            item {
                ScheduleSection("Odkryj połączenia") {
                    TextButton(onClick = {
                        onAction(ScheduleAction.OpenCollection(ScheduleCollection.HISTORY))
                        navigation.collection()
                    }) { Text("Historia") }
                }
            }
        } else {
            item { ScheduleSection("Wyniki wyszukiwania") }
        }
        searchResults(state, onAction, navigation)
    }
}

@Composable
private fun ScheduleSearch(state: ScheduleUiState, onAction: (ScheduleAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryTabRow(selectedTabIndex = state.category.ordinal, containerColor = MaterialTheme.colorScheme.surface) {
            SearchCategory.entries.forEach { category ->
                Tab(selected = state.category == category, onClick = { onAction(ScheduleAction.CategoryChanged(category)) }, text = { Text(category.label) })
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = { onAction(ScheduleAction.QueryChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
            label = { Text(if (state.category == SearchCategory.LINES) "Numer lub nazwa linii" else "Nazwa lub numer przystanku") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) IconButton(onClick = { onAction(ScheduleAction.QueryChanged("")) }) { Icon(Icons.Rounded.Close, "Wyczyść wyszukiwanie") }
            },
        )
    }
}

@Composable
private fun HomeShortcuts(onAction: (ScheduleAction) -> Unit, navigation: ScheduleNavigation) {
    fun open(collection: ScheduleCollection) {
        onAction(ScheduleAction.OpenCollection(collection))
        navigation.collection()
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ShortcutTile("Ulubione", "Twoje linie i przystanki", Icons.Rounded.FavoriteBorder, Modifier.weight(1f)) { open(ScheduleCollection.FAVORITES) }
            ShortcutTile("W pobliżu", "Znajdź przystanek", Icons.Rounded.MyLocation, Modifier.weight(1f)) { open(ScheduleCollection.NEARBY) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ShortcutTile("Offline", "Zapisane rozkłady", Icons.Rounded.CloudDownload, Modifier.weight(1f)) { open(ScheduleCollection.OFFLINE) }
            ShortcutTile("Komunikaty", "Zmiany i utrudnienia", Icons.Rounded.WarningAmber, Modifier.weight(1f), navigation.notices)
        }
    }
}

@Composable
private fun ShortcutTile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp), colors = scheduleCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.secondary)
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
