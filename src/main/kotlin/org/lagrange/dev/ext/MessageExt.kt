package org.lagrange.dev.ext

import org.lagrange.dev.BotContext
import org.lagrange.dev.message.Message
import org.lagrange.dev.message.MessageBuilder
import org.lagrange.dev.message.MessagePacker
import org.lagrange.dev.utils.proto.ProtoUtils
import org.lagrange.dev.utils.proto.asInt
import org.lagrange.dev.utils.proto.asLong

suspend fun BotContext.sendPrivateMessage(uin: Long, builder: MessageBuilder) : Message? {
    val friend = getFriendList().find { it.uin == uin } ?: throw Exception("Friend not found")
    val message = Message(friend)
    
    val packed = MessagePacker.build(this, message, builder)
    val response = packet.sendPacket("MessageSvc.PbSendMsg", packed.toByteArray())
    val proto = ProtoUtils.decodeFromByteArray(response.response)
    if (proto[1].asInt != 0) {
        logger.error("Failed to send message: ${proto[1].asInt}")
        return null
    }

    message.time = proto[3].asLong
    message.sequence = proto[14].asInt

    return message
}

suspend fun BotContext.sendGroupMessage(uin: Long, builder: MessageBuilder) : Message? {
    val group = getGroupList().find { it.groupUin == uin } ?: throw Exception("Group not found")
    val self = getFriendList().find { it.uin == this.keystore.uin } ?: throw Exception("Self not found")
    val message = Message(self, group)
    
    val packed = MessagePacker.build(this, message, builder)
    val response = packet.sendPacket("MessageSvc.PbSendMsg", packed.toByteArray())
    val proto = ProtoUtils.decodeFromByteArray(response.response)
    if (proto[1].asInt != 0) {
        logger.error("Failed to send message: ${proto[1].asInt}")
        return null
    }
    
    message.time = proto[3].asLong
    message.sequence = proto[11].asInt
    
    return message
}