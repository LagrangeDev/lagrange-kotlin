package org.lagrange.dev.message

import org.lagrange.dev.BotContext
import org.lagrange.dev.message.entity.*
import org.lagrange.dev.utils.proto.protobufOf
import java.io.File

class MessageBuilder {
    private val entities = mutableListOf<AbstractMessageEntity>()
    
    fun text(text: String) = entities.add(TextEntity(text))

    fun image(path: String) = entities.add(ImageEntity(File(path).inputStream()))
    
    fun image(buffer: ByteArray) = entities.add(ImageEntity(buffer.inputStream()))

    fun record(path: String) = entities.add(RecordEntity(File(path).inputStream()))
    
    fun record(buffer: ByteArray) = entities.add(RecordEntity(buffer.inputStream()))

    fun video(path: String) {
        val inputStream = File(path).inputStream()
        TODO()
    }
    
    fun video(buffer: ByteArray) {
        val inputStream = buffer.inputStream()
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