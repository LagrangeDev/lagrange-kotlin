package org.lagrange.dev.packet.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lagrange.dev.common.BotContact
import org.lagrange.dev.common.BotGroup
import org.lagrange.dev.message.Message
import org.lagrange.dev.message.entity.*
import org.lagrange.dev.utils.ext.calculateMD5
import org.lagrange.dev.utils.ext.calculateSHA1
import org.lagrange.dev.utils.ext.readInt32LE
import org.lagrange.dev.utils.ext.toHex
import org.lagrange.dev.utils.helper.ImageHelper
import org.lagrange.dev.utils.proto.*
import kotlin.random.Random

const val blockSize = 1024 * 1024

fun buildNTV2RichMediaDownloadReq(message: Message, entity: NTV2RichMediaEntity, ext: ProtoMap? = null) = protobufOf(
    1 to buildHead(message, entity, 200),
    3 to protobufOf(
        1 to (entity.msgInfo ?: error("msgInfo is null"))[1][1].asByteArray,
        2 to ext
    )
)

suspend fun buildNTV2RichMediaUploadReq(message: Message, entity: NTV2RichMediaEntity, ext: ProtoMap? = null) = protobufOf(
    1 to buildHead(message, entity, 100),
    2 to protobufOf(
        1 to listOf(
            protobufOf(
                1 to buildFileInfo(entity),
                2 to 0
            )
        ),
        2 to true,
        3 to false,
        4 to Random.nextInt(),
        5 to when (entity) {
            is ImageEntity -> 1
            is RecordEntity, is VideoEntity -> 2
        },
        6 to ext,
        7 to 0,
        8 to false
    )
)

fun parseNTV2RichMediaDownloadUrl(proto: ProtoMap) = "https://${proto[3][3][1].asUtf8String}${proto[3][3][2].asUtf8String}${if (proto.has(3, 1)) proto[3][1].asUtf8String else ""}"

fun parseNTV2RichMediaUploadReq(proto: ProtoMap): Triple<ProtoMap, ProtoMap, ProtoMap?> {
    val upload = proto[2]
    
    val msgInfo = upload[6]
    val remote = upload[3].asList.value
    val compat = upload[8]
    val subFiles = if (upload.has(10)) upload[10].asList.value else emptyList()
    
    val network = remote.map {
        protobufOf(
            1 to protobufOf(
                1 to true,
                2 to run {
                    val ip = it[1].asInt.readInt32LE()
                    "${ip[0]}.${ip[1]}.${ip[2]}.${ip[3]}"
                }
            ),
            2 to it[2].asInt
        )
    }
    
    if (!upload.has(10)) {
        return Triple(msgInfo.asMap, compat.asMap, null)
    }
    
    val ext = protobufOf(
        1 to msgInfo[1][1][2].asUtf8String,
        2 to upload[1].asUtf8String,
        5 to protobufOf(
            1 to network
        ),
        6 to msgInfo[1],
        10 to blockSize,
    )

    return Triple(msgInfo.asMap, compat.asMap, ext) 
}

private fun buildHead(message: Message, entity: NTV2RichMediaEntity, command: Int): ProtoMap {
    val (request, business) = when (entity) {
        is ImageEntity -> 2 to 1
        is RecordEntity -> 1 to 3
        is VideoEntity -> 2 to 2
    }
    
    return protobufOf(
        1 to protobufOf(
            1 to 1,
            2 to command
        ),
        2 to buildSceneInfo(message.contact, message.group, request, business),
        3 to protobufOf(
            1 to 2
        )
    )
}

private fun buildSceneInfo(contact: BotContact, group: BotGroup?, requestType: Int, businessType: Int) = protobufMapOf {
    it[101] = requestType
    it[102] = businessType

    if (group == null) {
        it[200] = 1
        it[201] = protobufOf(
            1 to 2,
            2 to contact.uid
        )
    } else {
        it[200] = 2
        it[202] = protobufOf(
            1 to group.groupUin
        )
    }
}

private fun buildFileType(entity: NTV2RichMediaEntity) = when (entity) {
    is ImageEntity -> protobufOf(
        1 to 1,
        3 to 0,
        4 to 0,
    )
    is RecordEntity -> protobufOf(
        1 to 3,
        2 to 0,
        3 to 0,
        4 to 1,
    )
    is VideoEntity -> protobufOf(
        1 to 2,
        2 to 0,
        3 to 0,
        4 to 0,
    )
}

private suspend fun buildFileInfo(entity: NTV2RichMediaEntity) = protobufOf(
    1 to withContext(Dispatchers.IO) {
        entity.stream!!.available()
    },
    2 to entity.stream!!.calculateMD5().toHex(),
    3 to entity.stream!!.calculateSHA1().toHex(),
    4 to entity.stream!!.calculateMD5().toHex() + ".png",
    5 to buildFileType(entity),
).also {
    when (entity) {
        is ImageEntity -> {
            val (type, size) = ImageHelper.resolve(entity.stream!!)
            it[5][2] = type.value
            it[6] = size.x
            it[7] = size.y
            it[8] = 0
            it[9] = 1
        }
        else -> {
            it[6] = 0
            it[7] = 0
            it[8] = 114514
            it[9] = 0
        }
    }
}