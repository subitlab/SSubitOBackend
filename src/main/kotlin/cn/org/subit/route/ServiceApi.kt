@file:Suppress("PackageDirectoryMismatch")

package cn.org.subit.route.serviceApi

import cn.org.subit.JWTAuth
import cn.org.subit.JWTAuth.getLoginService
import cn.org.subit.JWTAuth.getOAuthAccessToken
import cn.org.subit.JWTAuth.getOAuthCodeUser
import cn.org.subit.JWTAuth.getOAuthRefreshToken
import cn.org.subit.dataClasses.*
import cn.org.subit.dataClasses.UserId.Companion.toUserIdOrNull
import cn.org.subit.database.Authorizations
import cn.org.subit.database.Users
import cn.org.subit.route.utils.Context
import cn.org.subit.route.utils.finishCall
import cn.org.subit.route.utils.finishCallWithRedirect
import cn.org.subit.route.utils.get
import cn.org.subit.utils.HttpStatus
import cn.org.subit.utils.statuses
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

fun Route.serviceApi() = route("/serviceApi", {
    tags("服务接口")
    specId = "serviceApi"
})
{
    val rootPath = application.environment.rootPath

    route("/api-docs")
    {
        route("/api.json")
        {
            openApiSpec("serviceApi")
        }
        swaggerUI("$rootPath/serviceApi/api-docs/api.json")
    }

    get("", { hidden = true }) { finishCallWithRedirect("$rootPath/serviceApi/api-docs") }

    route("/oauth")
    {
        authenticate("ssubito-oauth-code", optional = true)
        {
            get("/accessToken", {
                securitySchemeNames("Authorization", "OAuth-Code")
                description = """
                    按照OAuth授权码授权流程获取访问令牌, 该接口需要在Authorization中添加服务token以及在OAuth-Code中添加授权码.
                    
                    注意无论用户 未授权当前服务/授权当前服务/取消授权当前服务 该接口都会返回成功, 且获得access token和refresh token.
                    
                    可通过`/status`接口查看用户对当前服务的授权状态.
                """.trimIndent()
                summary = "按OAuth授权码授权流程获取OAuth的访问令牌"
                request {
                    queryParameter<Int>("time")
                    {
                        required = false
                        description = """
                            accessToken有效期, 单位为秒, 不能超过${JWTAuth.OAUTH_ACCESS_TOKEN_MAX_VALIDITY.inWholeSeconds}, 默认为${JWTAuth.OAUTH_ACCESS_TOKEN_DEFAULT_VALIDITY.inWholeSeconds}.
                            
                            出于安全考虑, 建议accessToken有效期不要超过${JWTAuth.OAUTH_ACCESS_TOKEN_DEFAULT_VALIDITY.inWholeSeconds}秒.
                        """.trimIndent()
                    }
                }
                response {
                    statuses<AccessAndRefreshToken>(
                        HttpStatus.OK,
                        bodyDescription = """
                        `access token`和`refresh token`, `access token`有效期为`accessTokenExpiresIn`, `refresh token`有效期为`refreshTokenExpiresIn`,
                        单位均为秒.
                        
                        若`time`参数未指定, 则`accessTokenExpiresIn`默认为${JWTAuth.OAUTH_ACCESS_TOKEN_DEFAULT_VALIDITY.inWholeSeconds}秒.
                        否则`accessTokenExpiresIn`为`time`参数指定的值.
                        
                        `refresh token`有效期目前固定为${JWTAuth.OAUTH_REFRESH_TOKEN_VALIDITY.inWholeSeconds}秒.
                        
                        tokenType目前固定为`Bearer`.
                        """.trimIndent(),
                        example = AccessAndRefreshToken.example,
                    )
                    statuses(
                        HttpStatus.InvalidToken,
                        HttpStatus.InvalidOAuthCode,
                        HttpStatus.NotLoggedIn,
                        HttpStatus.BadRequest.subStatus("time too long"),
                    )
                }
            }) { oauthGetAccessToken() }
        }

        get("/status", {
            description = "获取用户对当前服务的授权状态, 该接口需要在Authorization中添加access token."
            summary = "获取用户对当前服务的授权状态"
            response {
                statuses<AuthorizationStatus>(HttpStatus.OK, examples = AuthorizationStatus.entries)
            }
        }) { getStatus() }

        get("/refresh", {
            description = "通过refresh token获得新的access token, 该接口需要在Authorization中添加refresh token."
            summary = "通过refresh token获得新的access token"
            request {
                queryParameter<Int>("time")
                {
                    required = false
                    description = """
                        accessToken有效期, 单位为秒, 不能超过${JWTAuth.OAUTH_ACCESS_TOKEN_MAX_VALIDITY.inWholeSeconds}, 默认为${JWTAuth.OAUTH_ACCESS_TOKEN_DEFAULT_VALIDITY.inWholeSeconds}.
                        
                        出于安全考虑, 建议accessToken有效期不要超过${JWTAuth.OAUTH_ACCESS_TOKEN_DEFAULT_VALIDITY.inWholeSeconds}秒.
                    """.trimIndent()
                }
            }
            response {
                statuses<AccessToken>(HttpStatus.OK, example = AccessToken("access token", "Bearer", JWTAuth.OAUTH_ACCESS_TOKEN_DEFAULT_VALIDITY.inWholeSeconds))
                statuses(HttpStatus.InvalidToken)
            }
        }) { refreshAccessToken() }
    }

    get("/accessToken", {
        summary = "通过用户id获得OAuth的访问令牌"
        description = """
            通过用户id获取OAuth的访问令牌, 该接口需要在Authorization中添加服务token.
            
            注意无论用户 未授权当前服务/授权当前服务/取消授权当前服务 该接口都会返回成功, 且获得access token和refresh token.
                    
            可通过`/status`接口查看用户对当前服务的授权状态.
        """.trimIndent()
        request {
            queryParameter<Int>("time")
            {
                required = false
                description = """
                    accessToken有效期, 单位为秒, 不能超过${JWTAuth.OAUTH_ACCESS_TOKEN_MAX_VALIDITY.inWholeSeconds}, 默认为${JWTAuth.OAUTH_ACCESS_TOKEN_DEFAULT_VALIDITY.inWholeSeconds}.
                    
                    出于安全考虑, 建议accessToken有效期不要超过${JWTAuth.OAUTH_ACCESS_TOKEN_DEFAULT_VALIDITY.inWholeSeconds}秒.
                """.trimIndent()
            }
            queryParameter<UserId>("user")
            {
                required = true
                description = "用户id"
            }
        }
        response {
            statuses<AccessAndRefreshToken>(HttpStatus.OK, example = AccessAndRefreshToken.example)
            statuses(
                HttpStatus.InvalidToken,
                HttpStatus.InvalidOAuthCode,
                HttpStatus.NotLoggedIn,
                HttpStatus.BadRequest.subStatus("time too long"),
                HttpStatus.BadRequest.subStatus("user is required"),
            )
        }
    }) { getAccessToken() }

    get("/userinfo", {
        summary = "通过access token获取用户信息"
        description = "通过access token获取用户信息, 该接口需要在Authorization中添加access token. 若无权查看改用户的任何信息视为该用户不存在"
        response {
            statuses<UserFull>(HttpStatus.OK.subStatus("获取全部用户信息"), example = UserFull.example)
            statuses<BasicUserInfo>(HttpStatus.OK.subStatus("获取基本用户信息"), example = BasicUserInfo.example)
            statuses(HttpStatus.NotFound)
            statuses(HttpStatus.InvalidToken)
        }
    }) { getUserInfo() }
}

