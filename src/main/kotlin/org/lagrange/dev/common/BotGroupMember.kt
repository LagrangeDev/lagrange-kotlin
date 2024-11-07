package org.lagrange.dev.common

class BotGroupMember(
    val uin: Long,
    val uid: String,
    val permission: GroupMemberPermission,
    val groupLevel: Int,
    val memberCard: String?,
    val memberName: String,
    val specialTitle: String?,
    val joinTime: Long,
    val lastMsgTime: Long,
    val shutUpTimestamp: Long
) {
    val avatar: String
        get() = "https://q1.qlogo.cn/g?b=qq&nk=$uin&s=640"
}