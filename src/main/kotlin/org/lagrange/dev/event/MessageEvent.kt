package org.lagrange.dev.event

import org.lagrange.dev.message.Message

class MessageEvent(val message: Message) : BaseEvent()