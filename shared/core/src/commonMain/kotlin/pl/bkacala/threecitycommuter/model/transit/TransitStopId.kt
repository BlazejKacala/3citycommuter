package pl.bkacala.threecitycommuter.model.transit

object TransitStopId {
    private const val GDYNIA_OFFSET = 1_000_000
    private const val SKM_OFFSET = 2_000_000

    fun toAppId(
        provider: TransitProvider,
        sourceStopId: Int,
    ): Int {
        return when (provider) {
            TransitProvider.GDANSK -> sourceStopId
            TransitProvider.GDYNIA -> GDYNIA_OFFSET + sourceStopId
            TransitProvider.SKM -> SKM_OFFSET + sourceStopId
        }
    }

    fun providerOf(appStopId: Int): TransitProvider {
        return when {
            appStopId >= SKM_OFFSET -> TransitProvider.SKM
            appStopId >= GDYNIA_OFFSET -> TransitProvider.GDYNIA
            else -> TransitProvider.GDANSK
        }
    }

    fun sourceIdOf(appStopId: Int): Int {
        return when (providerOf(appStopId)) {
            TransitProvider.GDANSK -> appStopId
            TransitProvider.GDYNIA -> appStopId - GDYNIA_OFFSET
            TransitProvider.SKM -> appStopId - SKM_OFFSET
        }
    }
}
