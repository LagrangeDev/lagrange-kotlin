package org.lagrange.dev.message

import org.lagrange.dev.BotContext
import org.lagrange.dev.common.BotContact
import org.lagrange.dev.common.BotFriend
import org.lagrange.dev.common.BotGroup
import org.lagrange.dev.common.BotStranger
import org.lagrange.dev.message.entity.*
import org.lagrange.dev.utils.proto.*

object MessagePacker {
    private val factory: ArrayList<AbstractMessageEntity> = arrayListOf(
        TextEntity(""),
        MentionEntity(0, ""),
        ImageEntity("")
    )

    suspend fun build(context: BotContext, message: Message, builder: MessageBuilder) = protobufOf(
        1 to (if (message.group == null) buildRoutingHead(message.contact as BotFriend) else buildRoutingHead(message.group)),
        2 to protobufOf(
            1 to 1,
            2 to 0,
            3 to 0
        ),
        3 to builder.build(context, message),
        4 to message.clientSequence,
        5 to message.random
    )
    
    suspend fun parse(context: BotContext, proto: ProtoValue): Message {
        val message = parseMessageBase(context, proto.asMap)
        val maps = proto[3][1][2].asList.value.map { it.asMap }

        maps.forEachIndexed { _, entity ->
            factory.forEach {
                val result = it.decode(maps, entity)
                if (result != null) {
                    message.entities.add(result)
                    return@forEachIndexed
                }
            }
        }

        message.entities.forEach { it.postprocess(context, message) }
        return message
    }
    
    private suspend fun parseMessageBase(context: BotContext, proto: ProtoMap): Message {
        val fromUin = proto[1][1].asLong
        val fromUid = proto[1][2].asUtf8String
        
        var group: BotGroup? = null
        val contact: BotContact = if (proto.has(1, 8)) {
            val groupHead = proto[1][8].asMap
            val groupUin = groupHead[1].asLong
            
            group = context.cache.getGroupList(false).find { it.groupUin == groupUin }
            context.cache.getGroupMemberList(groupUin, false).find { it.uin == fromUin }
        } else {
            context.cache.getFriendList(false).find { it.uin == fromUin }
        } ?: BotStranger(fromUin, "", fromUid)
        val (sequence, clientSequence) = (if (proto.has(2, 11)) proto[2][11].asInt to proto[2][5].asInt else proto[2][5].asInt to 0)
        val message = Message(contact, group, (proto[2][12].asLong and 0xfffffff).toInt(), sequence, clientSequence)
        
        return message
    }
    
    private fun buildRoutingHead(friend: BotFriend) = protobufOf(
        1 to protobufOf(
            1 to friend.uin,
            2 to friend.uid
        )
    )

    private fun buildRoutingHead(group: BotGroup) = protobufOf(
        2 to protobufOf(
            1 to group.groupUin,
        )
    )
}