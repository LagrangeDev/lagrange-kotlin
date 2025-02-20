package org.lagrange.dev.message.entity

import org.lagrange.dev.BotContext
import org.lagrange.dev.message.Message
import org.lagrange.dev.packet.service.buildNTV2RichMediaDownloadReq
import org.lagrange.dev.packet.service.buildNTV2RichMediaUploadReq
import org.lagrange.dev.packet.service.parseNTV2RichMediaDownloadUrl
import org.lagrange.dev.packet.service.parseNTV2RichMediaUploadReq
import org.lagrange.dev.utils.ext.EMPTY_BYTE_ARRAY
import org.lagrange.dev.utils.ext.calculateSHA1
import org.lagrange.dev.utils.ext.fromHex
import org.lagrange.dev.utils.proto.*
import java.io.InputStream

class ImageEntity : NTV2RichMediaEntity {
    var url = ""

    constructor() : super() // for factory

    constructor(stream: InputStream) : super() {
        this.url = ""
        this.stream = stream
    }

    var summary: String = "[图片]"
    var subType: Int = 0
    
    override var msgInfo: ProtoMap? = null
    override var stream: InputStream? = null    
    private var compat: ProtoMap? = null
    
    override fun encode() = listOf(
        protobufOf(
            53 to protobufOf(
                1 to 48,
                2 to msgInfo,
                3 to 10
            )
        ),
        protobufOf(
            4 to compat
        )
    )

    override fun decode(proto: List<ProtoMap>, current: ProtoMap): AbstractMessageEntity? {
        if (current.has(53) && current[53][1].asInt == 48 && (current[53][3].asInt == 10 || current[53][3].asInt == 20)) {
            return ImageEntity().also {
                val info = current[53][2].asMap
                
                it.msgInfo = info
                it.subType = if (info.has(2, 1, 1)) info[2][1][1].asInt else 0
                it.summary = info[2][1][2].asUtf8String
            }
        }
        
        return null
    }

    override suspend fun preprocess(context: BotContext, message: Message) {
        val ext = protobufOf(
            1 to protobufOf(
                1 to subType,
                2 to summary,
                (if (message.isGroup) 12 else 11) to "0800180020004200500062009201009a0100a2010c080012001800200028003a00".fromHex()
            ),
            2 to protobufOf(
                3 to EMPTY_BYTE_ARRAY,
            ),
            3 to protobufOf(
                11 to EMPTY_BYTE_ARRAY,
                12 to EMPTY_BYTE_ARRAY,
                13 to EMPTY_BYTE_ARRAY
            )
        )
        val packet = buildNTV2RichMediaUploadReq(message, this, ext)
        val response = context.packet.sendOidb(if (message.isGroup) 0x11c4 else 0x11c5, 100, packet.toByteArray(), true)
        
        val (msgInfo, compat, hwExt) = parseNTV2RichMediaUploadReq(ProtoUtils.decodeFromByteArray(response.payload))
        this.msgInfo = msgInfo
        this.compat = compat
        
        if (hwExt == null) {
            return
        }
        
        hwExt[11] = listOf(protobufOf(1 to stream!!.calculateSHA1()))
        context.highway.upload(if (message.isGroup) 1004 else 1003, stream!!, hwExt)
    }

    override suspend fun postprocess(context: BotContext, message: Message) {
        val ext = protobufOf(2 to protobufOf(
            1 to 0,
            2 to 0
        ))
        val packet = buildNTV2RichMediaDownloadReq(message, this, ext)
        val response = context.packet.sendOidb(if (message.isGroup) 0x11c4 else 0x11c5, 200, packet.toByteArray(), true)
        
        url = parseNTV2RichMediaDownloadUrl(ProtoUtils.decodeFromByteArray(response.payload))
    }
}