@Serializable
private data class AccessAndRefreshToken(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val accessTokenExpiresIn: Long,
    val refreshTokenExpiresIn: Long,
)
{
    companion object
    {
        val example get() = AccessAndRefreshToken("access token", "refresh token", "Bearer", JWTAuth.OAUTH_ACCESS_TOKEN_DEFAULT_VALIDITY.inWholeSeconds, JWTAuth.OAUTH_REFRESH_TOKEN_VALIDITY.inWholeSeconds)
    }
}

private fun Context.oauthGetAccessToken()
{
    val time = call.request.queryParameters["time"]?.toIntOrNull()?.seconds ?: JWTAuth.OAUTH_ACCESS_TOKEN_DEFAULT_VALIDITY
    val service = getLoginService() ?: finishCall(HttpStatus.NotLoggedIn)
    val codeUser = getOAuthCodeUser() ?: finishCall(HttpStatus.InvalidOAuthCode)
    getAccessToken(codeUser, service.id, time)
}

private fun Context.getAccessToken(): Nothing
{
    val time = call.request.queryParameters["time"]?.toIntOrNull()?.seconds ?: JWTAuth.OAUTH_ACCESS_TOKEN_DEFAULT_VALIDITY
    val user = call.request.queryParameters["user"]?.toUserIdOrNull() ?: finishCall(HttpStatus.BadRequest.subStatus("user is required"))
    val service = getLoginService() ?: finishCall(HttpStatus.NotLoggedIn)
    getAccessToken(user, service.id, time)
}

