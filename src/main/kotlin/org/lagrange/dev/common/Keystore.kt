package org.lagrange.dev.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.lagrange.dev.utils.crypto.ECDH
import org.lagrange.dev.utils.crypto.ecdh.EllipticCurve
import org.lagrange.dev.utils.ext.toHex
import kotlin.random.Random

@Serializable
data class Keystore(
    @JvmField var uin: Long,
    @JvmField var uid: String,

    var a2: ByteArray,
    var d2: ByteArray,
    var d2Key: ByteArray,
    var tgtgt: ByteArray,
    var encryptedA1: ByteArray,
    var noPicSig: ByteArray,

    var qrSig: ByteArray,

    val guid: ByteArray,
    val deviceName: String,
) { 
    @Transient internal val ecdh192: ECDH = ECDH(EllipticCurve.secp192k1)
    @Transient internal val ecdh256: ECDH = ECDH(EllipticCurve.prime256v1)

    @Transient internal var keySig: ByteArray? = null
    @Transient internal var exchangeKey: ByteArray? = null
    @Transient internal var unusualCookies: String? = null
    
    companion object {
        fun generateEmptyKeystore(): Keystore {
            return Keystore(
                uin =  0,
                uid =  "",
                a2 =  ByteArray(0),
                d2 =  ByteArray(0),
                d2Key = ByteArray(16),
                tgtgt =  ByteArray(0),
                encryptedA1 = ByteArray(0),
                noPicSig = ByteArray(0),
                qrSig = ByteArray(0),
                guid =  Random.nextBytes(16),
                deviceName = "Lagrange-${Random.nextBytes(3).toHex()}")
        }
    }
    
    internal fun clear() {
        a2 = ByteArray(0)
        d2 = ByteArray(0)
        d2Key = ByteArray(16)
        tgtgt = ByteArray(0)
        encryptedA1 = ByteArray(0)
        noPicSig = ByteArray(0)
        qrSig = ByteArray(0)
        keySig = null
        exchangeKey = null
        unusualCookies = null
    }
}