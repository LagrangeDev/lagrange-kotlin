package org.lagrange.dev.event

import org.lagrange.dev.BotContext

interface AbstractEventHandler<T : BaseEvent> {
    suspend fun handle(context: BotContext, event: T)
}