private fun getAccessToken(userId: UserId, serviceId: ServiceId, time: Duration): Nothing
{
    val accessToken = JWTAuth.makeOAuthAccessToken(serviceId, userId, time) ?: finishCall(HttpStatus.BadRequest.subStatus("time too long"))
    val refreshToken = JWTAuth.makeOAuthRefreshToken(serviceId, userId)
    val res = AccessAndRefreshToken(
        accessToken = accessToken.token,
        refreshToken = refreshToken.token,
        tokenType = "Bearer",
        accessTokenExpiresIn = time.inWholeSeconds,
        refreshTokenExpiresIn = JWTAuth.OAUTH_REFRESH_TOKEN_VALIDITY.inWholeSeconds,
    )
    finishCall(HttpStatus.OK, res)
}

private suspend fun Context.getStatus()
{
    val (user, service) = getOAuthAccessToken() ?: finishCall(HttpStatus.InvalidToken)
    val auth = get<Authorizations>().getAuthorization(user, service.id) ?: finishCall(HttpStatus.OK, AuthorizationStatus.UNAUTHORIZED)
    finishCall(HttpStatus.OK, if (auth.cancel) AuthorizationStatus.CANCELED else AuthorizationStatus.AUTHORIZED)
}

@Serializable
private data class AccessToken(val accessToken: String, val tokenType: String, val expiresIn: Long)

private fun Context.refreshAccessToken(): Nothing
{
    val refreshToken = getOAuthRefreshToken() ?: finishCall(HttpStatus.InvalidToken)
    val time = call.request.queryParameters["time"]?.toIntOrNull()?.seconds ?: JWTAuth.OAUTH_ACCESS_TOKEN_DEFAULT_VALIDITY
    val accessToken = JWTAuth.makeOAuthAccessToken(refreshToken.service.id, refreshToken.user, time) ?: finishCall(HttpStatus.BadRequest.subStatus("time too long"))
    finishCall(HttpStatus.OK, AccessToken(accessToken.token, "Bearer", time.inWholeSeconds))
}

private suspend fun Context.getUserInfo()
{
    val token = getOAuthAccessToken() ?: finishCall(HttpStatus.InvalidToken)
    val auth = get<Authorizations>().getAuthorization(token.user, token.service.id)
    val permission = when (auth?.cancel)
    {
        true -> token.service.authorized
        false -> token.service.cancelAuthorization
        null -> token.service.unauthorized
    }
    when (permission)
    {
        ServicePermission.NONE -> finishCall(HttpStatus.NotFound)
        ServicePermission.BASIC -> get<Users>().getUser(token.user)?.toUserFull()?.toBasicUserInfo()?.let { finishCall(HttpStatus.OK, it) } ?: finishCall(HttpStatus.NotFound)
        ServicePermission.ALL -> get<Users>().getUser(token.user)?.toUserFull()?.let { finishCall(HttpStatus.OK, it) } ?: finishCall(HttpStatus.NotFound)
    }
}