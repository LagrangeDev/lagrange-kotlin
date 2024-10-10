package org.lagrange.dev.network

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.lagrange.dev.common.AppInfo
import org.lagrange.dev.common.Keystore
import org.lagrange.dev.utils.crypto.TEA
import org.lagrange.dev.utils.ext.*
import org.lagrange.dev.utils.generator.StringGenerator
import org.lagrange.dev.utils.proto.protobufOf
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import kotlin.random.Random


internal class PacketHandler(
    private val keystore: Keystore,
    private val appInfo: AppInfo
) {
    private var sequence = Random.nextInt(0x10000, 0x20000)
    private val host = "msfwifi.3g.qq.com"
    private val port = 8080

    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private val socket = aSocket(selectorManager).tcp()
    private lateinit var input: ByteReadChannel
    private lateinit var output: ByteWriteChannel
    private val pending: ConcurrentHashMap<Int, CompletableDeferred<SsoResponse>> = ConcurrentHashMap()
    private val headLength = 4
    var connected = false

    private val logger = LoggerFactory.getLogger(PacketHandler::class.java)
    private val client = HttpClient()

    suspend fun connect() {
        val s = socket.connect(host, port)
        input = s.openReadChannel()
        output = s.openWriteChannel(autoFlush = true)
        logger.info("Connected to $host:$port")
        connected = true
        
        CoroutineScope(Dispatchers.IO).launch {
            handleReceive()
        }
    }
    
    suspend fun sendPacket(command: String, payload: ByteArray): SsoResponse {
        val seq = sequence++
        val sso = buildSso(command, payload, seq)
        val service = buildService(sso)

        val response = CompletableDeferred<SsoResponse>()
        pending[seq] = response
        output.writeFully(service)
        
        logger.debug("Sent packet '$command' with sequence $seq")
        
        return response.await()
    }

    private fun buildService(sso: ByteArray): ByteArray {
        val packet = BytePacketBuilder()
        
        packet.barrier({
            writeInt(12)
            writeByte(if (keystore.d2.isEmpty()) 2 else 1)
            writeBytes(keystore.d2, Prefix.UINT_32 or Prefix.INCLUDE_PREFIX)
            writeByte(0) // unknown
            writeString(keystore.uin.toString(), Prefix.UINT_32 or Prefix.INCLUDE_PREFIX)
            writeBytes(TEA.encrypt(sso, keystore.d2Key))
        }, Prefix.UINT_32 or Prefix.INCLUDE_PREFIX)
        
        return packet.build().readBytes()
    }
    
    private fun buildSso(command: String, payload: ByteArray, sequence: Int): ByteArray {
        val packet = BytePacketBuilder()
        
        packet.barrier({
            writeInt(sequence)
            writeInt(appInfo.subAppId)
            writeInt(2052)  // locale id
            writeFully("020000000000000000000000".fromHex())
            writeBytes(keystore.a2, Prefix.UINT_32 or Prefix.INCLUDE_PREFIX)
            writeString(command, Prefix.UINT_32 or Prefix.INCLUDE_PREFIX)
            writeBytes(ByteArray(0), Prefix.UINT_32 or Prefix.INCLUDE_PREFIX) // unknown
            writeString(keystore.guid.toHex(), Prefix.UINT_32 or Prefix.INCLUDE_PREFIX)
            writeBytes(ByteArray(0), Prefix.UINT_32 or Prefix.INCLUDE_PREFIX) // unknown
            writeString(appInfo.currentVersion, Prefix.UINT_16 or Prefix.INCLUDE_PREFIX)
            writeBytes(buildSsoReserved(command, payload, true), Prefix.UINT_32 or Prefix.INCLUDE_PREFIX)
        }, Prefix.UINT_32 or Prefix.INCLUDE_PREFIX)
        
        packet.writeBytes(payload, Prefix.UINT_32 or Prefix.INCLUDE_PREFIX)
        
        return packet.build().readBytes()
    }
    
    private fun buildSsoReserved(command: String, payload: ByteArray, isSign: Boolean): ByteArray {
        val proto = protobufOf(
            15 to StringGenerator.generateTrace(),
        )
        
        if (keystore.uid != "") {
            proto[16] = keystore.uid
        }
        
        if (isSign) {
            val url = "https://sign.lagrangecore.org/api/sign/25765"
            val response = runBlocking { 
                client.post<String>(url) {
                    body = Json.encodeToString(SignRequest(cmd = command, seq = sequence, src = payload.toHex()))
                    headers {
                        append("Content-Type", "application/json")
                    }
                }
            }
            val value = Json.decodeFromString(SignResponse.serializer(), response).value
            proto[24] = protobufOf(
                1 to value.sign.fromHex(),
                2 to value.token.fromHex(),
                3 to value.extra.fromHex()
            )
        }
        
        return proto.toByteArray()
    }
    
    private suspend fun handleReceive() {
        while (true) {
            try {
                val buffer = ByteArray(headLength)
                input.readFully(buffer)
                val length = buffer.readUInt32BE(0) // prefix included
                val payload = ByteArray(length.toInt() - headLength)
                input.readFully(payload)
                
                val service = parseService(payload)
                val sso = parseSso(service)
                logger.debug("Received packet '${sso.command}' with sequence ${sso.sequence}")
                
                pending.remove(sso.sequence).also { 
                    it?.complete(sso) ?: logger.warn("No pending request for sequence ${sso.sequence}")
                }
            } catch (e: Exception) {
                logger.error("Error while reading packet", e)
            }
        }
    }
    
    private fun parseSso(service: ByteArray): SsoResponse {
        val reader = ByteReadPacket(service)
        
        val headLen = reader.readUInt()
        val sequence = reader.readUInt()
        val retCode = reader.readInt()
        val extra = reader.readString(Prefix.UINT_32 or Prefix.INCLUDE_PREFIX)
        val command = reader.readString(Prefix.UINT_32 or Prefix.INCLUDE_PREFIX)
        val msgCookieLength = reader.readInt() - 4
        val msgCookie = reader.readBytes(msgCookieLength)
        val isCompressed = reader.readInt() == 1
        val reserveFieldLength = reader.readInt()
        val reserveField = reader.readBytes(reserveFieldLength)
        var payload = reader.readBytes(Prefix.UINT_32 or Prefix.INCLUDE_PREFIX)
        
        if (isCompressed) {
            val gzip = GZIPInputStream(payload.inputStream())
            val output = BytePacketBuilder()
            output.writeFully(gzip.readBytes())
            payload = output.build().readBytes()
        }
        
        return if (retCode == 0) {
            SsoResponse(retCode, command, payload, sequence.toInt())
        } else {
            SsoResponse(retCode, command, payload, sequence.toInt(), extra)
        }
    }
    
    private fun parseService(raw: ByteArray): ByteArray {
        val reader = ByteReadPacket(raw)
        
        val protocol = reader.readUInt()
        val authFlag = reader.readByte()
        val flag = reader.readByte()
        val uin = reader.readString(Prefix.UINT_32 or Prefix.INCLUDE_PREFIX)
        
        if (protocol != 12u && protocol != 13u) throw Exception("Unrecognized protocol: $protocol")
        
        val encrypted = reader.readBytes(reader.remaining.toInt())
        return when (authFlag) {
            0.toByte() -> encrypted
            1.toByte() -> TEA.decrypt(encrypted, keystore.d2Key)
            2.toByte() -> TEA.decrypt(encrypted, ByteArray(16))
            else -> throw Exception("Unrecognized auth flag: $authFlag")
        }
    }
    
    @Serializable
    private data class SignRequest(
        @SerialName("cmd") val cmd: String,
        @SerialName("seq") val seq: Int,
        @SerialName("src") val src: String
    )
    
    @Serializable
    private data class SignResponse(
        val platform: String,
        val version: String,
        val value: SignValue
    )

    @Serializable
    private data class SignValue(
        val sign: String,
        val token: String,
        val extra: String
    )
}