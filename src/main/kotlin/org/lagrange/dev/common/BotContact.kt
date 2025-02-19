package org.lagrange.dev.common

import kotlinx.serialization.Serializable

@Serializable
sealed class BotContact {
    abstract val uin: Long
    abstract val nickname: String
    internal abstract val uid: String
}