package cn.org.subit.dataClasses

import io.ktor.server.auth.*
import cn.org.subit.JWTAuth.TokenType
import kotlinx.serialization.Serializable

/**
 * @see TokenType 令牌类型
 */
sealed interface SsoPrincipal: Principal

@Serializable data class OAuthCodePrincipal(val user: UserId): SsoPrincipal
@Serializable data class OAuthAccessTokenPrincipal(val user: UserId, val service: ServiceInfo): SsoPrincipal
@Serializable data class OAuthRefreshTokenPrincipal(val user: UserId, val service: ServiceInfo): SsoPrincipal