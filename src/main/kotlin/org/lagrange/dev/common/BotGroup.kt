package org.lagrange.dev.common

class BotGroup(
    val groupUin: Long,
    val groupName: String,
    val memberCount: Int,
    val maxMember: Int,
    val createTime: Long,
    val description: String?,
    val question: String?,
    val announcement: String?
)