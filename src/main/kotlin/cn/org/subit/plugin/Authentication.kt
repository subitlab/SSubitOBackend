package cn.org.subit.plugin

import cn.org.subit.JWTAuth
import cn.org.subit.JWTAuth.initJwtAuth
import cn.org.subit.config.apiDocsConfig
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

/**
 * 安装登陆验证服务
 */
fun Application.installAuthentication() = install(Authentication)
{
    // 初始化jwt验证
    this@installAuthentication.initJwtAuth()
    // jwt验证, 这个验证是用于论坛正常的用户登陆
    jwt("ssubito-auth")
    {
        verifier(JWTAuth.makeJwtVerifier()) // 设置验证器
        validate() // 设置验证函数
        {
            runCatching { JWTAuth.checkToken(it.payload) }.getOrNull()
        }
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