package org.lagrange.dev.network

internal data class OidbResponse(
    val retCode: Int,
    val errorMsg: String,
    val payload: ByteArray,
)
