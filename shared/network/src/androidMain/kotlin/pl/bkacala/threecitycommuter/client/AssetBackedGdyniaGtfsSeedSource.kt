package pl.bkacala.threecitycommuter.client

import android.content.Context
import java.util.zip.GZIPInputStream

internal class AssetBackedGdyniaGtfsSeedSource(
    private val context: Context,
) : GdyniaGtfsSeedSource {
    override suspend fun readSeedDepartureMatchIndex(): String? =
        runCatching {
            context.assets.open(DEPARTURE_MATCH_INDEX_ASSET_PATH).use { input ->
                GZIPInputStream(input).bufferedReader().use { it.readText() }
            }
        }.getOrNull()

    override suspend fun readSeedShapeIndex(): String? =
        runCatching {
            context.assets.open(SHAPE_INDEX_ASSET_PATH).use { input ->
                GZIPInputStream(input).bufferedReader().use { it.readText() }
            }
        }.getOrNull()
}

private const val DEPARTURE_MATCH_INDEX_ASSET_PATH = "gdynia/departure_match_index.json.gz"
private const val SHAPE_INDEX_ASSET_PATH = "gdynia/shape_index.json.gz"
