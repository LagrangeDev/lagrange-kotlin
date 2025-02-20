package org.lagrange.dev.utils.ext

import io.ktor.utils.io.core.*

internal fun BytePacketBuilder.writeString(value: String, prefix: Prefix = Prefix.NONE) {
    this.writeLength(value.length.toUInt(), prefix)
    this.writeText(value)
}

internal fun BytePacketBuilder.writeBytes(value: ByteArray, prefix: Prefix = (Prefix.NONE)) {
    this.writeLength(value.size.toUInt(), prefix)
    this.writeFully(value)
}

internal fun BytePacketBuilder.barrier(target: ((BytePacketBuilder).() -> Unit), prefix: Prefix, addition: Int = 0) {
    val written = BytePacketBuilder()
    target(written)
    
    writeLength(written.size.toUInt() + addition.toUInt(), prefix)
    writePacket(written.build())
}

internal fun ByteReadPacket.readString(prefix: Prefix): String {
    val length = readLength(prefix)
    return this.readBytes(length.toInt()).toString(Charsets.UTF_8)
}

internal fun ByteReadPacket.readBytes(prefix: Prefix): ByteArray {
    val length = readLength(prefix)
    return this.readBytes(length.toInt())
}

private fun BytePacketBuilder.writeLength(length: UInt, prefix: Prefix) {
    val prefixLength = prefix.getPrefixLength()
    val includePrefix = prefix.isIncludePrefix()
    val writtenLength = length + (if (includePrefix) prefixLength else 0).toUInt()
    
    when (prefixLength) {
        1 -> this.writeByte(writtenLength.toByte())
        2 -> this.writeUShort(writtenLength.toUShort())
        4 -> this.writeUInt(writtenLength)
        else -> {}
    }
}

private fun ByteReadPacket.readLength(prefix: Prefix): UInt {
    val prefixLength = prefix.getPrefixLength()
    val includePrefix = prefix.isIncludePrefix()
    
    return when (prefixLength) {
        1 -> this.readByte().toUInt() - (if (includePrefix) prefixLength else 0).toUInt()
        2 -> this.readUShort().toUInt() - (if (includePrefix) prefixLength else 0).toUInt()
        4 -> this.readUInt() - (if (includePrefix) prefixLength else 0).toUInt()
        else -> 0u
    }
}

fun ByteReadPacket.readShortLittleEndian(): Short {
    val value = this.readShort()
    return if (value.toInt() < 0) (value.toInt() + Short.MAX_VALUE * 2).toShort() else value
}

fun ByteReadPacket.readIntLittleEndian(): Int {
    val value = this.readInt()
    return if (value < 0) (value + Int.MAX_VALUE * 2) else value
}