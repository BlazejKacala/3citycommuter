package pl.bkacala.threecitycommuter.ui.screen.map.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed


@Stable
data class DeparturesBottomSheetModel(
    val header: DeparturesHeaderModel,
    val departures: List<DepartureRowModel>,
)

@Composable
fun DeparturesBottomSheet(
    model: DeparturesBottomSheetModel,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            model.header.Widget()
            model.departures.fastForEachIndexed { index, it ->
                it.Widget()
                if (index != model.departures.size - 1) {
                    Divider()
                }
            }
            if (model.departures.isEmpty()) {
                DeparturesEmptyRow()
            }
        }
    }
}