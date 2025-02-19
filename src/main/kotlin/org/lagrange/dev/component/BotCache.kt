package org.lagrange.dev.component

import org.lagrange.dev.BotContext
import org.lagrange.dev.common.BotFriend
import org.lagrange.dev.common.BotGroup
import org.lagrange.dev.common.BotGroupMember
import org.lagrange.dev.common.GroupMemberPermission
import org.lagrange.dev.utils.proto.*

internal class BotCache(private val context: BotContext) {
    private val uinToUid = mutableMapOf<Long, String>()
    
    private var friendList: List<BotFriend>? = null
    
    private var groupList: List<BotGroup>? = null
    
    private var groupMemberList: MutableMap<Long, List<BotGroupMember>> = mutableMapOf()

    internal val flattenMemberList: MutableMap<Long, BotGroupMember> = mutableMapOf()
    
    suspend fun resolveUid(uin: Long, groupUin: Long? = null): String? {
        return if (uinToUid.containsKey(uin)) {
            uinToUid[uin]!!
        } else {
            if (groupUin != null) {
                getGroupMemberList(groupUin, true)
            } else {
                getFriendList(true)
            }
            
            uinToUid[uin]
        }
    }

    suspend fun getFriendList(refreshCache: Boolean): List<BotFriend> {
        if (friendList == null || refreshCache) {
            val list = fetchFriendList()
            
            for (f in list) {
                uinToUid[f.uin] = f.uid
            }
            
            friendList = list
        }
        
        return friendList as List<BotFriend>
    }
    
    suspend fun getGroupList(refreshCache: Boolean): List<BotGroup> {
        if (groupList == null || refreshCache) {
            groupList = fetchGroupList()
        }
        
        return groupList as List<BotGroup>
    }
    
    suspend fun getGroupMemberList(groupUin: Long, refreshCache: Boolean): List<BotGroupMember> {
        if (!groupMemberList.containsKey(groupUin) || refreshCache) {
            val list = fetchMemberList(groupUin)
            groupMemberList[groupUin] = list
            
            for (m in list) {
                uinToUid[m.uin] = m.uid
                flattenMemberList[m.uin] = m
            }
        }
        
        return groupMemberList[groupUin] as List<BotGroupMember>
    }

    private suspend fun fetchFriendList(): List<BotFriend> {
        val result = mutableListOf<BotFriend>()
        var nextUin: Long? = null

        do {
            val proto = protobufOf(
                2 to 300,
                10001 to listOf(
                    protobufOf(
                        1 to 1,
                        2 to protobufOf(1 to listOf(103, 102, 20002, 27394))
                    ),
                    protobufOf(
                        1 to 4,
                        2 to protobufOf(1 to listOf(100, 101, 102))
                    )
                )
            )

            if (nextUin != null) {
                proto[5] = protobufOf(1 to nextUin)
            }

            val response = context.packet.sendOidb(0xfd4, 1, proto.toByteArray())
            val respProto = ProtoUtils.decodeFromByteArray(response.payload)

            for (f in respProto[101].asList.value) {
                val properties = (f[10001].asList.value.filter { it[1].asInt == 1 })[0][2][2].asList.value.associateBy({ x -> x[1].asInt }, { x -> x[2].asUtf8String })
                result.add(BotFriend(f[3].asLong, properties[20002]!!, properties[103]!!, properties[102]!!, properties[27394]!!, f[1].asUtf8String))
            }

            if (respProto.contains(2)) {
                nextUin = respProto[2][1].asLong
            }
        } while (nextUin != null)

        return result
    }

    private suspend fun fetchGroupList(): List<BotGroup> {
        val result = mutableListOf<BotGroup>()
        val proto = protobufOf(
            1 to protobufOf(
                1 to ProtoUtils.createProtoFill(1, 2, 3, 4, 5, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 5001, 5002, 5003),
                2 to ProtoUtils.createProtoFill(1, 2, 3, 4, 5, 6, 7, 8),
                3 to ProtoUtils.createProtoFill(5, 6)
            )
        )
        val response = context.packet.sendOidb(0xfe5, 2, proto.toByteArray(), true)
        val respProto = ProtoUtils.decodeFromByteArray(response.payload)

        for (r in respProto[2].asList.value) {
            val info = r[4]

            val desc = if (info.contains(18)) info[18].asUtf8String else null
            val question = if (info.contains(19)) info[19].asUtf8String else null
            val announcement = if (info.contains(30)) info[30].asUtf8String else null
            val group = BotGroup(r[3].asLong, info[5].asUtf8String, info[4].asInt, info[3].asInt, info[2].asLong, desc, question, announcement)
            result.add(group)
        }

        return result
    }

    private suspend fun fetchMemberList(groupUin: Long): List<BotGroupMember>  {
        val result = mutableListOf<BotGroupMember>()
        var token: String? = null

        do {
            val proto = protobufOf(
                1 to groupUin,
                2 to 5,
                3 to 2,
                4 to ProtoUtils.createProtoFill(10, 11, 12, 13, 16, 17, 18, 19, 20, 21, 100, 101, 102, 103, 104, 105, 106, 107, 200, 201)
            )

            if (token != null) {
                proto[15] = token
            }

            val response = context.packet.sendOidb(0xfe7, 3, proto.toByteArray())
            val respProto = ProtoUtils.decodeFromByteArray(response.payload)

            for (m in respProto[2].asList.value) {
                val perm = if (m.contains(107)) when (m[107].asInt) {
                    0 -> GroupMemberPermission.MEMBER
                    1 -> GroupMemberPermission.ADMINISTRATOR
                    2 -> GroupMemberPermission.OWNER
                    else -> GroupMemberPermission.UNKNOWN
                } else GroupMemberPermission.MEMBER
                val title = if (m.contains(17)) m[17].asUtf8String else ""
                val level = if (m.contains(12)) m[12][2].asInt else 0
                val shutupTime = if (m.contains(102)) m[102].asLong else 0
                val card = if (m[11].contains(2)) m[11][2].asUtf8String else ""
                val name = if (m.contains(10)) m[10].asUtf8String else ""
                val member = BotGroupMember(m[1][4].asLong, m[1][2].asUtf8String, perm, level, card, name, title, m[100].asLong, m[101].asLong, shutupTime)
                result.add(member)
            }

            token = if (respProto.contains(15)) respProto[15].asUtf8String else null
        } while (token != null)

        return result
    }
}