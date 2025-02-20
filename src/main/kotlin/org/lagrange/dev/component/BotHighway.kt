package org.lagrange.dev.component

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.lagrange.dev.BotContext
import org.lagrange.dev.ext.fetchHighwayTicket
import org.lagrange.dev.utils.ext.calculateMD5
import org.lagrange.dev.utils.ext.toHex
import org.lagrange.dev.utils.proto.ProtoMap
import org.lagrange.dev.utils.proto.ProtoUtils
import org.lagrange.dev.utils.proto.asInt
import org.lagrange.dev.utils.proto.protobufOf
import org.slf4j.LoggerFactory
import java.io.InputStream
import kotlin.math.min

private const val chunkSize = 1024 * 1024

private const val concurrent = 5

internal class BotHighway(private val context: BotContext) {
    private val logger = LoggerFactory.getLogger(BotHighway::class.java)
    
    private val client = HttpClient(CIO)
    
    private var sequence: Int = 0
    
    private var ticket: ByteArray? = null
    private var urls: Map<Int, List<String>>? = null
    
    init {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                while (!context.online) {
                    delay(1000)
                }

                fetchTicket()
                delay(12 * 60 * 60 * 1000)
            }
        }
    }
    
    suspend fun upload(commandId: Int, input: InputStream, extendInfo: ProtoMap) {
        if (ticket == null || urls == null) {
            fetchTicket()
        }
        
        var offset = 0
        val fileSize = withContext(Dispatchers.IO) {
            input.available()
        }
        val semaphore = Semaphore(concurrent)
        val jobs = mutableListOf<Job>()

        while (offset < fileSize) {
            val buffer = ByteArray(min(chunkSize, fileSize - offset))
            val payload = withContext(Dispatchers.IO) {
                input.read(buffer)
            }
            val fileMd5 = input.calculateMD5()
            
            val proto = protobufOf(
                1 to protobufOf(
                    1 to 1,
                    2 to context.keystore.uin.toString(),
                    3 to "PicUp.DataUp",
                    4 to sequence++,
                    5 to 0,
                    6 to context.appInfo.subAppId,
                    7 to 16,
                    8 to commandId
                ),
                2 to protobufOf(
                    1 to 0,
                    2 to fileSize,
                    3 to offset,
                    4 to payload,
                    5 to 0,
                    6 to ticket,
                    8 to buffer.calculateMD5(), 
                    9 to fileMd5,
                    10 to 0,
                    13 to 0
                ),
                3 to extendInfo,
                4 to 0,
                5 to protobufOf(
                    1 to 8,
                    2 to context.keystore.a2,
                    3 to context.appInfo.appId
                )
            )
            logger.trace("Upload chunk: offset={}, payload={}, head={}", offset, payload, proto.toByteArray().toHex())
            
            offset += payload
            
            val url = urls!![1]!![0]
            
            jobs.add(CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    semaphore.acquire()
                    val (respHead, body) = sendPacket(proto, buffer, url, offset == fileSize)
                    logger.debug("Upload response: {}", respHead[3].asInt)
                }.onFailure {
                    logger.error("Failed to upload chunk", it)
                }.also {
                    semaphore.release()
                }
            })
        }
        
        jobs.joinAll()
    }
    
    private suspend fun sendPacket(head: ProtoMap, body: ByteArray, url: String, isEnd: Boolean): Pair<ProtoMap, ByteArray> {
        val headBytes = head.toByteArray()
        val buffer = BytePacketBuilder().apply { 
            writeByte(0x28)
            writeInt(headBytes.size)
            writeInt(body.size)
            writeFully(headBytes)
            writeFully(body)
            writeByte(0x29)
        }.build().readBytes()
        
        val response = client.post(url) {
            setBody(buffer)
            headers {
                append("Connection", if (isEnd) "close" else "keep-alive")
                append("Accept-Encoding", "identity")
                append("User-Agent", "Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.2)")
            }
        }.body<ByteArray>()
        
        val reader = ByteReadPacket(response)
        
        if (reader.readByte() != 0x28.toByte()) {
            logger.error("Invalid packet head")
            error("Invalid packet head")
        }
        
        val headSize = reader.readInt()
        val bodySize = reader.readInt()
        val respHead = reader.readBytes(headSize)
        val respBody = reader.readBytes(bodySize)
        
        if (reader.readByte() != 0x29.toByte()) {
            logger.error("Invalid packet tail")
            error("Invalid packet tail")
        }
        
        return ProtoUtils.decodeFromByteArray(respHead) to respBody
    }
    
    private suspend fun fetchTicket() {
        val (sig, servers) = context.fetchHighwayTicket()
        ticket = sig
        urls = servers

        logger.info("Highway ticket fetched, delay for 12h")
    }
}