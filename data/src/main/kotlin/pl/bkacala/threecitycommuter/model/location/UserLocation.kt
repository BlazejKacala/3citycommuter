package pl.bkacala.threecitycommuter.model.location

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    /**
     * if true then real location isn't available and it's fixed on Gdańsk Główny station.
     */
    val isFixed: Boolean
) {
    companion object Factory {
        fun default(): UserLocation = UserLocation(
            latitude = 54.3552444,
            longitude = 18.6465378,
            isFixed = true
        )
    }
}

