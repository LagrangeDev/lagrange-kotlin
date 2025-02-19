package org.lagrange.dev.component

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import org.lagrange.dev.BotContext
import org.slf4j.LoggerFactory

internal class BotHighway(private val context: BotContext) {
    private val logger = LoggerFactory.getLogger(BotHighway::class.java)
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }
}