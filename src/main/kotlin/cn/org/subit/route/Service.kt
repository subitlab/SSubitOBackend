@file:Suppress("PackageDirectoryMismatch")

package cn.org.subit.route.service

import cn.org.subit.JWTAuth
import cn.org.subit.JWTAuth.getLoginUser
import cn.org.subit.dataClasses.*
import cn.org.subit.dataClasses.ServiceId.Companion.toServiceIdOrNull
import cn.org.subit.dataClasses.UserId.Companion.toUserIdOrNull
import cn.org.subit.database.Services
import cn.org.subit.route.utils.*
import cn.org.subit.utils.FileUtils
import cn.org.subit.utils.HttpStatus
import cn.org.subit.utils.statuses
import cn.org.subit.utils.toEnumOrNull
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.put
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.io.File
import javax.imageio.ImageIO

fun Route.service() = route("/service", {
    tags("服务")
})
{
    post("/create", {
        description = "创建服务"
        summary = "创建服务"
        request {
            body<CreateData>()
            {
                description = "创建服务"
                required = true
            }
        }
        response {
            statuses<ServiceId>(HttpStatus.OK, example = ServiceId(1))
        }
    }) { createService() }

    route("/avatar/{id}",{
        request {
            pathParameter<ServiceId>("id")
            {
                required = true
                description = "服务ID"
            }
        }
    })
    {
        put("", {
            description = "设置服务头像"
            summary = "设置服务头像"
            request {
                body<File>()
                {
                    mediaTypes(ContentType.Image.Any)
                    description = "设置服务头像"
                    required = true
                }
            }
            response {
                statuses(HttpStatus.OK)
            }
        }) { setAvatar() }

        get("", {
            description = "获取服务头像"
            summary = "获取服务头像"
            response {
                statuses(ContentType.Image.PNG, HttpStatus.OK)
            }
        }) { getAvatar() }
    }

    get("/secret/{id}", {
        description = "获取服务密钥"
        summary = "获取服务密钥"
        request {
            pathParameter<ServiceId>("id")
            {
                required = true
                description = "服务ID"
            }
        }
        response {
            statuses<JWTAuth.Token>(HttpStatus.OK, example = JWTAuth.Token("token"))
        }
    }) { getSecret() }

    route("/{id}", {
        request {
            pathParameter<ServiceId>("id")
            {
                required = true
                description = "服务ID"
            }
        }
    })
    {
        get("", {
            description = """
                获取服务信息, 若为服务所有者或管理员则返回完整信息, 否则返回基础信息. 若服务不存在或无权看到则返回404.
                
                当前用户不是管理员且服务状态不为NORMAL则视为无权查看.
            """.trimIndent()
            summary = "获取服务信息"
            response {
                statuses<ServiceInfo>(HttpStatus.OK.subStatus("获取服务信息成功"), example = ServiceInfo.example)
                statuses<BasicServiceInfo>(HttpStatus.OK.subStatus("获取服务基础信息成功"), example = BasicServiceInfo.example)
            }
        }) { getServiceInfo() }

        put("", {
            description = """
                修改服务信息, 仅服务所有者或管理员可用. 服务所有者修改服务信息时, 并不会直接修改信息, 而是将修改内容作为待审核内容, 等待管理员审核.
                服务所有者修改服务状态无效, 修改其他信息信息作为待审核内容. 若修改信息与当前信息相同则忽略, 若已有其他待审核信息则覆盖
                
                管理员修改服务信息时, 会直接修改服务信息, 并清空待审核内容.
                
                若既是服务所有者又是管理员, 则效果与管理员相同.
            """.trimIndent()
            summary = "修改服务信息"
            request {
                body<UpdateData>()
                {
                    description = "修改服务信息"
                    required = true
                    example(
                        "example",
                        UpdateData(
                            name = "服务名称",
                            description = "服务描述",
                            status = ServiceStatus.NORMAL,
                            unauthorized = ServicePermission.NONE,
                            authorized = ServicePermission.BASIC,
                            cancelAuthorization = ServicePermission.ALL,
                        )
                    )
                }
            }
            response {
                statuses(HttpStatus.OK, HttpStatus.Forbidden, HttpStatus.NotFound)
            }
        }) { updateService() }
    }

    get("/list", {
        description = "获取服务列表, 仅能看到自己有权看到的服务"
        summary = "获取服务列表"
        request {
            paged()
            queryParameter<UserId>("owner")
            {
                required = false
                description = "服务所有者, 若不填则不限制"
            }
            queryParameter<ServiceStatus>("status")
            {
                required = false
                description = "服务状态, 若不填则不限制"
            }
        }
        response {
            statuses<Slice<BasicServiceInfo>>(HttpStatus.OK, example = sliceOf(BasicServiceInfo.example))
        }
    }) { getServiceList() }

    get("/needPending", {
        description = "获取需要处理的服务列表, 仅管理员可用"
        summary = "获取需要处理的服务列表"
        request {
            paged()
        }
        response {
            statuses<Slice<ServiceInfo>>(HttpStatus.OK, example = sliceOf(ServiceInfo.example))
        }
    }) { getNeedPendingServices() }
}

@Serializable
private data class CreateData(
    val name: String,
    val description: String,
)

private suspend fun Context.createService(): Nothing
{
    val data = call.receive<CreateData>()
    val user = getLoginUser() ?: finishCall(HttpStatus.NotLoggedIn)
    val services = get<Services>()
    val id = services.createService(data.name, data.description, user.id)
    finishCall(HttpStatus.OK, id)
}

