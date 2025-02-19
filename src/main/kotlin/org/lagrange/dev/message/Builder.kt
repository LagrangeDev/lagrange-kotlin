package org.lagrange.dev.message

fun buildMessage(block: MessageBuilder.() -> Unit) = MessageBuilder().apply(block)