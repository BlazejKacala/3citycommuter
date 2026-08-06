package pl.bkacala.threecitycommuter.resource

actual fun readBundledResourceText(resourceName: String): String =
    error("Bundled resource reading is not implemented on iOS for $resourceName")
