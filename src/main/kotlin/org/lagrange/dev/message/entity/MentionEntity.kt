package org.lagrange.dev.message.entity

import kotlinx.serialization.Serializable
import org.lagrange.dev.BotContext
import org.lagrange.dev.message.Message
import org.lagrange.dev.utils.ext.readUInt32BE
import org.lagrange.dev.utils.proto.ProtoMap
import org.lagrange.dev.utils.proto.asByteArray
import org.lagrange.dev.utils.proto.asUtf8String
import org.lagrange.dev.utils.proto.protobufOf

@Serializable
class MentionEntity(
    val uin: Long,
    var display: String = ""
) : AbstractMessageEntity() {
    private var uid : String? = null
    
    override fun encode(): List<ProtoMap> = listOf(
        protobufOf(
            1 to protobufOf(
                1 to display,
                12 to protobufOf(
                    3 to if (uin.toInt() == 0) 1 else 2,
                    4 to 0,
                    5 to 0,
                    9 to uid
                )
            )
        )
    )

    override fun decode(proto: List<ProtoMap>, current: ProtoMap): AbstractMessageEntity? {
        return if (current.contains(1) && current.has(1, 3)) MentionEntity(
            current[1][3].asByteArray.readUInt32BE(7),
            current[1][1].asUtf8String
        ) else null
    }

    override suspend fun preprocess(context: BotContext, message: Message) {
        if (display == "") {
            display = "@" + if (message.group == null) {
                context.cache.getFriendList(false).find { it.uin == uin }?.nickname ?: uin.toString()
            } else {
                context.cache.getGroupMemberList(message.group.groupUin, false).find { it.uin == uin }?.nickname ?: uin.toString()
            }
        }
        
        uid = context.cache.resolveUid(uin, message.group?.groupUin)
    }

    override suspend fun postprocess(context: BotContext, message: Message) {
        uid = context.cache.resolveUid(uin, message.group?.groupUin)
    }
}