private suspend fun Context.setAvatar(): Nothing
{
    val user = getLoginUser() ?: finishCall(HttpStatus.NotLoggedIn)
    val id = call.parameters["id"]?.toServiceIdOrNull() ?: finishCall(HttpStatus.BadRequest)
    val service = get<Services>().getService(id) ?: finishCall(HttpStatus.NotFound.subStatus("服务不存在"))
    if (service.owner != user.id && !user.hasAdmin) finishCall(HttpStatus.Forbidden.subStatus("仅管理员或服务所有者可以设置头像"))
    FileUtils.setAvatar(service.id, call.receive())
    finishCall(HttpStatus.OK)
}

private suspend fun Context.getAvatar(): Nothing
{
    val id = call.parameters["id"]?.toServiceIdOrNull() ?: finishCall(HttpStatus.BadRequest)
    val services = get<Services>()
    val service = services.getService(id) ?: finishCall(HttpStatus.NotFound.subStatus("服务不存在"))
    val avatar = FileUtils.getAvatar(service.id)
    finishCallWithBytes(HttpStatus.OK, ContentType.Image.PNG) { ImageIO.write(avatar, "png", this) }
}

private suspend fun Context.getSecret(): Nothing
{
    val user = getLoginUser() ?: finishCall(HttpStatus.NotLoggedIn)
    val id = call.parameters["id"]?.toServiceIdOrNull() ?: finishCall(HttpStatus.BadRequest)
    val service = get<Services>().getService(id) ?: finishCall(HttpStatus.NotFound.subStatus("服务不存在"))
    if (service.owner != user.id) finishCall(HttpStatus.Forbidden.subStatus("仅服务所有者可以获取密钥"))
    finishCall(HttpStatus.OK, JWTAuth.makeServiceToken(service.id))
}

private suspend fun Context.getServiceInfo(): Nothing
{
    val user = getLoginUser()
    val id = call.parameters["id"]?.toServiceIdOrNull() ?: finishCall(HttpStatus.BadRequest)
    val service = get<Services>().getService(id) ?: finishCall(HttpStatus.NotFound.subStatus("服务不存在"))
    if (user?.id != service.owner && !user.hasAdmin)
    {
        if (service.status != ServiceStatus.NORMAL) finishCall(HttpStatus.NotFound.subStatus("服务不存在"))
        finishCall(HttpStatus.OK, service.toBasicServiceInfo())
    }
    finishCall(HttpStatus.OK, service)
}

private suspend fun Context.getServiceList(): Nothing
{
    val user = getLoginUser() ?: finishCall(HttpStatus.NotLoggedIn)
    val owner = call.parameters["owner"]?.toUserIdOrNull()
    val status = call.parameters["status"]?.toEnumOrNull<ServiceStatus>()
    val (begin, count) = call.getPage()
    val services = get<Services>()
    val list = services.getServices(user, owner, status, begin, count).map { it.toBasicServiceInfo() }
    finishCall(HttpStatus.OK, list)
}

private suspend fun Context.getNeedPendingServices(): Nothing
{
    val user = getLoginUser() ?: finishCall(HttpStatus.NotLoggedIn)
    if (!user.hasAdmin) finishCall(HttpStatus.Forbidden.subStatus("仅管理员可查看"))
    val (begin, count) = call.getPage()
    val services = get<Services>()
    val list = services.getNeedPendingServices(begin, count)
    finishCall(HttpStatus.OK, list)
}

@Serializable
private data class UpdateData(
    val name: String,
    val description: String,
    val status: ServiceStatus,
    val unauthorized: ServicePermission,
    val authorized: ServicePermission,
    val cancelAuthorization: ServicePermission,
)

private suspend fun Context.updateService(): Nothing
{
    val user = getLoginUser() ?: finishCall(HttpStatus.NotLoggedIn)
    val id = call.parameters["id"]?.toServiceIdOrNull() ?: finishCall(HttpStatus.BadRequest)
    val data = call.receive<UpdateData>()
    val services = get<Services>()
    val service = services.getService(id) ?: finishCall(HttpStatus.NotFound.subStatus("服务不存在"))
    if (service.owner != user.id && !user.hasAdmin && service.status != ServiceStatus.NORMAL)
        finishCall(HttpStatus.NotFound.subStatus("服务不存在"))
    if (service.owner != user.id && !user.hasAdmin)
        finishCall(HttpStatus.Forbidden.subStatus("仅管理员或服务所有者可以修改"))

    if (user.hasAdmin)
    {
        services.updateService(
            service.copy(
                name = data.name,
                description = data.description,
                status = data.status,
                unauthorized = data.unauthorized,
                authorized = data.authorized,
                cancelAuthorization = data.cancelAuthorization,
                pendingName = null,
                pendingDescription = null,
                pendingUnauthorized = null,
                pendingAuthorized = null,
                pendingCancelAuthorization = null,
            )
        )
    }
    else if (
        data.name == service.name &&
        data.description == service.description &&
        data.unauthorized == service.unauthorized &&
        data.authorized == service.authorized &&
        data.cancelAuthorization == service.cancelAuthorization
    )
    {
        services.updateService(
            service.copy(
                pendingName = null,
                pendingDescription = null,
                pendingUnauthorized = null,
                pendingAuthorized = null,
                pendingCancelAuthorization = null,
            )
        )
    }
    else
    {
        services.updateService(
            service.copy(
                pendingName = data.name,
                pendingDescription = data.description,
                pendingUnauthorized = data.unauthorized,
                pendingAuthorized = data.authorized,
                pendingCancelAuthorization = data.cancelAuthorization,
            )
        )
    }
    finishCall(HttpStatus.OK)
}