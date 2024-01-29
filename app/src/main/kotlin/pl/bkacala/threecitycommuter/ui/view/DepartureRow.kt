package pl.bkacala.threecitycommuter.ui.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsBike
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pl.bkacala.threecitycommuter.ui.theme.Padding

@Stable
data class DepartureRowModel(
    val isNear: Boolean,
    val vehicleType: VehicleType,
    val lineNumber: String,
    val direction: String,
    val departureTime: String,
    val disabledFriendly: Boolean,
    val bikesAllowed: Boolean
) {
    enum class VehicleType { Bus, Tram }
}

@Composable
fun DepartureRowModel.Widget() {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(
            vertical = Padding.small,
            horizontal = Padding.normal
        )
    ) {

        VehicleImage(vehicleType)
        Spacer(modifier = Modifier.width(Padding.small))
        LineNumber(lineNumber)
        Spacer(modifier = Modifier.width(Padding.normal))
        Direction(direction)
        if (disabledFriendly) {
            Spacer(modifier = Modifier.width(Padding.small))
            DisabledFriendlyIcon()
        }
        if (bikesAllowed) {
            Spacer(modifier = Modifier.width(Padding.small))
            BikesAllowedIcon()
        }
        Spacer(modifier = Modifier.width(Padding.normal))
        DepartureTime(isNear, departureTime)

    }
}

@Composable
private fun DepartureTime(isNear: Boolean, departureTime: String) {

    val isVisible = remember(departureTime) { mutableStateOf(true) }

    if(isNear) {
        LaunchedEffect(departureTime) {
            while(true) {
                delay(300)
                isVisible.value = !isVisible.value
            }
        }
    }

    Box(modifier = Modifier.width(45.dp)) {
        if(isVisible.value) {
            Text(
                text = departureTime, style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun BikesAllowedIcon() {
    Icon(
        imageVector = Icons.Rounded.DirectionsBike,
        contentDescription = "Można wsiąść z rowerem",
        modifier = Modifier.size(12.dp)
    )
}

@Composable
private fun DisabledFriendlyIcon() {
    Icon(
        imageVector = Icons.Rounded.WheelchairPickup,
        contentDescription = "Dosotosowany dla niepełnosprawnych",
        modifier = Modifier.size(12.dp)
    )
}

@Composable
private fun RowScope.Direction(direction: String) {
    Text(
        text = direction, style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.primary
        ),
        maxLines = 2,
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun LineNumber(lineNumber: String) {
    Text(
        text = lineNumber, style = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun VehicleImage(vehicleType: DepartureRowModel.VehicleType) {
    val image = when (vehicleType) {
        DepartureRowModel.VehicleType.Bus -> Icons.Sharp.DirectionsBusFilled
        DepartureRowModel.VehicleType.Tram -> Icons.Sharp.Tram
    }

    Icon(
        imageVector = image,
        contentDescription = "typ pojazdu",
        modifier = Modifier.size(18.dp)
    )
}

@Preview(showBackground = true)
@Composable
private fun DepartureRowPreview() {
    DepartureRowModel(
        isNear = true,
        vehicleType = DepartureRowModel.VehicleType.Bus,
        lineNumber = "154",
        bikesAllowed = true,
        disabledFriendly = true,
        departureTime = "Teraz",
        direction = "Orunia Górna"
    ).Widget()

}

