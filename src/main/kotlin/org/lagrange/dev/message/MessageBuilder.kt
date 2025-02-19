package org.lagrange.dev.message

import org.lagrange.dev.BotContext
import org.lagrange.dev.message.entity.*
import org.lagrange.dev.utils.proto.protobufOf

class MessageBuilder {
    private val entities = mutableListOf<AbstractMessageEntity>()
    
    fun text(text: String) = entities.add(TextEntity(text))

    fun image(path: String) {
        TODO()
    }

    fun record(path: String) {
        TODO()
    }

    fun video(path: String) {
        TODO()
    }

    fun file(path: String) {
        TODO()
    }

    fun mention(uin: Long, display: String = "") = entities.add(MentionEntity(uin, display))
    
    fun reply(message: Message) {
        TODO()
    }
    
    fun append(entity: AbstractMessageEntity) = entities.add(entity)
    
    internal suspend fun build(context: BotContext, message: Message) = protobufOf(
        1 to protobufOf(
            2 to entities.map {
                it.preprocess(context, message)
                it.encode()
            }.flatten()
        )
    )
}