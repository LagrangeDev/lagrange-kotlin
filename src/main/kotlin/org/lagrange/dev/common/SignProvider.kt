package org.lagrange.dev.common

@FunctionalInterface
interface SignProvider {
    fun sign(cmd: String, seq: Int, src: ByteArray): SignResult
}