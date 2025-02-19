package org.lagrange.dev.ext

import org.lagrange.dev.BotContext
import org.lagrange.dev.event.AbstractEventHandler
import org.lagrange.dev.event.MessageEvent

fun BotContext.onMessageEvent(handler: AbstractEventHandler<MessageEvent>) = event.registerHandler(handler)