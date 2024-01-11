package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun MapScreen() {
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Tu będzie mapa z listą przystanków",
            modifier = Modifier.align(Alignment.Center)
        )
    }
}