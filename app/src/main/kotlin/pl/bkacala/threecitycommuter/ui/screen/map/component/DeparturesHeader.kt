package pl.bkacala.threecitycommuter.ui.screen.map.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import pl.bkacala.threecitycommuter.ui.theme.Padding

@Stable
data class DeparturesHeaderModel(
    val busStopName: String,
    val isForDemand: Boolean
)

@Composable
fun DeparturesHeaderModel.Widget() {

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(Padding.large)) {
        Text(text = busStopName, style = MaterialTheme.typography.headlineSmall)
        if(isForDemand) {
            Text(text = "na żądanie")
        }
    }

}

@Preview(showBackground = true)
@Composable
fun DeparturesHeaderWidgetPreview() {
    DeparturesHeaderModel("Muzeum II Wojny Światowej", true).Widget()
}