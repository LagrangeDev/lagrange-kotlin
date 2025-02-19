package org.lagrange.dev.common

import kotlinx.serialization.Serializable

@Serializable
class BotFriend(
    override val uin: Long,
    override val nickname: String,
    val remarks: String,
    val personalSign: String,
    val qid: String,
    override val uid: String,
    ) : BotContact() {
    val avatar: String 
        get() = "https://q1.qlogo.cn/g?b=qq&nk=$uin&s=100"
}