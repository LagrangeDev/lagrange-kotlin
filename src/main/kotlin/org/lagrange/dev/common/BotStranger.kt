package org.lagrange.dev.common

import kotlinx.serialization.Serializable

@Serializable
class BotStranger(
    override val uin: Long,
    override val nickname: String,
    override val uid: String
): BotContact() {
    val avatar: String
        get() = "https://q1.qlogo.cn/g?b=qq&nk=$uin&s=640"
}