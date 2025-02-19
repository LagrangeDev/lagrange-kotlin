package org.lagrange.dev.common

import kotlinx.serialization.Serializable

@Serializable
class BotGroupMember(
    override val uin: Long,
    override val uid: String,
    val permission: GroupMemberPermission,
    val groupLevel: Int,
    val memberCard: String?,
    override val nickname: String,
    val specialTitle: String?,
    val joinTime: Long,
    val lastMsgTime: Long,
    val shutUpTimestamp: Long
): BotContact() {
    val avatar: String
        get() = "https://q1.qlogo.cn/g?b=qq&nk=$uin&s=640"
}