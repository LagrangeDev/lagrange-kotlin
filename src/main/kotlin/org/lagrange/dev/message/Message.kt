package org.lagrange.dev.message

import org.lagrange.dev.common.BotContact
import org.lagrange.dev.common.BotGroup
import org.lagrange.dev.message.entity.AbstractMessageEntity
import kotlin.random.Random

class Message(
    val contact: BotContact,
    val group: BotGroup? = null,
    internal val random: Int = Random.nextInt(),
    internal var sequence: Int = 0,
    internal val clientSequence: Int = Random.nextInt(100000, 999999)
) {
    val entities = mutableListOf<AbstractMessageEntity>()
    var time: Long = System.currentTimeMillis() / 1000
}