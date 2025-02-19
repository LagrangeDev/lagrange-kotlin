package org.lagrange.dev.component

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.lagrange.dev.BotContext
import org.lagrange.dev.event.MessageEvent
import org.lagrange.dev.message.MessagePacker
import org.lagrange.dev.network.SsoResponse
import org.lagrange.dev.utils.proto.ProtoUtils
import org.lagrange.dev.utils.proto.asInt

internal class BotListener(private val context: BotContext) {
    companion object {
        private val handlers = mutableMapOf<String, suspend BotListener.(SsoResponse) -> Unit>(
            "trpc.msg.olpush.OlPushService.MsgPush" to BotListener::handleMsgPush,
            "trpc.qq_new_tech.status_svc.StatusService.KickNT" to BotListener::handleKickNT
        )
    }
    
    fun handle(sso: SsoResponse): Boolean {
        val handler = handlers[sso.command]
        if (handler != null) {
            CoroutineScope(Dispatchers.IO).launch {
                handler.invoke(this@BotListener, sso)
            }
            return true
        }
        
        return false
    }
    
    suspend fun handleMsgPush(sso: SsoResponse) {
        val proto = ProtoUtils.decodeFromByteArray(sso.response)

        val type = proto[1][2][1].asInt
        val event = when (type) {
            166, 82, 141 -> {
                MessageEvent(MessagePacker.parse(context, proto[1]))
            }
            else -> null
        }
        
        if (event != null) {
            context.event.handle(event)
        }
    }
    
    suspend fun handleKickNT(sso: SsoResponse) {
        
    }
}