package pl.bkacala.threecitycommuter.ui.screen.map.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pl.bkacala.threecitycommuter.ui.theme.Padding

@Composable
fun DepartureSkeletonRow() {
    val color = Color.LightGray.copy(alpha = 0.45f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Padding.big, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp, 20.dp)
                .background(color, RoundedCornerShape(4.dp)),
        )
        Box(
            modifier = Modifier
                .width(92.dp)
                .size(92.dp, 20.dp)
                .background(color, RoundedCornerShape(4.dp)),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .size(160.dp, 20.dp)
                .background(color, RoundedCornerShape(4.dp)),
        )
    }
}
