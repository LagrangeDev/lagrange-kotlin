package org.lagrange.dev.message.entity

import kotlinx.serialization.Serializable
import org.lagrange.dev.BotContext
import org.lagrange.dev.message.Message
import org.lagrange.dev.utils.proto.ProtoMap
import org.lagrange.dev.utils.proto.asUtf8String
import org.lagrange.dev.utils.proto.protobufOf

@Serializable
class TextEntity(
    val text: String
) : AbstractMessageEntity() {
    override fun encode(): List<ProtoMap> = listOf(
        protobufOf(1 to protobufOf(1 to text))
    )

    override fun decode(proto: List<ProtoMap>, current: ProtoMap): AbstractMessageEntity? {
        return if (current.contains(1) && !current.has(1, 3)) TextEntity(current[1][1].asUtf8String) else null
    }

    override suspend fun preprocess(context: BotContext, message: Message) {
        
    }

    override suspend fun postprocess(context: BotContext, message: Message) {
        
    }
}