package pl.bkacala.threecitycommuter.ui.screen.map.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import pl.bkacala.threecitycommuter.ui.theme.Padding

@Preview(showBackground = true)
@Composable
fun DeparturesEmptyRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Padding.large)
            .padding(bottom = Padding.large)
    ) {
        Text(text = "Brak odjazdów z tego przystanku w najbliższej przyszłości")
    }
}