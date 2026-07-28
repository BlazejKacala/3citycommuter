package pl.bkacala.threecitycommuter.ui.screen.map.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.WheelchairPickup
import androidx.compose.material.icons.sharp.DirectionsBusFilled
import androidx.compose.material.icons.sharp.Tram
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pl.bkacala.threecitycommuter.ui.theme.Padding

@Stable
data class DepartureRowModel(
    val departureKey: String,
    val isNear: Boolean,
    val vehicleType: VehicleType,
    val lineNumber: String,
    val direction: String,
    val departureTime: String,
    val disabledFriendly: Boolean,
    val bikesAllowed: Boolean,
    val showGpsIndicator: Boolean,
    val gpsPosition: Boolean,
    val isSelected: Boolean,
    val vehicleId: Long?,
    val routeId: Int,
    val tripId: Int,
)

@Composable
fun DepartureRowModel.Widget(
    accentColor: Color,
    onSelected: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(58.dp)
            .clickable {
                onSelected(departureKey)
            },
    ) {
        Selection(
            selected = isSelected,
            accentColor = accentColor,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(
                    top = Padding.small,
                    bottom = Padding.small,
                    start = Padding.large,
                    end = Padding.normal,
                ),
        ) {
            VehicleImage(vehicleType)
            Spacer(modifier = Modifier.width(Padding.large))
            LineNumber(lineNumber)
            Spacer(modifier = Modifier.width(Padding.large))
            Direction(direction)
            if (disabledFriendly) {
                Spacer(modifier = Modifier.width(Padding.normal))
                DisabledFriendlyIcon()
            }
            if (bikesAllowed) {
                Spacer(modifier = Modifier.width(Padding.normal))
                BikesAllowedIcon()
            }
            Spacer(modifier = Modifier.width(Padding.big))
            Spacer(modifier = Modifier.width(Padding.normal))
            DepartureTime(this@Widget)
            GPSIcon(
                showGpsIndicator = showGpsIndicator,
                gpsPosition = gpsPosition,
                accentColor = accentColor,
            )
        }
    }
}

@Composable
private fun Selection(
    selected: Boolean,
    accentColor: Color,
) {
    Box(
        modifier = Modifier
            .width(2.dp)
            .fillMaxHeight()
            .background(color = if (selected) accentColor else Color.Transparent),
    )
}

@Composable
private fun DepartureTime(model: DepartureRowModel) {
    val isVisible = remember(model) { mutableStateOf(true) }

    if (model.isNear) {
        LaunchedEffect(model) {
            while (true) {
                delay(300)
                isVisible.value = !isVisible.value
            }
        }
    }

    Box(modifier = Modifier.width(65.dp)) {
        if (isVisible.value) {
            Text(
                text = model.departureTime,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun GPSIcon(
    showGpsIndicator: Boolean,
    gpsPosition: Boolean,
    accentColor: Color,
) {
    if (!showGpsIndicator) {
        return
    }

    Icon(
        imageVector = if (gpsPosition) Icons.Outlined.GpsFixed else Icons.Rounded.GpsNotFixed,
        contentDescription = "Czy pozycja pojazdu jest dostępna",
        modifier = Modifier.size(24.dp),
        tint = if (gpsPosition) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BikesAllowedIcon() {
    Icon(
        imageVector = Icons.Rounded.DirectionsBike,
        contentDescription = "Można wsiąść z rowerem",
        modifier = Modifier.size(12.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DisabledFriendlyIcon() {
    Icon(
        imageVector = Icons.Rounded.WheelchairPickup,
        contentDescription = "Dostosowany dla niepełnosprawnych",
        modifier = Modifier.size(12.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RowScope.Direction(direction: String) {
    Text(
        text = direction,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun LineNumber(lineNumber: String) {
    Text(
        text = lineNumber,
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun VehicleImage(vehicleType: VehicleType) {
    val image = when (vehicleType) {
        VehicleType.Bus -> Icons.Sharp.DirectionsBusFilled
        VehicleType.Tram -> Icons.Sharp.Tram
    }

    Icon(
        imageVector = image,
        contentDescription = "typ pojazdu",
        modifier = Modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
