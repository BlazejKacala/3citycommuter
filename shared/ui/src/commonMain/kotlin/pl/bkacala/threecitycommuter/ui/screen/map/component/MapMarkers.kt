package pl.bkacala.threecitycommuter.ui.screen.map.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Tram
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun StationIcon(type: TransitStopMapItem.Type, isSelected: Boolean) {
    val busIcon = remember { Icons.Outlined.DirectionsBus }
    val tramIcon = remember { Icons.Outlined.Tram }
    val color =
        if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    MapMarkerBackground(color) {
        Row {
            Icon(
                imageVector = if (type == TransitStopMapItem.Type.Tram) tramIcon else busIcon,
                contentDescription = "Przystanek",
                tint = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )

            Canvas(
                modifier = Modifier
                    .height(22.dp)
                    .width(10.dp),
                onDraw = {
                    drawOval(
                        color = color,
                        size = Size(8.dp.toPx(), 8.dp.toPx()),
                        topLeft = Offset(0f, 4.dp.toPx()),
                    )
                    drawLine(
                        color = color,
                        start = Offset(4.dp.toPx(), 6.dp.toPx()),
                        end = Offset(4.dp.toPx(), 22.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                    )
                },
            )
        }
    }
}

@Composable
fun MapMarkerBackground(
    color: Color,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .background(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.background,
            )
            .border(
                width = 1.dp,
                shape = RoundedCornerShape(8.dp),
                color = color,
            )
            .padding(2.dp),
    ) {
        content()
    }
}

@Composable
fun ClusterMarker(size: Int) {
    val text = if (size < 100) size.toString() else "99+"

    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = CircleShape,
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            )
            .size(28.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
