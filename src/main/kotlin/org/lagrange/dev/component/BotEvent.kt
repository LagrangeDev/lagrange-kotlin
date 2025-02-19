package org.lagrange.dev.component

import org.lagrange.dev.BotContext
import org.lagrange.dev.event.AbstractEventHandler
import org.lagrange.dev.event.BaseEvent

@Suppress("UNCHECKED_CAST")
internal class BotEvent(private val context: BotContext) {
    private val handler: MutableMap<String, AbstractEventHandler<*>> = mutableMapOf()
    
    inline fun <reified T: BaseEvent> registerHandler(handler: AbstractEventHandler<T>) {
        this.handler[T::class.simpleName!!] = handler
    }
    
    suspend fun <T: BaseEvent> handle(event: T) {
        (handler[event::class.simpleName] as AbstractEventHandler<T>?)?.handle(context, event)
    }
}