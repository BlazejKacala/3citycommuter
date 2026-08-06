package pl.bkacala.threecitycommuter.resource

import kotlinx.serialization.json.Json
import pl.bkacala.threecitycommuter.model.rail.RailStationSeed

private const val RAIL_STATIONS_SEED_RESOURCE = "rail_stations.json"

expect fun readBundledResourceText(resourceName: String): String

fun loadRailStationsSeed(json: Json): List<RailStationSeed> =
    json.decodeFromString(readBundledResourceText(RAIL_STATIONS_SEED_RESOURCE))
