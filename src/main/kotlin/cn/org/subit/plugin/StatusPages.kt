package cn.org.subit.plugin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import cn.org.subit.logger.SSubitOLogger
import cn.org.subit.utils.HttpStatus
import cn.org.subit.utils.respond
import io.ktor.server.response.*
import kotlin.time.Duration.Companion.seconds

/**
 * 对于不同的状态码返回不同的页面
 */
fun Application.installStatusPages() = install(StatusPages)
{
    val logger = SSubitOLogger.getLogger()
    exception<BadRequestException> { call, _ -> call.respond(HttpStatus.BadRequest) }
    exception<Throwable>
    { call, throwable ->
        SSubitOLogger.getLogger("ForumBackend.installStatusPages")
            .warning("出现位置错误, 访问接口: ${call.request.path()}", throwable)
        call.respond(HttpStatus.InternalServerError)
    }

    /** 针对请求过于频繁的处理, 详见[RateLimit] */
    status(HttpStatusCode.TooManyRequests)
    { _ ->
        val time = call.response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.seconds
        val typeName = call.response.headers["X-RateLimit-Type"]
        val type = RateLimit.list.find { it.rawRateLimitName == typeName }
        logger.config("TooManyRequests with type: $type($typeName), retryAfter: $time")
        if (time == null)
            return@status call.respond(HttpStatus.TooManyRequests)
        if (type == null)
            return@status call.respond(HttpStatus.TooManyRequests.copy(message = "请求过于频繁, 请${time}后再试"))
        type.customResponse(call, time)
    }

    status(HttpStatusCode.Unauthorized)
    { _ ->
        val rootPath = this.call.application.environment.rootPath
        // 如果不是api docs还没有返回体的话, 说明是携带了token但token不合法, 返回401
        if (!call.request.path().startsWith("$rootPath/api-docs") && call.response.responseType == null)
            call.respond(HttpStatus.Unauthorized)
    }


}