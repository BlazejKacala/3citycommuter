package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.cinterop.refTo
import platform.Foundation.NSData

internal actual suspend fun downloadUrlToFile(
    httpClient: HttpClient,
    url: String,
    filePath: String,
) {
    val bytes = httpClient.get(url).body<ByteArray>()
    NSData.create(bytes = bytes.refTo(0), length = bytes.size.toULong())
        .writeToFile(filePath, atomically = true)
}
