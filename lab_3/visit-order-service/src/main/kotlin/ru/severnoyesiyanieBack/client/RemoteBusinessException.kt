package ru.severnoyesiyanieBack.client

class RemoteBusinessException(
    val status: Int,
    override val message: String,
    val contentType: String? = null
) : RuntimeException(message)
