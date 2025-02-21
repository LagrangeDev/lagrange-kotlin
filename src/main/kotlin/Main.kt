package org.lagrange.dev

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.lagrange.dev.common.AppInfo
import org.lagrange.dev.common.Keystore
import org.lagrange.dev.utils.sign.UrlSignProvider
import java.nio.file.Files
import java.nio.file.Paths

fun main() {
    val signApiUrl = "https://sign.lagrangecore.org/api/sign/25765"
    val urlSignProvider = UrlSignProvider(signApiUrl)
    val appInfo = urlSignProvider.getAppInfo() ?: AppInfo.linux
    if (!Files.exists(Paths.get("/Users/wenxuanlin/Desktop/Project/OicqRepos/lagrange-kotlin/keystore.json"))) {
        val bot = BotContext(Keystore.generateEmptyKeystore(), appInfo, UrlSignProvider(signApiUrl))
        val (url, qrcode) = runBlocking {
            bot.fetchQrCode()
        }
        Files.write(Paths.get("/Users/wenxuanlin/Desktop/Project/OicqRepos/lagrange-kotlin/qrcode.png"), qrcode)

        val success = runBlocking {
            bot.loginByQrCode()
        }

        if (success) {
            val keystore = bot.keystore
            val json = Json.encodeToString(keystore)
            Files.write(Paths.get("/Users/wenxuanlin/Desktop/Project/OicqRepos/lagrange-kotlin/keystore.json"), json.toByteArray())
        }
    } else {
        val json = String(Files.readAllBytes(Paths.get("/Users/wenxuanlin/Desktop/Project/OicqRepos/lagrange-kotlin/keystore.json")))
        val keystore = Json.decodeFromString(Keystore.serializer(), json)
        val bot = BotContext(keystore, appInfo, UrlSignProvider(signApiUrl))
        
        runBlocking {
            bot.loginByToken()
            bot.getFriendList()
        }
    }

    Thread.sleep(Long.MAX_VALUE)
}