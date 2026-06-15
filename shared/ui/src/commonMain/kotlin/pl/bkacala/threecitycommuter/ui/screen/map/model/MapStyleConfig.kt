package pl.bkacala.threecitycommuter.ui.screen.map.model

/**
 * Configuration for map styles with token management.
 * 
 * This file provides a centralized way to configure map styles and their tokens.
 * In the future, this can be integrated with user preferences/settings.
 * 
 * USAGE:
 * ------
 * 1. For Stadia Maps:
 *    - Get a free token from https://stadiamaps.com/
 *    - Store it securely (e.g., in local.properties or BuildConfig)
 *    - Pass it to MapScreen: MapScreen(snackbarHostState, MapStyle.STADIA_SMOOTH, "your_token")
 * 
 * 2. For Mapbox:
 *    - Add MAPBOX_DOWNLOADS_TOKEN to gradle.properties
 *    - Use any Mapbox style: MapStyle.MAPBOX_STREETS
 * 
 * 3. For Demo/OSM:
 *    - No token needed: MapStyle.DEMO or MapStyle.OSM_RASTER
 */

// ============================================================================
// EXAMPLE USAGE IN FUTURE MENU
// ============================================================================
//
// @Composable
// fun MapStyleSelector(
//     selectedStyle: MapStyle,
//     onStyleSelected: (MapStyle) -> Unit,
//     stadiaToken: String?,
// ) {
//     val styles = MapStyle.entries.filter { 
//         !it.requiresToken || stadiaToken != null || it.tokenPlaceholder == null
//     }
//     
//     Column {
//         styles.forEach { style ->
//             RadioButton(
//                 selected = style == selectedStyle,
//                 onClick = { onStyleSelected(style) },
//                 label = style.displayName
//             )
//         }
//     }
// }
//
// ============================================================================

/**
 * Helper to get all available styles that can be used with the current configuration.
 */
fun getAvailableMapStyles(stadiaToken: String? = null): List<MapStyle> {
    return MapStyle.entries.filter { style ->
        when {
            // Demo and OSM styles don't need tokens
            !style.requiresToken -> true
            // Stadia styles need a token
            style.tokenPlaceholder != null && stadiaToken != null -> true
            // Mapbox styles are configured via gradle.properties
            style.tokenPlaceholder == null && style.requiresToken -> true
            else -> false
        }
    }
}

/**
 * Recommended styles for different use cases.
 */
val recommendedStreetStyles = listOf(
    MapStyle.STADIA_OSM_BRIGHT,
    MapStyle.STADIA_SMOOTH,
    MapStyle.STADIA_SMOOTH_DARK,
    MapStyle.MAPBOX_STREETS,
    MapStyle.MAPBOX_LIGHT,
    MapStyle.MAPBOX_DARK,
)

val recommendedFreeStyles = listOf(
    MapStyle.DEMO,
    MapStyle.OSM_RASTER,
    MapStyle.OSM_HUMANITARIAN,
)

/**
 * Extension function to check if a style is available with current configuration.
 */
fun MapStyle.isAvailable(stadiaToken: String? = null): Boolean {
    return when {
        !requiresToken -> true
        tokenPlaceholder != null && stadiaToken != null -> true
        tokenPlaceholder == null && requiresToken -> true // Mapbox via gradle
        else -> false
    }
}
