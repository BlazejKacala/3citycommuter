package pl.bkacala.threecitycommuter.ui.screen.map.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import pl.bkacala.threecitycommuter.model.transit.TransitProvider
import pl.bkacala.threecitycommuter.ui.theme.Padding
import pl.bkacala.threecitycommuter.ui.theme.stopMarkerColor

@Stable
data class DeparturesBottomSheetModel(
    val provider: TransitProvider,
    val header: DeparturesHeaderModel,
    val departures: List<DepartureRowModel>,
)

@Composable
fun DeparturesBottomSheet(
    model: DeparturesBottomSheetModel,
    onDepartureSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = stopMarkerColor(model.provider)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            model.header.Widget(accentColor = accentColor)
            if (model.provider == TransitProvider.SKM) {
                Text(
                    text = "Pozycja pociagu nie jest publicznie dostepna. Pokazujemy trase i czasy odjazdow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Padding.big),
                )
            }
            model.departures.fastForEachIndexed { index, it ->
                it.Widget(
                    accentColor = accentColor,
                    onSelected = onDepartureSelected,
                )
                if (index != model.departures.size - 1) {
                    HorizontalDivider()
                }
            }
            if (model.departures.isEmpty()) {
                DeparturesEmptyRow()
            }
        }
    }
}
