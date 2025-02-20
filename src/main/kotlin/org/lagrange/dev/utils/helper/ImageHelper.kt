package org.lagrange.dev.utils.helper

import io.ktor.utils.io.core.*
import org.lagrange.dev.utils.ext.seekBeginning
import java.io.InputStream

object ImageHelper {
    fun resolve(image: InputStream): Pair<ImageFormat, Vector2> {
        val buffer = ByteArray(32)
        image.read(buffer)
        image.seekBeginning()

        val packet = ByteReadPacket(buffer)

        // GIF check
        return when {
            buffer.copyOfRange(0, 6).contentEquals(byteArrayOf(0x47.toByte(), 0x49.toByte(), 0x46.toByte(), 0x38.toByte(), 0x39.toByte(), 0x61.toByte())) || buffer.copyOfRange(0, 6).contentEquals(byteArrayOf(0x47.toByte(), 0x49.toByte(), 0x46.toByte(), 0x38.toByte(), 0x37.toByte(), 0x61.toByte())) -> { // GIF89a / GIF87a
                val width = packet.readShortLittleEndian().toInt()
                val height = packet.readShortLittleEndian().toInt()
                ImageFormat.Gif to Vector2(width, height)
            }

            // JPEG check
            buffer.copyOfRange(0, 2).contentEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) -> { // JPEG
                var size = Vector2(0, 0)
                while (packet.remaining > 10) {
                    val marker = packet.readShort().toInt() and 0xFCFF
                    if (marker == 0xC0FF) { // SOF0 ~ SOF3
                        packet.discard(3) // Skip 3 bytes of header
                        val width = packet.readShort().toInt()
                        val height = packet.readShort().toInt()
                        size = Vector2(width, height)
                        break
                    } else {
                        val length = packet.readShort().toInt() - 2
                        packet.discard(length) // Skip the block
                    }
                }
                ImageFormat.Jpeg to size
            }

            // PNG check
            buffer.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte())) -> { // PNG
                packet.discard(16) // Skip the PNG header
                val width = packet.readInt().toInt()
                val height = packet.readInt().toInt()
                ImageFormat.Png to Vector2(width, height)
            }

            // WEBP check
            buffer.copyOfRange(0, 4).contentEquals(byteArrayOf(0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte())) && buffer.copyOfRange(8, 12).contentEquals(byteArrayOf(0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte())) -> { // RIFF WEBP
                if (buffer.copyOfRange(12, 16).contentEquals(byteArrayOf(0x56.toByte(), 0x50.toByte(), 0x38.toByte(), 0x58.toByte()))) { // VP8X
                    val width = packet.readShortLittleEndian().toInt() + 1
                    val height = packet.readShortLittleEndian().toInt() + 1
                    ImageFormat.Webp to Vector2(width, height)
                } else if (buffer.copyOfRange(12, 16).contentEquals(byteArrayOf(0x56.toByte(), 0x50.toByte(), 0x38.toByte(), 0x4C.toByte()))) { // VP8L
                    val sizeInfo = packet.readIntLittleEndian()
                    val width = (sizeInfo and 0x3FFF) + 1
                    val height = (sizeInfo shr 14 and 0x3FFF) + 1
                    ImageFormat.Webp to Vector2(width, height)
                } else { // VP8
                    val width = packet.readShortLittleEndian().toInt()
                    val height = packet.readShortLittleEndian().toInt()
                    ImageFormat.Webp to Vector2(width, height)
                }
            }

            // BMP check
            buffer.copyOfRange(0, 2).contentEquals(byteArrayOf(0x42.toByte(), 0x4D.toByte())) -> { // BMP
                packet.discard(18) // Skip header
                val width = packet.readShortLittleEndian().toInt()
                val height = packet.readShortLittleEndian().toInt()
                ImageFormat.Bmp to Vector2(width, height)
            }

            // TIFF check
            buffer.copyOfRange(0, 2).contentEquals(byteArrayOf(0x49.toByte(), 0x49.toByte())) || buffer.copyOfRange(0, 2).contentEquals(byteArrayOf(0x4D.toByte(), 0x4D.toByte())) -> { // TIFF
                packet.discard(18) // Skip header
                val width = packet.readShortLittleEndian().toInt()
                val height = packet.readShortLittleEndian().toInt()
                ImageFormat.Tiff to Vector2(width, height)
            }
            else -> {
                ImageFormat.Unknown to Vector2(0, 0)
            }
        }
    }

    enum class ImageFormat(val value: Int) {
        Unknown(0),
        Png(1001),
        Jpeg(1000),
        Gif(2000),
        Webp(1002),
        Bmp(1005),
        Tiff(1006)
    }

    data class Vector2(
        val x: Int,
        val y: Int
    )
}
