package pl.bkacala.threecitycommuter.ui.screen.map.model

/**
 * Available map styles for the application.
 * Each style has a display name and a URI that can be used with MaplibreMap.
 *
 * NOTE: For styles requiring tokens (Stadia), you need to:
 * 1. Obtain a token from the respective provider
 * 2. Add it to your local.properties or build configuration
 * 3. Replace the placeholder in the URI
 */
enum class MapStyle(
    val displayName: String,
    val styleUri: String,
    val requiresToken: Boolean = false,
    val tokenPlaceholder: String? = null,
    val defaultZoom: Float = 14.0f,
) {
    // No token required - demo tiles (countries only, zoom 0-6)
    DEMO(
        displayName = "Demo (Countries)",
        styleUri = "https://demotiles.maplibre.org/style.json",
        requiresToken = false,
        // Demo tiles only work well at low zoom
        defaultZoom = 6.0f,
    ),

    // Stadia Maps - requires token (free tier available)
    // Get token at: https://stadiamaps.com/
    // Replace YOUR_STADIA_TOKEN with your actual token
    STADIA_SMOOTH(
        displayName = "Stadia Smooth",
        styleUri = "https://tiles.stadiamaps.com/styles/alidade_smooth.json?access_token=YOUR_STADIA_TOKEN",
        requiresToken = true,
        tokenPlaceholder = "YOUR_STADIA_TOKEN",
        defaultZoom = 14.0f,
    ),

    STADIA_SMOOTH_DARK(
        displayName = "Stadia Smooth Dark",
        styleUri = "https://tiles.stadiamaps.com/styles/alidade_smooth_dark.json?access_token=YOUR_STADIA_TOKEN",
        requiresToken = true,
        tokenPlaceholder = "YOUR_STADIA_TOKEN",
        defaultZoom = 14.0f,
    ),

    STADIA_OSM_BRIGHT(
        displayName = "Stadia OSM Bright",
        styleUri = "https://tiles.stadiamaps.com/styles/osm_bright.json?access_token=YOUR_STADIA_TOKEN",
        requiresToken = true,
        tokenPlaceholder = "YOUR_STADIA_TOKEN",
        defaultZoom = 14.0f,
    ),

    // OpenStreetMap raster tiles - no token required, but less interactive
    OSM_RASTER(
        displayName = "OpenStreetMap (Raster)",
        styleUri = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
        requiresToken = false,
        defaultZoom = 14.0f,
    ),

    OSM_HUMANITARIAN(
        displayName = "OSM Humanitarian",
        styleUri = "https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png",
        requiresToken = false,
        defaultZoom = 14.0f,
    ),
    ;

    /**
     * Get the style URI with token replaced if needed.
     * For Stadia, replace the placeholder with your actual token.
     */
    fun getUriWithToken(token: String? = null): String {
        return if (requiresToken && tokenPlaceholder != null && token != null) {
            styleUri.replace(tokenPlaceholder, token)
        } else {
            styleUri
        }
    }
}
