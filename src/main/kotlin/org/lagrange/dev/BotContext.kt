package org.lagrange.dev

import kotlinx.coroutines.*
import org.lagrange.dev.common.AppInfo
import org.lagrange.dev.common.Keystore
import org.lagrange.dev.common.SignProvider
import org.lagrange.dev.component.BotCache
import org.lagrange.dev.component.BotEvent
import org.lagrange.dev.component.BotHighway
import org.lagrange.dev.component.BotListener
import org.lagrange.dev.network.PacketHandler
import org.lagrange.dev.packet.login.QrCodeState
import org.lagrange.dev.packet.login.ntlogin
import org.lagrange.dev.packet.login.wtlogin
import org.lagrange.dev.utils.ext.toHex
import org.lagrange.dev.utils.proto.ProtoUtils
import org.lagrange.dev.utils.proto.asUtf8String
import org.lagrange.dev.utils.proto.protobufOf
import org.slf4j.LoggerFactory

class BotContext(
    val keystore: Keystore,
    internal val appInfo: AppInfo,
    signProvider: SignProvider,
) {
    private var qrCodeState: QrCodeState = QrCodeState.Unknown
    
    internal val cache = BotCache(this)
    internal val listener = BotListener(this) 
    internal val event = BotEvent(this)
    
    internal val logger = LoggerFactory.getLogger(BotContext::class.java)

    internal val packet = PacketHandler(keystore, appInfo, signProvider, listener)

    internal val highway = BotHighway(this)

    val connected 
        get() = packet.connected
    var online: Boolean = false 
        private set
    
    suspend fun getFriendList(refreshCache: Boolean = false) = cache.getFriendList(refreshCache)
    
    suspend fun getGroupList(refreshCache: Boolean = false) = cache.getGroupList(refreshCache)
    
    suspend fun getGroupMemberList(groupUin: Long, refreshCache: Boolean = false) = cache.getGroupMemberList(groupUin, refreshCache)

    suspend fun fetchQrCode(): Pair<String, ByteArray> {
        if (!packet.connected) {
            packet.connect()
        }

        val transEmp = wtlogin(keystore, appInfo).buildTransEmp0x31()
        val response = packet.sendPacket("wtlogin.trans_emp", transEmp)
        val parsed = wtlogin(keystore, appInfo).parseTransEmp0x31(response.response)

        val proto = ProtoUtils.decodeFromByteArray(parsed.getValue(0xd1u))
        return Pair(proto[2].asUtf8String, parsed.getValue(0x17u))
    }

    suspend fun loginByQrCode(): Boolean {
        while (true) {
            qrCodeState = queryState()
            logger.info("QrCode state: ${qrCodeState.value}")

            if (qrCodeState.value == QrCodeState.Confirmed.value) {
                logger.info("QrCode confirmed, trying to login with NoPicSig")
                break
            }
            withContext(Dispatchers.IO) {
                Thread.sleep(2000)
            }
        }
        
        val login = wtlogin(keystore, appInfo).buildLogin()
        val response = packet.sendPacket("wtlogin.login", login)
        val success = wtlogin(keystore, appInfo).parseLogin(response.response)

        return if (success) online() else false
    }
    
    suspend fun loginByToken(): Boolean {
        if (!packet.connected) {
            packet.connect()
        }
        
        if (keystore.d2.isNotEmpty() && keystore.d2Key.isNotEmpty()) {
            try { 
                return online() 
            } catch (e: Exception) {
                logger.error("Failed to directly online", e)
            }
        }

        keystore.clear()
        val keyExchange = ntlogin(keystore, appInfo).buildKeyExchange()
        val response = packet.sendPacket("trpc.login.ecdh.EcdhService.SsoKeyExchange", keyExchange)
        ntlogin(keystore, appInfo).parseKeyExchange(response.response)
        logger.info("Key exchange completed")
        
        val easyLogin = ntlogin(keystore, appInfo).buildNTLoginPacket(keystore.encryptedA1)
        val loginResponse = packet.sendPacket("trpc.login.ecdh.EcdhService.SsoNTLoginEasyLogin", easyLogin)
        
        return false
    }
    
    suspend fun getState(): QrCodeState {
        if (qrCodeState == QrCodeState.Unknown) {
            qrCodeState = queryState()
        }
        
        return qrCodeState
    }
    
    fun logout() {
        packet.disconnect()
    }

    private suspend fun queryState(): QrCodeState {
        val transEmp = wtlogin(keystore, appInfo).buildTransEmp0x12()
        val response = packet.sendPacket("wtlogin.trans_emp", transEmp)
        return wtlogin(keystore, appInfo).parseTransEmp0x12(response.response)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun online(): Boolean {
        val proto = protobufOf(
            1 to keystore.guid.toHex().lowercase(),
            2 to 0, // kickPC
            3 to appInfo.currentVersion,
            4 to 0, // IsFirstRegisterProxyOnline
            5 to 2052, // localeId
            6 to protobufOf(
                1 to keystore.deviceName,
                2 to appInfo.kernel,
                3 to "Windows 10.0.19042",
                4 to "",
                5 to appInfo.vendorOs
            ),
            7 to 0, // SetMute
            8 to 0, // RegisterVendorType
            9 to 1, // RegType
        )

        val sso = packet.sendPacket("trpc.qq_new_tech.status_svc.StatusService.Register", proto.toByteArray())
        if (sso.retCode != 0) {
            logger.error("Failed to register, retCode: ${sso.retCode} | retMsg: ${sso.extra}")
            return false
        }
        
        val parsed = ProtoUtils.decodeFromByteArray(sso.response)
        val success = parsed[2]?.asUtf8String?.contains("register success") == true
        online = success

        GlobalScope.launch {
            while (true) {
                val ssoHeartBeat = protobufOf(1 to 1)
                packet.sendPacket("trpc.qq_new_tech.status_svc.StatusService.SsoHeartBeat", ssoHeartBeat.toByteArray())
                delay(60 * 1000)
            }
        }
        
        return success
    }
}