package pl.bkacala.threecitycommuter.ui.screen.map.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import pl.bkacala.threecitycommuter.model.stops.BusStopData
import pl.bkacala.threecitycommuter.ui.theme.Padding

@Stable
data class SearchItemModel(
    val name: String,
    val distance: String,
    val data: BusStopData,
)


@Composable
fun SearchItemModel.Widget() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Padding.normal, horizontal = Padding.large),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Rounded.DirectionsBus, contentDescription = "ikona autobusu")
    }
}

