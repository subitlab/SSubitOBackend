package cn.org.subit.plugin

import cn.org.subit.route.basic.EmailInfo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import cn.org.subit.utils.HttpStatus
import cn.org.subit.utils.respond
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

sealed interface RateLimit
{
    val rawRateLimitName: String
    val limit: Int
    val duration: Duration
    val rateLimitName: RateLimitName
        get() = RateLimitName(rawRateLimitName)
    suspend fun customResponse(call: ApplicationCall, duration: Duration)
    suspend fun getKey(call: ApplicationCall): Any

    companion object
    {
        val list = listOf(SendEmail)
    }

    data object SendEmail: RateLimit
    {
        override val rawRateLimitName = "sendEmail"
        override val limit = 1
        override val duration = 1.minutes
        override suspend fun customResponse(call: ApplicationCall, duration: Duration)
        {
            call.respond(HttpStatus.TooManyRequests.copy(message = "发送邮件过于频繁, 请${duration}后再试"))
        }

        /**
         * 按照请求体中的邮箱及其用途来限制. 如果接收不到请求体的话应该会返回BadRequest, 所以这里通过随机UUID来不限制
         */
        override suspend fun getKey(call: ApplicationCall): Any =
            runCatching {
                call.receive<EmailInfo>().let { it.copy(email = it.email.lowercase()) }
            }.getOrNull() ?: UUID.randomUUID()
    }
}

/**
 * 安装速率限制插件, 该插件可以限制请求的速率, 防止恶意请求
 */
fun Application.installRateLimit() = install(io.ktor.server.plugins.ratelimit.RateLimit)
{
    RateLimit.list.forEach()
    { rateLimit ->
        register(rateLimit.rateLimitName)
        {
            rateLimiter(limit = rateLimit.limit, refillPeriod = rateLimit.duration)
            requestKey { rateLimit.getKey(call = it) }
            modifyResponse { call, state ->
                call.response.headers.appendIfAbsent("X-RateLimit-Type", rateLimit.rawRateLimitName, false)
                when (state)
                {
                    is RateLimiter.State.Available ->
                    {
                        call.response.headers.appendIfAbsent("X-RateLimit-Limit", state.limit.toString())
                        call.response.headers.appendIfAbsent("X-RateLimit-Remaining", state.remainingTokens.toString())
                        call.response.headers.appendIfAbsent(
                            "X-RateLimit-Reset",
                            (state.refillAtTimeMillis / 1000).toString()
                        )
                    }

                    is RateLimiter.State.Exhausted ->
                    {
                        call.response.headers.appendIfAbsent(HttpHeaders.RetryAfter, state.toWait.inWholeSeconds.toString())
                    }
                }
            }
        }
    }

    global {
        rateLimiter(limit = 150, refillPeriod = 1.seconds)
        requestKey { call -> call.request.local.remoteHost }
    }
}