package org.lagrange.dev.common

data class SignResult(
    val sign: ByteArray,
    val token: ByteArray,
    val extra: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignResult

        if (!sign.contentEquals(other.sign)) return false
        if (!token.contentEquals(other.token)) return false
        if (!extra.contentEquals(other.extra)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sign.contentHashCode()
        result = 31 * result + token.contentHashCode()
        result = 31 * result + extra.contentHashCode()
        return result
    }
}