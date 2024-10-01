package cn.org.subit

import at.favre.lib.crypto.bcrypt.BCrypt
import cn.org.subit.console.SimpleAnsiColor.Companion.CYAN
import cn.org.subit.console.SimpleAnsiColor.Companion.RED
import cn.org.subit.dataClasses.*
import cn.org.subit.dataClasses.ServiceId.Companion.toServiceId
import cn.org.subit.dataClasses.UserId.Companion.toUserId
import cn.org.subit.database.Services
import cn.org.subit.database.Users
import cn.org.subit.logger.SSubitOLogger
import cn.org.subit.utils.toEnumOrNull
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Payload
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.pipeline.*
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.OffsetDateTime
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * JWT验证
 */
@Suppress("MemberVisibilityCanBePrivate")
object JWTAuth: KoinComponent
{
    private val logger = SSubitOLogger.getLogger()
    private val users: Users by inject()
    private val services: Services by inject()

    @Serializable
    data class Token(val token: String)

    /**
     * JWT密钥
     */
    private lateinit var SECRET_KEY: String

    /**
     * JWT算法
     */
    private lateinit var algorithm: Algorithm

    /**
     * 用户JWT Token有效期
     */
    val USER_TOKEN_VALIDITY: Duration = 90.days

    /**
     * 服务JWT Token有效期
     */
     val SERVICE_TOKEN_VALIDITY: Duration = 180.days

    /**
     * OAuth授权码有效期
     */
    val OAUTH_CODE_VALIDITY: Duration = 10.minutes

    /**
     * OAuth access token最长有效期
     */
    val OAUTH_ACCESS_TOKEN_MAX_VALIDITY: Duration = 30.days

    /**
     * OAuth access token默认有效期
     */
    val OAUTH_ACCESS_TOKEN_DEFAULT_VALIDITY: Duration = 1.days

    /**
     * OAuth refresh token有效期
     */
    val OAUTH_REFRESH_TOKEN_VALIDITY: Duration = 90.days

    enum class TokenType
    {
        /**
         * 用户, 验证对应的Principal为[UserInfo]
         */
        USER,

        /**
         * 服务, 验证对应的Principal为[ServiceInfo]
         */
        SERVICE,

        /**
         * OAuth授权码, 验证对应的Principal为[OAuthCodePrincipal]
         */
        OAUTH_CODE,

        /**
         * OAuth access token, 验证对应的Principal为[OAuthAccessTokenPrincipal]
         */
        OAUTH_ACCESS_TOKEN,

        /**
         * OAuth refresh token, 验证对应的Principal为[OAuthRefreshTokenPrincipal]
         */
        OAUTH_REFRESH_TOKEN
    }

    fun Application.initJwtAuth()
    {
        // 从配置文件中读取密钥
        val key = environment.config.propertyOrNull("jwt.secret")?.getString()
        if (key == null)
        {
            logger.info("${CYAN}jwt.secret${RED} not found in config file, use random secret key")
            SECRET_KEY = UUID.randomUUID().toString()
        }
        else
        {
            SECRET_KEY = key
        }
        // 初始化JWT算法
        algorithm = Algorithm.HMAC512(SECRET_KEY)
    }

    /**
     * 生成验证器
     */
    fun makeJwtVerifier(): JWTVerifier = JWT.require(algorithm).build()

    private fun makeToken(type: TokenType, validity: Duration, vararg claims: Pair<String, Int>): Token = JWT.create()
        .withSubject("Authentication")
        .withClaim("type", type.name)
        .apply { claims.forEach { (k, v) -> withClaim(k, v) } }
        .withExpiresAt((OffsetDateTime.now().toInstant().toKotlinInstant() + validity).toJavaInstant())
        .withIssuer("subit")
        .withIssuedAt(OffsetDateTime.now().toInstant().toKotlinInstant().toJavaInstant())
        .sign(algorithm)
        .let(::Token)

