package pl.bkacala.threecitycommuter.ui.screen.map

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.maps.android.compose.rememberCameraPositionState
import pl.bkacala.threecitycommuter.ui.screen.map.component.DeparturesBottomSheet

@Composable
fun MapScreen() {

    BoxWithConstraints {

        val viewModel = hiltViewModel<MapScreenViewModel>()
        val cameraPositionState = rememberCameraPositionState()

        Map(
            cameraPositionState = cameraPositionState,
            viewModel = viewModel
        )
        DeparturesBottomSheet(departures = )

    }

}