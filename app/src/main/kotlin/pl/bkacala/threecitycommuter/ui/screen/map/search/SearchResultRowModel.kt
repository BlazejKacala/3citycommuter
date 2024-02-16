package pl.bkacala.threecitycommuter.ui.screen.map.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import pl.bkacala.threecitycommuter.ui.theme.Padding

data class SearchResultRowModel(
    val station: String,
    val distance: String,
    val isForBuses: Boolean,
    val isForTrams: Boolean,
    val onClicked: () -> Unit
)

@Composable
fun SearchResultRowModel.Widget() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClicked() }
            .padding(horizontal = Padding.big, vertical = Padding.normal),
        verticalAlignment = Alignment.CenterVertically

    ) {
        if (isForTrams) {
            Icon(
                imageVector = Icons.Filled.Tram,
                contentDescription = "Przystanek tramwajowy",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isForBuses) {
            Icon(
                imageVector = Icons.Filled.DirectionsBus,
                contentDescription = "Przystanek tramwajowy",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(Padding.normal))
        Column {
            Text(text = station, style = MaterialTheme.typography.titleMedium)
            Text(
                text = distance,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SearchResultRowModelPreview() {
    SearchResultRowModel("Orunia Górna", "Przystanek w odległy o 225m", true, true, {})
}