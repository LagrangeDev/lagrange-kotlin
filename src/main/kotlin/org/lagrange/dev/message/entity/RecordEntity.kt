package org.lagrange.dev.message.entity

import org.lagrange.dev.BotContext
import org.lagrange.dev.message.Message
import org.lagrange.dev.packet.service.buildNTV2RichMediaDownloadReq
import org.lagrange.dev.packet.service.buildNTV2RichMediaUploadReq
import org.lagrange.dev.packet.service.parseNTV2RichMediaDownloadUrl
import org.lagrange.dev.utils.ext.EMPTY_BYTE_ARRAY
import org.lagrange.dev.utils.ext.fromHex
import org.lagrange.dev.utils.proto.*
import java.io.InputStream

class RecordEntity(
    var url: String,
) : NTV2RichMediaEntity() {
    override var msgInfo: ProtoMap? = null
    override var stream: InputStream? = null
    
    override fun encode(): List<ProtoMap> {
        return emptyList()
    }

    override fun decode(proto: List<ProtoMap>, current: ProtoMap): AbstractMessageEntity? {
        if (current.has(53) && current[53][1].asInt == 48 && (current[53][3].asInt == 22 || current[53][3].asInt == 12)) {
            return RecordEntity("").also {
                it.msgInfo = current[53][2].asMap
            }
        }

        return null
    }

    override suspend fun preprocess(context: BotContext, message: Message) {
        val ext = protobufOf(
            1 to protobufOf(
                2 to "",
            ),
            2 to protobufOf(
                3 to EMPTY_BYTE_ARRAY,
            ),
            3 to if (message.isGroup) protobufOf(
                11 to EMPTY_BYTE_ARRAY,
                12 to "08003800".fromHex(),
                13 to "9a0107aa030408081200".fromHex()
            ) else protobufOf(
                11 to "08003800".fromHex(),
                12 to EMPTY_BYTE_ARRAY,
                13 to "9a010baa03080804120400000000".fromHex()
            )
        )
        val packet = buildNTV2RichMediaUploadReq(message, this, ext)
        val response = context.packet.sendOidb(if (message.isGroup) 0x126e else 0x126d, 100, packet.toByteArray(), true)
    }

    override suspend fun postprocess(context: BotContext, message: Message) {
        val ext = protobufOf(2 to protobufOf(
            1 to 0,
            2 to 0
        ))
        val packet = buildNTV2RichMediaDownloadReq(message, this, ext)
        val response = context.packet.sendOidb(if (message.isGroup) 0x126e else 0x126d, 200, packet.toByteArray(), true)

        url = parseNTV2RichMediaDownloadUrl(ProtoUtils.decodeFromByteArray(response.payload))
    }
}