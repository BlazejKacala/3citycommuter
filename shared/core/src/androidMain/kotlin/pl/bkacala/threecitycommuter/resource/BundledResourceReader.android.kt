package pl.bkacala.threecitycommuter.resource

actual fun readBundledResourceText(resourceName: String): String {
    val resourceStream = RailSeedResourceAnchor::class.java.classLoader
        ?.getResourceAsStream(resourceName)
        ?: error("Missing bundled resource: $resourceName")
    return resourceStream.bufferedReader().use { it.readText() }
}

private class RailSeedResourceAnchor
