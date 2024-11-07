package org.lagrange.dev

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.lagrange.dev.common.AppInfo
import org.lagrange.dev.common.Keystore
import java.nio.file.Files
import java.nio.file.Paths

fun main() {
    if (!Files.exists(Paths.get("/Users/wenxuanlin/Desktop/Project/OicqRepos/lagrange-kotlin/keystore.json"))) {
        val bot = BotContext(Keystore.generateEmptyKeystore(), AppInfo.linux)
        val (url, qrcode) = runBlocking {
            bot.fetchQrCode()
        }
        // /Users/wenxuanlin/Desktop/Project/OicqRepos/lagrange-kotlin
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
        val bot = BotContext(keystore, AppInfo.linux)
        
        runBlocking {
            bot.loginByToken()
            bot.getFriendList()
        }
    }

    Thread.sleep(Long.MAX_VALUE)
}