    fun makeUserToken(user: UserId) =
        makeToken(TokenType.USER, USER_TOKEN_VALIDITY, "id" to user.value)
    fun makeServiceToken(service: ServiceId) =
        makeToken(TokenType.SERVICE, SERVICE_TOKEN_VALIDITY, "id" to service.value)
    fun makeOAuthCodeToken(user: UserId) =
        makeToken(TokenType.OAUTH_CODE, OAUTH_CODE_VALIDITY, "id" to user.value)
    fun makeOAuthAccessToken(service: ServiceId, user: UserId, validity: Duration) =
        if (validity > OAUTH_ACCESS_TOKEN_MAX_VALIDITY) null
        else makeToken(TokenType.OAUTH_ACCESS_TOKEN, validity, "service" to service.value, "user" to user.value)
    fun makeOAuthRefreshToken(service: ServiceId, user: UserId) =
        makeToken(TokenType.OAUTH_REFRESH_TOKEN, OAUTH_REFRESH_TOKEN_VALIDITY, "service" to service.value, "user" to user.value)

    suspend fun checkToken(token: Payload): SsoPrincipal?
    {
        val tokenType = token.getClaim("type").asString().toEnumOrNull<TokenType>() ?: return null
        logger.config("Check token type: $tokenType")
        return when (tokenType)
        {
            TokenType.USER ->
            {
                val (userInfo, lastPswChange) =
                    users.getUserWithLastPasswordChange(token.getClaim("id").asInt().toUserId()) ?: return null
                val tokenTime = token.issuedAtAsInstant.toKotlinInstant()
                if (lastPswChange > tokenTime)
                {
                    logger.config("User token issued before password change, last change time: $lastPswChange, token time: $tokenTime")
                    return null
                }
                userInfo
            }
            TokenType.SERVICE ->
            {
                val (service, revokedTime) =
                    services.getServiceWithSecretRevokedTime(token.getClaim("id").asInt().toServiceId()) ?: return null
                val tokenTime = token.issuedAtAsInstant.toKotlinInstant()
                if (revokedTime > tokenTime)
                {
                    logger.config("Service token issued before service revoked, revoked time: $revokedTime, token time: $tokenTime")
                    return null
                }
                service
            }
            TokenType.OAUTH_CODE ->
            {
                OAuthCodePrincipal(token.getClaim("id").asInt().toUserId())
            }
            TokenType.OAUTH_ACCESS_TOKEN ->
            {
                val (service, revokedTime) =
                    services.getServiceWithSecretRevokedTime(token.getClaim("service").asInt().toServiceId()) ?: return null
                val tokenTime = token.issuedAtAsInstant.toKotlinInstant()
                if (revokedTime > tokenTime)
                {
                    logger.config("Access token issued before service revoked, revoked time: $revokedTime, token time: $tokenTime")
                    return null
                }
                OAuthAccessTokenPrincipal(
                    token.getClaim("user").asInt().toUserId(),
                    service
                )
            }
            TokenType.OAUTH_REFRESH_TOKEN ->
            {
                val (service, revokedTime) =
                    services.getServiceWithSecretRevokedTime(token.getClaim("service").asInt().toServiceId()) ?: return null
                val tokenTime = token.issuedAtAsInstant.toKotlinInstant()
                if (revokedTime > tokenTime)
                {
                    logger.config("Refresh token issued before service revoked, revoked time: $revokedTime, token time: $tokenTime")
                    return null
                }
                OAuthRefreshTokenPrincipal(
                    token.getClaim("user").asInt().toUserId(),
                    service
                )
            }
        }
    }

    fun PipelineContext<*, ApplicationCall>.getLoginUser(): UserInfo? = call.principal<UserInfo>()
    fun PipelineContext<*, ApplicationCall>.getLoginService(): ServiceInfo? = call.principal<ServiceInfo>()
    fun PipelineContext<*, ApplicationCall>.getOAuthCodeUser(): UserId? = call.principal<OAuthCodePrincipal>()?.user
    fun PipelineContext<*, ApplicationCall>.getOAuthAccessToken(): OAuthAccessTokenPrincipal? = call.principal<OAuthAccessTokenPrincipal>()
    fun PipelineContext<*, ApplicationCall>.getOAuthRefreshToken(): OAuthRefreshTokenPrincipal? = call.principal<OAuthRefreshTokenPrincipal>()

    private val hasher = BCrypt.with(BCrypt.Version.VERSION_2B)
    private val verifier = BCrypt.verifyer(BCrypt.Version.VERSION_2B)

    /**
     * 在数据库中保存密码的加密
     */
    fun encryptPassword(password: String): String = hasher.hashToString(12, password.toCharArray())
    fun verifyPassword(password: String, hash: String): Boolean = verifier.verify(password.toCharArray(), hash).verified
}
