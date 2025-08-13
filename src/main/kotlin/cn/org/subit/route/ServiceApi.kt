@file:Suppress("PackageDirectoryMismatch")

package cn.org.subit.route.serviceApi

import cn.org.subit.JWTAuth
import cn.org.subit.JWTAuth.getLoginService
import cn.org.subit.JWTAuth.getLoginUser
import cn.org.subit.JWTAuth.getOAuthAccessToken
import cn.org.subit.JWTAuth.getOAuthCodeUser
import cn.org.subit.JWTAuth.getOAuthRefreshToken
import cn.org.subit.dataClasses.*
import cn.org.subit.dataClasses.UserId.Companion.toUserIdOrNull
import cn.org.subit.database.Authorizations
import cn.org.subit.database.StudentIds
import cn.org.subit.database.Users
import cn.org.subit.route.utils.*
import cn.org.subit.utils.FileUtils.getAvatar
import cn.org.subit.utils.HttpStatus
import cn.org.subit.utils.decodeSearchListOrNull
import cn.org.subit.utils.statuses
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import javax.imageio.ImageIO
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Suppress("DuplicatedCode")
fun Route.serviceApi() = route("/serviceApi", {
    tags("服务接口")
    specId = "serviceApi"
})
{
    val rootPath = application.rootPath

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
        authenticate("ssubito-oauth-code", "ssubito-auth", strategy = AuthenticationStrategy.Required)
        {
            get("/accessToken", {
                securitySchemeNames("Authorization", "Oauth-Code")
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
                        HttpStatus.InvalidToken.subStatus(code = 1),
                        HttpStatus.InvalidOAuthCode.subStatus(code = 2),
                        HttpStatus.NotLoggedIn.subStatus(code = 3),
                        HttpStatus.BadRequest.subStatus("time too long", 4),
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
                HttpStatus.InvalidToken.subStatus(code = 1),
                HttpStatus.InvalidOAuthCode.subStatus(code = 2),
                HttpStatus.NotLoggedIn.subStatus(code = 3),
                HttpStatus.NotFound,
                HttpStatus.BadRequest.subStatus("time too long", 4),
                HttpStatus.BadRequest.subStatus("user is required", 5),
            )
        }
    }) { getAccessToken() }

    get("/authorizations", {
        summary = "获得授权该服务的用户列表"
        description = """
            获得授权该服务的用户列表, 该接口需要在Authorization中添加服务token.
        """.trimIndent()
        request {
            paged()
        }
        response {
            statuses<Slice<AuthorizationInfo>>(HttpStatus.OK, example = sliceOf(AuthorizationInfo.example))
        }
    }) { getAuthorizations() }

    route("/search")
    {
        get("/username", {
            summary = "通过用户名（关键字）搜索用户"
            description = """
                通过用户名搜索用户, 该接口需要在Authorization中添加服务token.
                
                注意该接口仅返回授权当前服务的用户.
            """.trimIndent()
            request {
                queryParameter<String>("key")
                {
                    allowEmptyValue = true
                    required = true
                    description = "用户名关键字"
                }
                queryParameter<List<AuthorizationStatus>>("authorizationState")
                {
                    required = false
                    description = "用户的授权状态可选列表，json列表，不传/格式错误则为不限制"
                }
                paged()
            }
            response {
                statuses<Slice<UserId>>(HttpStatus.OK, example = sliceOf(UserId(1)))
            }
        }, Context::searchUserByUsername)

        get("/realName", {
            summary = "通过真实姓名（关键字）搜索用户"
            description = """
                通过真实姓名搜索用户, 该接口需要在Authorization中添加服务token.
                
                注意该接口仅返回授权当前服务的用户.
            """.trimIndent()
            request {
                queryParameter<String>("key")
                {
                    required = true
                    description = "真实姓名关键字"
                }
                queryParameter<List<AuthorizationStatus>>("authorizationState")
                {
                    required = false
                    description = "用户的授权状态可选列表，json列表，不传/格式错误则为不限制"
                }
                paged()
            }
            response {
                statuses<Slice<UserId>>(HttpStatus.OK, example = sliceOf(UserId(1)))
            }
        }, Context::searchUserByRealName)

        get("/studentId", {
            summary = "通过学号前缀搜索用户"
            description = """
                通过学号搜索用户, 该接口需要在Authorization中添加服务token.
                
                注意该接口仅返回授权当前服务的用户.
            """.trimIndent()
            request {
                queryParameter<String>("key")
                {
                    required = true
                    description = "学号关键字"
                }
                queryParameter<List<AuthorizationStatus>>("authorizationState")
                {
                    required = false
                    description = "用户的授权状态可选列表，json列表，不传/格式错误则为不限制"
                }
                paged()
            }
            response {
                statuses<Slice<UserId>>(HttpStatus.OK, example = sliceOf(UserId(1)))
            }
        }, Context::searchUserByStudentId)
    }

    get("/info", {
        summary = "通过access token获取用户和服务信息"
        description = """
            通过access token获取用户和服务信息, 该接口需要在Authorization中添加access token.
            
            当用户不存在时返回404. 当当前服务无权获得该用户的任何信息时, 返回200, 但user为该用户的id.
        """.trimIndent()
        response {
            statuses<Information<UserFull>>(HttpStatus.OK.subStatus("获取全部用户信息", 1), example = Information(UserFull.example, BasicServiceInfo.example))
            statuses<Information<BasicUserInfo>>(HttpStatus.OK.subStatus("获取基本用户信息", 2), example = Information(BasicUserInfo.example, BasicServiceInfo.example))
            statuses<Information<WrappingUserId>>(HttpStatus.OK.subStatus("无权获得用户信息", 3), example = Information(WrappingUserId(UserId(1)), BasicServiceInfo.example))
            statuses(HttpStatus.NotFound)
            statuses(HttpStatus.InvalidToken)
        }
    }) { getInfo() }

    get("/avatar/{id}", {
        description = "获取用户头像"
        request {
            pathParameter<UserId>("id")
            {
                required = true
                description = "要获取的用户ID, 0为当前登陆用户, 若id不为0则无需登陆, 否则需要登陆"
            }
        }
        response {
            statuses(ContentType.Image.PNG, HttpStatus.OK, bodyDescription = "获取到的头像, 总是png格式的")
            statuses(HttpStatus.BadRequest, HttpStatus.NotLoggedIn)
        }
    }) { getAvatar() }
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

