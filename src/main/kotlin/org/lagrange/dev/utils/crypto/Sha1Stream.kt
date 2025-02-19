package org.lagrange.dev.utils.crypto

class Sha1Stream {
    private var state = IntArray(5)
    private var count = IntArray(2)
    private var buffer = ByteArray(SHA1_BLOCK_SIZE)

    companion object {
        const val SHA1_BLOCK_SIZE = 64
        const val SHA1_DIGEST_SIZE = 20
        
        private val PADDING = byteArrayOf(
            0x80.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
    }

    init {
        reset()
    }
    
    private fun reset() {
        state[0] = 0x67452301
        state[1] = 0xEFCDAB89.toInt()
        state[2] = 0x98BADCFE.toInt()
        state[3] = 0x10325476
        state[4] = 0xC3D2E1F0.toInt()
        count[0] = 0
        count[1] = 0
    }

    private fun transform(data: ByteArray) {
        require(data.size == 64) { "Data must be exactly 64 bytes" }

        val w = IntArray(80)
        for (i in 0 until 16) {
            w[i] = (data[i * 4 + 0].toInt() and 0xFF shl 24) or
                    (data[i * 4 + 1].toInt() and 0xFF shl 16) or
                    (data[i * 4 + 2].toInt() and 0xFF shl 8) or
                    (data[i * 4 + 3].toInt() and 0xFF)
        }

        for (i in 16 until 80) {
            w[i] = (w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16])
            w[i] = (w[i] shl 1) or (w[i] ushr 31)
        }

        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]

        for (i in 0 until 80) {
            var temp = when {
                i < 20 -> ((b and c) or ((b.inv()) and d)) + 0x5A827999
                i < 40 -> (b xor c xor d) + 0x6ED9EBA1
                i < 60 -> (((b and c) or (b and d) or (c and d)) + 0x8F1BBCDC).toInt()
                else -> ((b xor c xor d) + 0xCA62C1D6).toInt()
            }

            temp += ((a shl 5) or (a ushr 27)) + e + w[i]
            e = d
            d = c
            c = (b shl 30) or (b ushr 2)
            b = a
            a = temp
        }

        state[0] += a
        state[1] += b
        state[2] += c
        state[3] += d
        state[4] += e
    }

    private fun update(data: ByteArray, len: Int) {
        var index = (count[0] ushr 3) and 0x3F
        count[0] = count[0] + (len shl 3)

        if (count[0] < (len shl 3)) {
            count[1]++
        }

        count[1] += len ushr 29

        val partLen = SHA1_BLOCK_SIZE - index
        var i: Int
        if (len >= partLen) {
            System.arraycopy(data, 0, buffer, index, partLen)
            transform(buffer)

            i = partLen
            while (i + SHA1_BLOCK_SIZE <= len) {
                transform(data.sliceArray(IntRange(i,i + SHA1_BLOCK_SIZE - 1)))
                i += SHA1_BLOCK_SIZE
            }

            index = 0
        } else {
            i = 0
        }

        System.arraycopy(data, i, buffer, index, len - i)
    }
    
    fun hash(digest: ByteArray, bigEndian: Boolean) = if (bigEndian) {
        for (i in state.indices) {
            digest[i * 4 + 0] = (state[i] shr 24).toByte()
            digest[i * 4 + 1] = (state[i] shr 16 and 0xFF).toByte()
            digest[i * 4 + 2] = (state[i] shr 8 and 0xFF).toByte()
            digest[i * 4 + 3] = (state[i] and 0xFF).toByte()
        }
    } else {
        for (i in state.indices) {
            digest[i * 4 + 0] = (state[i] and 0xFF).toByte()
            digest[i * 4 + 1] = (state[i] shr 8 and 0xFF).toByte()
            digest[i * 4 + 2] = (state[i] shr 16 and 0xFF).toByte()
            digest[i * 4 + 3] = (state[i] shr 24).toByte()
        }
    }
    
    fun final(digest: ByteArray) {
        require(digest.size == SHA1_DIGEST_SIZE) { "Digest array must be of size $SHA1_DIGEST_SIZE" }

        val bits = ByteArray(8)
        for (i in 0..7) {
            bits[i] = (count[if (i >= 4) 0 else 1] ushr ((3 - (i and 3)) * 8)).toByte()
        }

        val index = (count[0] ushr 3) and 0x3F
        val padLen = if (index < 56) 56 - index else 120 - index

        update(PADDING, padLen)
        update(bits, 8)

        for (i in 0 until SHA1_DIGEST_SIZE) {
            digest[i] = (state[i shr 2] ushr ((3 - (i and 3)) * 8)).toByte()
        }
    }
}