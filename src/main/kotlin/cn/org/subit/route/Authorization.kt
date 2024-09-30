@file:Suppress("PackageDirectoryMismatch")

package cn.org.subit.dataClasses.authorization

import cn.org.subit.JWTAuth
import cn.org.subit.JWTAuth.getLoginUser
import cn.org.subit.dataClasses.*
import cn.org.subit.dataClasses.AuthorizationId.Companion.toAuthorizationIdOrNull
import cn.org.subit.dataClasses.ServiceId.Companion.toServiceIdOrNull
import cn.org.subit.database.Authorizations
import cn.org.subit.database.Services
import cn.org.subit.route.utils.*
import cn.org.subit.utils.HttpStatus
import cn.org.subit.utils.statuses
import io.github.smiley4.ktorswaggerui.dsl.routing.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Route.authorization() = route("/authorization", {
    tags("授权")
})
{
    get("/list", {
        description = "获取自己的授权列表"
        summary = "获取授权列表"
        request {
            paged()
        }
        response {
            statuses<Slice<AuthorizationInfo>>(HttpStatus.OK, example = sliceOf(AuthorizationInfo.example))
        }
    }) { getList() }

    post("/grant/{id}", {
        description = "授权某项服务"
        summary = "授权"
        request {
            pathParameter<Int>("id")
            {
                required = true
                description = "服务id"
            }
        }
        response {
            statuses<AuthorizationId>(HttpStatus.OK, example = AuthorizationId(1))
        }
    }) { grant() }

    delete("/{id}", {
        description = "取消授权"
        summary = "取消授权"
        request {
            pathParameter<Int>("id")
            {
                required = true
                description = "授权id"
            }
        }
        response {
            statuses(HttpStatus.OK)
        }
    }) { revoke() }

    get("/{id}", {
        description = "通过授权id获取授权信息"
        summary = "获取授权信息"
        request {
            pathParameter<AuthorizationId>("id")
            {
                required = true
                description = "授权id"
            }
        }
        response {
            statuses<AuthorizationInfo>(HttpStatus.OK, example = AuthorizationInfo.example)
            statuses(HttpStatus.NotFound)
        }
    }) { getAuthorization() }

    get("/service/{id}", {
        description = "通过服务id获取授权信息"
        summary = "获取授权信息"
        request {
            pathParameter<ServiceId>("id")
            {
                required = true
                description = "服务id"
            }
        }
        response {
            statuses<AuthorizationInfo>(HttpStatus.OK, example = AuthorizationInfo.example)
            statuses(HttpStatus.NotFound)
        }
    }) { getAuthorizationByService() }

    get("/code", {
        description = """
            创建OAuth授权码, 用于OAuth2授权码模式.
            
            该授权码和服务无关, 仅和用户有关. 这意味着只要用户登录则该接口始终成功. 
            当服务按OAuth授权码授权流程重定向到sso前端进行登录验证时, 前端应先确认用户授权过该服务, 再通过该接口获得code并返回.
            否则该接口依旧会成功, 但在服务申请access token时失败
        """.trimIndent()
        summary = "创建授权码"
        response {
            statuses<String>(HttpStatus.OK, example = "token")
            statuses(HttpStatus.NotLoggedIn)
        }
    }) { createCode() }
}

private suspend fun Context.getList(): Nothing
{
    val user = getLoginUser() ?: finishCall(HttpStatus.NotLoggedIn)
    val authorizations = get<Authorizations>()
    val (begin, count) = call.getPage()
    val res = authorizations.getAuthorizations(user.id, begin, count)
    finishCall(HttpStatus.OK, res)
}

private suspend fun Context.grant(): Nothing
{
    val user = getLoginUser() ?: finishCall(HttpStatus.NotLoggedIn)
    val authorizations = get<Authorizations>()
    val serviceId = call.parameters["id"]?.toServiceIdOrNull() ?: finishCall(HttpStatus.BadRequest)
    val service = get<Services>().getService(serviceId) ?: finishCall(HttpStatus.NotFound)
    if (service.status != ServiceStatus.NORMAL && !user.hasAdmin && user.id != service.owner) finishCall(HttpStatus.NotFound)
    else if (service.status != ServiceStatus.NORMAL) finishCall(HttpStatus.NotAcceptable.subStatus("无法授权非正常状态的服务"))
    val res = authorizations.grantAuthorization(user.id, service.id)
    finishCall(HttpStatus.OK, res)
}

private suspend fun Context.revoke(): Nothing
{
    val user = getLoginUser() ?: finishCall(HttpStatus.NotLoggedIn)
    val authorizations = get<Authorizations>()
    val authorizationId = call.parameters["id"]?.toAuthorizationIdOrNull() ?: finishCall(HttpStatus.BadRequest)
    val authorization = authorizations.getAuthorization(authorizationId) ?: finishCall(HttpStatus.NotFound)
    if (authorization.user != user.id || authorization.cancel) finishCall(HttpStatus.NotFound)
    authorizations.revokeAuthorization(authorizationId)
    finishCall(HttpStatus.OK)
}

private fun Context.createCode(): Nothing
{
    val user = getLoginUser() ?: finishCall(HttpStatus.NotLoggedIn)
    finishCall(HttpStatus.OK, JWTAuth.makeOAuthCodeToken(user.id).token)
}

private suspend fun Context.getAuthorization(): Nothing
{
    val user = getLoginUser() ?: finishCall(HttpStatus.NotLoggedIn)
    val authorizationId = call.parameters["id"]?.toAuthorizationIdOrNull() ?: finishCall(HttpStatus.BadRequest)
    val authorization = get<Authorizations>().getAuthorization(authorizationId) ?: finishCall(HttpStatus.NotFound)
    if (authorization.user != user.id || authorization.cancel) finishCall(HttpStatus.NotFound)
    finishCall(HttpStatus.OK, authorization)
}

private suspend fun Context.getAuthorizationByService(): Nothing
{
    val user = getLoginUser() ?: finishCall(HttpStatus.NotLoggedIn)
    val serviceId = call.parameters["id"]?.toServiceIdOrNull() ?: finishCall(HttpStatus.BadRequest)
    val authorization = get<Authorizations>().getAuthorization(user.id, serviceId) ?: finishCall(HttpStatus.NotFound)
    if (authorization.cancel) finishCall(HttpStatus.NotFound)
    finishCall(HttpStatus.OK, authorization)
}