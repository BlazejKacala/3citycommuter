package pl.bkacala.threecitycommuter.model.transit

object TransitStopId {
    private const val GDYNIA_OFFSET = 1_000_000

    fun toAppId(
        provider: TransitProvider,
        sourceStopId: Int,
    ): Int {
        return when (provider) {
            TransitProvider.GDANSK -> sourceStopId
            TransitProvider.GDYNIA -> GDYNIA_OFFSET + sourceStopId
        }
    }

    fun providerOf(appStopId: Int): TransitProvider {
        return if (appStopId >= GDYNIA_OFFSET) {
            TransitProvider.GDYNIA
        } else {
            TransitProvider.GDANSK
        }
    }

    fun sourceIdOf(appStopId: Int): Int {
        return when (providerOf(appStopId)) {
            TransitProvider.GDANSK -> appStopId
            TransitProvider.GDYNIA -> appStopId - GDYNIA_OFFSET
        }
    }
}
