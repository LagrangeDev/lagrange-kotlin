package org.lagrange.dev.message.entity

import org.lagrange.dev.BotContext
import org.lagrange.dev.message.Message
import org.lagrange.dev.utils.proto.ProtoMap
import java.io.InputStream

class VideoEntity(
    
) : NTV2RichMediaEntity() {
    override var msgInfo: ProtoMap? = null
    override var stream: InputStream? = null
    
    override fun encode(): List<ProtoMap> {
        TODO("Not yet implemented")
    }

    override fun decode(proto: List<ProtoMap>, current: ProtoMap): AbstractMessageEntity? {
        TODO("Not yet implemented")
    }

    override suspend fun preprocess(context: BotContext, message: Message) {
        TODO("Not yet implemented")
    }

    override suspend fun postprocess(context: BotContext, message: Message) {
        TODO("Not yet implemented")
    }
}