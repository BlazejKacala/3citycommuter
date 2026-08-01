package pl.bkacala.threecitycommuter.client

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import java.io.FileOutputStream

internal actual suspend fun downloadUrlToFile(
    httpClient: HttpClient,
    url: String,
    filePath: String,
) {
    httpClient.prepareGet(url).execute { response ->
        val channel = response.bodyAsChannel()
        FileOutputStream(filePath).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                if (bytesRead <= 0) break
                output.write(buffer, 0, bytesRead)
            }
        }
    }
}
