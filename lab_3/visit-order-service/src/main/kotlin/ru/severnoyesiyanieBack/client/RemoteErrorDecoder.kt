package ru.severnoyesiyanieBack.client

import feign.Response
import feign.codec.ErrorDecoder
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

@Component
class RemoteErrorDecoder : ErrorDecoder {
    private val defaultDecoder = ErrorDecoder.Default()

    override fun decode(methodKey: String, response: Response): Exception {
        if (response.status() in 400..499) {
            val message = response.body()?.asReader(StandardCharsets.UTF_8)?.use { it.readText() }
                ?.takeIf { it.isNotBlank() } ?: "Remote request was rejected"
            val contentType = response.headers().entries
                .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value?.firstOrNull()
            return RemoteBusinessException(response.status(), message, contentType)
        }
        return defaultDecoder.decode(methodKey, response)
    }
}
