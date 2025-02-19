package org.lagrange.dev.packet.login

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.readBytes
import org.lagrange.dev.common.AppInfo
import org.lagrange.dev.common.Keystore
import org.lagrange.dev.utils.crypto.TEA
import org.lagrange.dev.utils.ext.*
import org.lagrange.dev.utils.proto.ProtoUtils
import org.lagrange.dev.utils.proto.asUtf8String
import kotlin.random.Random

@OptIn(ExperimentalUnsignedTypes::class)
internal class wtlogin(
    private val keystore: Keystore, 
    private val appInfo: AppInfo
) {
    
    private val ecdhKey = "04928D8850673088B343264E0C6BACB8496D697799F37211DEB25BB73906CB089FEA9639B4E0260498B51A992D50813DA8".fromHex()
    
    fun buildTransEmp0x31(): ByteArray {
        val tlvs = TlvQrCode(keystore, appInfo).apply {
            tlv16()
            tlv1b()
            tlv1d()
            tlv33()
            tlv35()
            tlv66()
            tlvD1()
        }

        val packet = BytePacketBuilder().apply {
            writeUShort(0u)
            writeUInt(appInfo.appId.toUInt())
            writeULong(0u) // uin
            writeBytes(ByteArray(0))
            writeByte(0)
            writeBytes(ByteArray(0), Prefix.UINT_16 or Prefix.LENGTH_ONLY)
            writeBytes(tlvs.build())
        }

        return buildCode2DPacket(packet.build().readBytes(), 0x31u)
    }
    
    fun buildTransEmp0x12(): ByteArray {
        val packet = BytePacketBuilder().apply {
            writeUShort(0u)
            writeUInt(appInfo.appId.toUInt())
            writeBytes(keystore.qrSig, Prefix.UINT_16 or Prefix.LENGTH_ONLY)
            writeULong(0u) // uin
            writeByte(0)
            writeBytes(ByteArray(0), Prefix.UINT_16 or Prefix.LENGTH_ONLY)
            writeUShort(0u)  // actually it is the tlv count, but there is no tlv so 0x0 is used
        }
        
        return buildCode2DPacket(packet.build().readBytes(), 0x12u)
    }

    fun buildLogin(): ByteArray {
        val tlvs = Tlv(keystore, appInfo).apply {
            tlv106A2()
            tlv144()
            tlv116()
            tlv142()
            tlv145()
            tlv18()
            tlv141()
            tlv177()
            tlv191()
            tlv100()
            tlv107()
            tlv318()
            tlv16a()
            tlv166()
            tlv521()
        }

        val packet = BytePacketBuilder().apply {
            writeUShort(9u) // internal command
            writeFully(tlvs.build())
        }

        return buildWtLogin(packet.build().readBytes(), 2064u)
    }

    fun parseTransEmp0x31(raw: ByteArray): Map<UShort, ByteArray> {
        val wtlogin = parseWtLogin(raw)
        val code2d = parseCode2DPacket(wtlogin)
        
        val reader = ByteReadPacket(code2d)
        reader.discard(1)
        
        val sig = reader.readBytes(Prefix.UINT_16 or Prefix.LENGTH_ONLY)
        val tlv = readTlv(reader)
        keystore.qrSig = sig
        
        return tlv
    }

    fun parseTransEmp0x12(raw: ByteArray): QrCodeState {
        val wtlogin = parseWtLogin(raw)
        val code2d = parseCode2DPacket(wtlogin)
        
        val reader = ByteReadPacket(code2d)
        val retCode = QrCodeState(reader.readByte())
        if (retCode.value == QrCodeState.Confirmed.value) {
            reader.discard(4)
            keystore.uin = reader.readUInt().toLong()
            reader.discard(4)
            
            val tlv = readTlv(reader)
            keystore.tgtgt = tlv[0x1eu]!!
            keystore.encryptedA1 = tlv[0x18u]!!
            keystore.noPicSig = tlv[0x19u]!!
        }
        
        return retCode
    }
    
    fun parseLogin(raw: ByteArray): Boolean {
        val wtlogin = parseWtLogin(raw)
        val reader = ByteReadPacket(wtlogin)
        
        val command = reader.readUShort()
        val state = reader.readUByte()
        val tlv119Reader = readTlv(reader)
        
        if (state.toInt() == 0) {
            val tlv119 = tlv119Reader[0x119u]!!
            val tlvs = readTlv(ByteReadPacket(TEA.decrypt(tlv119, keystore.tgtgt)))
            keystore.apply { 
                d2Key = tlvs[0x305u]!!
                uid = ProtoUtils.decodeFromByteArray(tlvs[0x543u]!!)[9][11][1].asUtf8String
                a2 = tlvs[0x10Au]!!
                d2 = tlvs[0x143u]!!
                encryptedA1 = tlvs[0x106u]!!
            }
            return true
        } else {
            val tlv146 = ByteReadPacket(tlv119Reader[0x146u]!!)
            val state = tlv146.readUInt()
            val tag = tlv146.readString(Prefix.UINT_16 or Prefix.LENGTH_ONLY)
            val message = tlv146.readString(Prefix.UINT_16 or Prefix.LENGTH_ONLY)
            
            println("Login failed: $state, $tag, $message")
        }
        
        return false
    }

    private fun buildCode2DPacket(tlvs: ByteArray, command: UShort): ByteArray {
        val newPacket = BytePacketBuilder().apply {
            writeByte(0x2) // packet Start
            writeUShort((43 + tlvs.size + 1).toUShort()) // _head_len = 43 + data.size +1
            writeUShort(command)
            writeFully(ByteArray(21))
            writeByte(0x3)
            writeShort(0x0) // close
            writeShort(0x32) // Version Code: 50
            writeUInt(0u) // trans_emp sequence
            writeULong(0.toULong()) // dummy uin
            writeFully(tlvs)
            writeByte(0x3)
        }

        val requestBody = BytePacketBuilder().apply {
            writeUInt((System.currentTimeMillis() / 1000).toUInt())
            writeFully(newPacket.build().readBytes())
        }

        val packet = BytePacketBuilder().apply {
            writeByte(0x0) // encryptMethod == EncryptMethod.EM_ST || encryptMethod == EncryptMethod.EM_ECDH_ST
            writeUShort(requestBody.size.toUShort())
            writeInt(appInfo.appId) // TODO: AppInfo.AppId
            writeInt(0x72) // Role
            writeBytes(ByteArray(0), Prefix.UINT_16 or Prefix.LENGTH_ONLY) // uSt
            writeBytes(ByteArray(0), Prefix.UINT_8 or Prefix.LENGTH_ONLY) // rollback
            writeFully(requestBody.build().readBytes())
        }

        return buildWtLogin(packet.build().readBytes(), 2066u)
    }
    
    private fun parseCode2DPacket(wtlogin: ByteArray): ByteArray {
        val reader = ByteReadPacket(wtlogin)
        
        val packetLength = reader.readUInt()
        reader.discard(4)
        val command = reader.readUShort()
        reader.discard(40)
        val appId = reader.readUInt()
        
        return reader.readBytes(reader.remaining.toInt())
    }
    
    private fun buildWtLogin(payload: ByteArray, command: UShort): ByteArray {
        val encrypted = TEA.encrypt(payload, keystore.ecdh192.keyExchange(ecdhKey, true))
        val packet = BytePacketBuilder()
        packet.writeByte(2)
        packet.barrier({
            writeUShort(8001u)
            writeUShort(command)
            writeUShort(0u) // TODO: Sequence
            writeUInt(keystore.uin.toUInt()) // TODO: Uin
            writeByte(3) // extVer
            writeByte(135.toByte()) // cmdVer
            writeUInt(0u) // actually unknown const 0
            writeByte(19) // pubId
            writeUShort(0u) // insId
            writeUShort(appInfo.appClientVersion.toUShort())
            writeUInt(0u) // retryTime
            writeFully(buildEncryptHead())
            writeFully(encrypted)
            writeByte(3)
        }, Prefix.UINT_16 or Prefix.INCLUDE_PREFIX, 1) // addition of 1 aims to include packet start
        
        return packet.build().readBytes()
    }
    
    private fun parseWtLogin(raw: ByteArray): ByteArray {
        val reader = ByteReadPacket(raw)
        val header = reader.readByte()
        if (header != 0x02.toByte()) throw Exception("Invalid Header")
        
        val internalLength = reader.readUShort()
        val ver = reader.readUShort()
        val cmd = reader.readUShort()
        val sequence = reader.readUShort()
        val uin = reader.readUInt()
        val flag = reader.readByte()
        val retryTime = reader.readUShort()
        
        val encrypted = reader.readBytes(reader.remaining.toInt() - 1)
        val decrypted = TEA.decrypt(encrypted, keystore.ecdh192.keyExchange(ecdhKey, true))
        if (reader.readByte() != 0x03.toByte()) throw Exception("Packet end not found")
        
        return decrypted
    }
    
    private fun buildEncryptHead(): ByteArray = BytePacketBuilder().apply {
        writeByte(1)
        writeByte(1)
        writeBytes(Random.nextBytes(16))
        writeUShort(0x102u) // unknown const
        writeBytes(keystore.ecdh192.getPublicKey(true), Prefix.UINT_16 or Prefix.LENGTH_ONLY)
    }.build().readBytes()
    
    private fun readTlv(reader: ByteReadPacket): Map<UShort, ByteArray> {
        val tlvCount = reader.readUShort()
        val result = mutableMapOf<UShort, ByteArray>()
        for (i in 0 until tlvCount.toInt()) {
            val tag = reader.readUShort()
            val length = reader.readUShort()
            val value = reader.readBytes(length.toInt())
            
            result[tag] = value
        }
        
        return result
    }
}