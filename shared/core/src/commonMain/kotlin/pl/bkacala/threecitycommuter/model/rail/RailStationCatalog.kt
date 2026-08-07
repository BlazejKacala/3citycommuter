package pl.bkacala.threecitycommuter.model.rail

/** Provides the active rail station catalog used by the PLK-backed data source. */
interface RailStationCatalog {
    suspend fun getActiveStations(): List<RailStationSeed>
}
