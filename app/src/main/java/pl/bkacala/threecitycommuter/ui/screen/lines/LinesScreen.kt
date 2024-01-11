package pl.bkacala.threecitycommuter.ui.screen.lines

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun LinesScreen() {
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Tu będzie lista linii autobusowych i tramwajowych",
            modifier = Modifier.align(Alignment.Center)
        )
    }
}