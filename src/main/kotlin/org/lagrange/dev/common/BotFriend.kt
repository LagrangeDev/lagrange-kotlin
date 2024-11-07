package org.lagrange.dev.common

class BotFriend(
    val uin: Long,
    val nickname: String,
    val remarks: String,
    val personalSign: String,
    val qid: String,
    internal val uid: String,
    ) {
    val avatar: String 
        get() = "https://q1.qlogo.cn/g?b=qq&nk=$uin&s=100"
}