private suspend fun Context.getAccessToken(): Nothing
{
    val time = call.request.queryParameters["time"]?.toIntOrNull()?.seconds ?: JWTAuth.OAUTH_ACCESS_TOKEN_DEFAULT_VALIDITY
    val user = call.request.queryParameters["user"]?.toUserIdOrNull() ?: finishCall(HttpStatus.BadRequest.subStatus("user is required"))
    val service = getLoginService() ?: finishCall(HttpStatus.NotLoggedIn)
    get<Users>().getUser(user) ?: finishCall(HttpStatus.NotFound)
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
private data class AccessToken(val accessToken: String, val tokenType: String, val accessTokenExpiresIn: Long)

private fun Context.refreshAccessToken(): Nothing
{
    val refreshToken = getOAuthRefreshToken() ?: finishCall(HttpStatus.InvalidToken)
    val time = call.request.queryParameters["time"]?.toIntOrNull()?.seconds ?: JWTAuth.OAUTH_ACCESS_TOKEN_DEFAULT_VALIDITY
    val accessToken = JWTAuth.makeOAuthAccessToken(refreshToken.service.id, refreshToken.user, time) ?: finishCall(HttpStatus.BadRequest.subStatus("time too long"))
    finishCall(HttpStatus.OK, AccessToken(accessToken.token, "Bearer", time.inWholeSeconds))
}

@Serializable
private data class Information<User>(
    val user: User,
    val service: BasicServiceInfo,
)

@Serializable
private data class WrappingUserId(val id: UserId)

private suspend fun Context.getInfo()
{
    val token = getOAuthAccessToken() ?: finishCall(HttpStatus.InvalidToken)
    val auth = get<Authorizations>().getAuthorization(token.user, token.service.id)
    val permission = when (auth?.cancel)
    {
        true -> token.service.cancelAuthorization
        false -> token.service.authorized
        null -> token.service.unauthorized
    }
    val user: Any = when (permission)
    {
        ServicePermission.NONE -> token.user
        ServicePermission.BASIC -> get<Users>().getUser(token.user)?.toUserFull()?.toBasicUserInfo() ?: finishCall(HttpStatus.NotFound)
        ServicePermission.ALL -> get<Users>().getUser(token.user)?.toUserFull() ?: finishCall(HttpStatus.NotFound)
    }

    when (user)
    {
        is BasicUserInfo -> finishCall(HttpStatus.OK.subStatus("获取基本用户信息"), Information(user, token.service.toBasicServiceInfo()))
        is UserFull -> finishCall(HttpStatus.OK.subStatus("获取全部用户信息"), Information(user, token.service.toBasicServiceInfo()))
        is UserId -> finishCall(HttpStatus.OK.subStatus("无权获得用户信息"), Information(WrappingUserId(user), token.service.toBasicServiceInfo()))
        else -> finishCall(HttpStatus.InvalidToken)
    }
}

private suspend fun Context.getAuthorizations()
{
    val service = getLoginService() ?: finishCall(HttpStatus.NotLoggedIn)
    val (begin, count) = call.getPage()
    val authorizations = get<Authorizations>().getAuthorizations(service.id, begin, count)
    finishCall(HttpStatus.OK, authorizations)
}

private suspend fun Context.searchUserByUsername(): Nothing
{
    val service = getLoginService() ?: finishCall(HttpStatus.NotLoggedIn)
    val key = call.request.queryParameters["key"] ?: finishCall(HttpStatus.BadRequest.subStatus("key is required"))
    val authorizationStatus = call.request.queryParameters["authorizationState"].decodeSearchListOrNull<List<AuthorizationStatus>>()
    val (begin, count) = call.getPage()
    val users = get<Users>().searchUser(key, service.id, begin, count, authorizationStatus)
    finishCall(HttpStatus.OK, users.map { it.id })
}

private suspend fun Context.searchUserByRealName(): Nothing
{
    val service = getLoginService() ?: finishCall(HttpStatus.NotLoggedIn)
    val key = call.request.queryParameters["key"] ?: finishCall(HttpStatus.BadRequest.subStatus("key is required"))
    val authorizationStatus = call.request.queryParameters["authorizationState"].decodeSearchListOrNull<List<AuthorizationStatus>>()
    val (begin, count) = call.getPage()
    val users = get<StudentIds>().searchUserByRealName(key, service.id, begin, count, authorizationStatus)
    finishCall(HttpStatus.OK, users)
}

private suspend fun Context.searchUserByStudentId(): Nothing
{
    val service = getLoginService() ?: finishCall(HttpStatus.NotLoggedIn)
    val key = call.request.queryParameters["key"] ?: finishCall(HttpStatus.BadRequest.subStatus("key is required"))
    val authorizationStatus = call.request.queryParameters["authorizationState"].decodeSearchListOrNull<List<AuthorizationStatus>>()
    val (begin, count) = call.getPage()
    val users = get<StudentIds>().searchUserByStudentId(key, service.id, begin, count, authorizationStatus)
    finishCall(HttpStatus.OK, users)
}

@Suppress("DuplicatedCode")
private fun Context.getAvatar()
{
    val doNotCache: Boolean
    val id = (call.parameters["id"]?.toUserIdOrNull() ?: finishCall(HttpStatus.BadRequest)).let {
        doNotCache = it == UserId(0)
        if (it == UserId(0)) getLoginUser()?.id ?: finishCall(HttpStatus.NotLoggedIn)
        else it
    }
    val avatar = getAvatar(id)
    if (!doNotCache) call.response.header(HttpHeaders.CacheControl, "max-age=${15*60}")
    finishCallWithBytes(HttpStatus.OK, ContentType.Image.PNG) { ImageIO.write(avatar, "png", this) }
}