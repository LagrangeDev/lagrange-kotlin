package org.lagrange.dev.message.entity

import org.lagrange.dev.BotContext
import org.lagrange.dev.message.Message
import org.lagrange.dev.utils.proto.ProtoMap
import org.lagrange.dev.utils.proto.asInt
import org.lagrange.dev.utils.proto.asMap
import org.lagrange.dev.utils.proto.asUtf8String

class ImageEntity(
    var url: String,
) : AbstractMessageEntity() {
    var summary: String = "[图片]"
    var subType: Int = 0
    private var msgInfo: ProtoMap? = null
    
    override fun encode(): List<ProtoMap> {
        return emptyList()
    }

    override fun decode(proto: List<ProtoMap>, current: ProtoMap): AbstractMessageEntity? {
        if (current.has(53) && current[53][1].asInt == 48 && (current[53][3].asInt == 10 || current[53][3].asInt == 20)) {
            return ImageEntity("").also {
                it.msgInfo = current[53][2].asMap
                it.subType = it.msgInfo!![2][1][1].asInt
                it.summary = it.msgInfo!![2][1][2].asUtf8String
            }
        }
        
        return null
    }

    override suspend fun preprocess(context: BotContext, message: Message) {
        
    }

    override suspend fun postprocess(context: BotContext, message: Message) {
        
    }
}