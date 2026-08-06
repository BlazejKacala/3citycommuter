package pl.bkacala.threecitycommuter.repository.stops

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

internal object StopCatalogCacheConfig {
    val refreshInterval: Duration = 1.days
}
