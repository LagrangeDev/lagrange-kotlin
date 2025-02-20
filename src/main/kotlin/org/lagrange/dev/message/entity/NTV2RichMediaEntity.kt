package org.lagrange.dev.message.entity

import org.lagrange.dev.utils.proto.ProtoMap
import java.io.InputStream

sealed class NTV2RichMediaEntity : AbstractMessageEntity() { 
    abstract var msgInfo: ProtoMap?
    abstract var stream: InputStream?
}