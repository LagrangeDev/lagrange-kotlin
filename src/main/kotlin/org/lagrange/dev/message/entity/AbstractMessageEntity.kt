package org.lagrange.dev.message.entity

import kotlinx.serialization.Serializable
import org.lagrange.dev.BotContext
import org.lagrange.dev.message.Message
import org.lagrange.dev.utils.proto.ProtoMap

@Serializable
sealed class AbstractMessageEntity {
    abstract fun encode(): List<ProtoMap>
    abstract fun decode(proto: List<ProtoMap>, current: ProtoMap): AbstractMessageEntity?
    abstract suspend fun preprocess(context: BotContext, message: Message)
    abstract suspend fun postprocess(context: BotContext, message: Message)
}