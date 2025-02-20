package org.lagrange.dev.ext

import org.lagrange.dev.BotContext
import org.lagrange.dev.utils.ext.readInt32LE
import org.lagrange.dev.utils.ext.toHex
import org.lagrange.dev.utils.proto.*

suspend fun BotContext.sendFriendPoke() {
    
}

internal suspend fun BotContext.fetchHighwayTicket(): Pair<ByteArray, Map<Int, List<String>>> {
    val response = packet.sendPacket("HttpConn.0x6ff_501", protobufOf(
        0x501 to protobufOf(
            1 to 0,
            2 to 0,
            3 to 16,
            4 to 1,
            5 to keystore.a2.toHex(),
            6 to 3,
            7 to listOf(1, 5, 10, 21),
            9 to 2,
            10 to 9,
            11 to 8,
            15 to "1.0.1"
        )
    ).toByteArray())
    
    val proto = ProtoUtils.decodeFromByteArray(response.response)
    val sigSession = proto[0x501][1].asByteArray
    
    val servers = mutableMapOf<Int, List<String>>()
    proto[0x501][3].asList.value.map { 
        servers[it[1].asInt] = it[2].asList.value.map { addr ->
            val ip = addr[2].asInt.readInt32LE()
            "http://${ip[0]}.${ip[1]}.${ip[2]}.${ip[3]}:${addr[3].asInt}/cgi-bin/httpconn?htcmd=0x6FF0087&uin=${keystore.uin}"
        }
    }
    
    return sigSession to servers
}