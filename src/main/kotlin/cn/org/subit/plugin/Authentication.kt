@file:Suppress("PackageDirectoryMismatch")

package cn.org.subit.plugin.authentication

import cn.org.subit.JWTAuth
import cn.org.subit.JWTAuth.initJwtAuth
import cn.org.subit.config.apiDocsConfig
import cn.org.subit.logger.SSubitOLogger
import cn.org.subit.utils.HttpStatus
import cn.org.subit.utils.respond
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*

/**
 * 安装登陆验证服务
 */
fun Application.installAuthentication() = install(Authentication)
{
    val logger = SSubitOLogger.getLogger()
    // 初始化jwt验证
    this@installAuthentication.initJwtAuth()
    // jwt验证, 这个验证是用于论坛正常的用户登陆
    jwt("ssubito-auth")
    {
        authHeader {
            val token = it.request.header(HttpHeaders.Authorization) ?: run {
                val t = parseHeaderValue(it.request.header(HttpHeaders.SecWebSocketProtocol))
                val index = t.indexOfFirst { headerValue -> headerValue.value == "Bearer" }
                if (index == -1) return@authHeader null
                it.response.header(HttpHeaders.SecWebSocketProtocol, "Bearer")
                t.getOrNull(index + 1)?.value?.let { token -> "Bearer $token" }
            }
            val res = token?.let(::parseAuthorizationHeader)
            logger.config("ssubito-auth token: $res")
            res
        }
        verifier(JWTAuth.makeJwtVerifier()) // 设置验证器
        validate() // 设置验证函数
        {
            runCatching { JWTAuth.checkToken(it.payload) }.getOrNull()
        }
        challenge { _, _ -> call.respond(HttpStatus.InvalidToken) }
    }

    jwt("ssubito-oauth-code")
    {
        authHeader { it.request.header("Oauth-Code")?.let(::parseAuthorizationHeader) }
        verifier(JWTAuth.makeJwtVerifier()) // 设置验证器
        validate()
        {
            runCatching { JWTAuth.checkToken(it.payload) }.getOrNull()
        }
        challenge { _, _ -> call.respond(HttpStatus.InvalidOAuthCode) }
    }

    // 此登陆仅用于api文档的访问, 见ApiDocs插件
    basic("auth-api-docs")
    {
        realm = "Access to the Swagger UI"
        validate()
        {
            if (it.name == apiDocsConfig.name && it.password == apiDocsConfig.password)
                UserIdPrincipal(it.name)
            else null
        }
    }
}