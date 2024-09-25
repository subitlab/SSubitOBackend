@file:Suppress("PackageDirectoryMismatch")

package cn.org.subit.plugin.websockets

import cn.org.subit.plugin.contentnegotiation.contentNegotiationJson
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.websocket.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

fun Application.installWebSockets() = install(WebSockets)
{
    pingPeriod = 15.seconds.toJavaDuration()
    timeout = 15.seconds.toJavaDuration()
    maxFrameSize = Long.MAX_VALUE
    masking = false
    contentConverter = KotlinxWebsocketSerializationConverter(contentNegotiationJson)
}