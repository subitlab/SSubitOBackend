@file:Suppress("PackageDirectoryMismatch")

package cn.org.subit.route.seiue

import cn.org.subit.JWTAuth.getLoginUser
import cn.org.subit.config.systemConfig
import cn.org.subit.dataClasses.UserId
import cn.org.subit.database.StudentIds
import cn.org.subit.plugin.contentNegotiation.contentNegotiationJson
import cn.org.subit.route.utils.Context
import cn.org.subit.route.utils.finishCall
import cn.org.subit.route.utils.finishCallWithRedirect
import cn.org.subit.route.utils.get
import cn.org.subit.utils.HttpStatus
import cn.org.subit.utils.Locks
import cn.org.subit.utils.statuses
import io.github.smiley4.ktorswaggerui.dsl.routing.delete
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

private val httpClient = HttpClient(Java)
{
    engine()
    {
        pipelining = true
        protocolVersion = java.net.http.HttpClient.Version.HTTP_2
    }
    install(ContentNegotiation)
    {
        json(contentNegotiationJson)
    }
}

fun Route.seiue() = route("/seiue", {
    tags = listOf("Seiue")
})
{
    get("/bind", {
        description = "绑定学号"
    })
    {
        finishCallWithRedirect(
            "https://passport.seiue.com/authorize?response_type=token" +
            "&client_id=${systemConfig.clientId}" +
            "&school_id=${systemConfig.schoolId}" +
            "&redirect_uri=${systemConfig.redirectUri}"
        )
    }

    post("/bind", {
        description = "完成绑定"
        request {
            queryParameter<String>("access_token")
            queryParameter<Long>("active_reflection_id")
        }
        response {
            statuses(HttpStatus.OK, HttpStatus.EmailExist.copy(message = "学号已存在"), HttpStatus.BadRequest)
        }
    }) { postBind() }

    delete("/bind", {
        description = "解绑学号"
        request {
            queryParameter<String>("studentId")
            {
                required = true
                description = "学号"
            }
        }
        response {
            statuses(HttpStatus.OK, HttpStatus.BadRequest)
        }
    }) { deleteBind() }
}

@Suppress("PropertyName")
@Serializable
data class ArchivedType(
    val id: Int,
    val school_id: Int,
    val parent_id: Int,
    val weight: Int,
    val name: String,
    val label: String? = null,
    val description: String? = null,
    val type: String,
    val created_at: String,
    val archived_at: String? = null,
)

@Suppress("PropertyName")
@Serializable
data class Seiue(
    val id: Int,
    val school_id: Int,
    val name: String,
    val role: String,
    val department_names: List<String>? = null,
    val pinyin: String,
    val gender: String? = null,
    val user_id: Int? = null,
    val usin: String? = null,
    val ename: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val idcard: String? = null,
    val photo: String? = null,
    val status: String? = null,
    val archived_type_id: Int? = null,
    val archived_type: ArchivedType? = null,
    val outer_id: String? = null,
    val deleted_at: String? = null,
)

private val addBindLocks = Locks<String>()

private suspend fun Context.postBind()
{
    val loginUser = getLoginUser() ?: finishCall(HttpStatus.NotLoggedIn)
    val token = call.request.queryParameters["access_token"] ?: finishCall(HttpStatus.BadRequest.subStatus("access_token 为空"))
    val activeReflectionId =
        call.request.queryParameters["active_reflection_id"]?.toLongOrNull() ?: finishCall(HttpStatus.BadRequest)

    val response = httpClient.get("https://open.seiue.com/api/v3/oauth/me")
    {
        bearerAuth(token)
        header("X-Reflection-Id", activeReflectionId)
    }

    val seiue = runCatching { response.body<Seiue>() }.getOrNull() ?: finishCall(HttpStatus.BadRequest.copy(message = "seiue token 无效"))
    if (seiue.usin == null) finishCall(HttpStatus.BadRequest.copy(message = "seiue token 无效(学号为空)"))
    val studentIds = get<StudentIds>()

    if (seiue.school_id != systemConfig.schoolId)
        finishCall(HttpStatus.BadRequest.copy(message = "学校不匹配"))

    addBindLocks.withLock<Nothing>(seiue.usin)
    {
        if (studentIds.getStudentIdUsers(seiue.usin) != null)
            finishCall(HttpStatus.EmailExist.copy(message = "学号已存在"))
        else
        {
            studentIds.addStudentId(loginUser.id, seiue.usin, seiue.name, !seiue.status.equals("normal", true), seiue)
            finishCall(HttpStatus.OK, "学号添加成功")
        }
    }
}

private val deleteBindLocks = Locks<UserId>()

private suspend fun Context.deleteBind()
{
    val loginUser = getLoginUser() ?: finishCall(HttpStatus.NotLoggedIn)
    val studentId = call.request.queryParameters["studentId"] ?: finishCall(HttpStatus.BadRequest)
    val studentIds = get<StudentIds>()

    deleteBindLocks.withLock<Nothing>(loginUser.id)
    {
        if (studentIds.getStudentIdCount(loginUser.id) >= 2)
        {
            if (studentIds.removeStudentId(loginUser.id, studentId)) finishCall(HttpStatus.OK)
            else finishCall(HttpStatus.NotFound)
        }
        else finishCall(HttpStatus.BadRequest.copy(message = "无法解绑唯一的希悦账号"))
    }
}